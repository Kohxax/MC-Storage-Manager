import type { AppDatabase } from '../db/client';
import { ContainerRepository, ContainerScopeError, type ContainerInput } from '../db/repositories/containers';
import { PlayerRepository, type SyncPlayerInput } from '../db/repositories/players';
import { RegionRepository } from '../db/repositories/regions';
import { SyncBatchRepository } from '../db/repositories/sync-batches';
import type { Player } from '../db/schema';
import { API_ERROR_CODES } from '../../shared/types/api';
import { ApiRequestError } from '../utils/api';
import { RegionService } from './regions';

export class SyncService {
  private readonly players: PlayerRepository;
  private readonly regions: RegionRepository;
  private readonly batches: SyncBatchRepository;
  private readonly containers: ContainerRepository;
  private readonly regionService: RegionService;

  constructor(private readonly database: AppDatabase) {
    this.players = new PlayerRepository(database);
    this.regions = new RegionRepository(database);
    this.batches = new SyncBatchRepository(database);
    this.containers = new ContainerRepository(database);
    this.regionService = new RegionService(database);
  }

  syncPlayer(input: SyncPlayerInput, serverId?: string): Player {
    if (!/^[-0-9a-f]{36}$/i.test(input.minecraftUuid)) {
      throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'minecraftUuid must be a UUID.', 400);
    }
    const displayName = input.displayName.trim();
    if (displayName.length === 0 || displayName.length > 64) {
      throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'displayName must be between 1 and 64 characters.', 400);
    }
    const permissions = [...new Set(input.permissions.map((permission) => permission.trim()).filter(Boolean))].slice(0, 64);
    return this.players.sync({ ...input, displayName, permissions }, serverId);
  }

  saveContainerBatch(
    serverId: string,
    regionId: string,
    idempotencyKey: string,
    inputs: ContainerInput[],
  ): { batchId: string; idempotent: boolean; containers: ReturnType<ContainerRepository['listItemsByRegion']> } {
    if (!/^[A-Za-z0-9._:-]{8,128}$/.test(idempotencyKey)) {
      throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'A valid idempotency key is required.', 400);
    }
    const normalizedInputs = inputs.map((input) => ({
      ...input,
      deleted: input.deleted === true,
      items: input.items.map((item) => ({ ...item, variantKey: item.variantKey ?? '' })),
    }));
    if (normalizedInputs.length > 500) {
      throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'A batch may contain at most 500 containers.', 400);
    }
    for (const input of normalizedInputs) {
      if (!Number.isSafeInteger(input.x) || !Number.isSafeInteger(input.y) || !Number.isSafeInteger(input.z)) {
        throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Container coordinates must be safe integers.', 400);
      }
      if (input.items.length > 256 || input.items.some((item) => !Number.isSafeInteger(item.amount) || item.amount < 0)) {
        throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Container item amounts must be non-negative integers.', 400);
      }
      if (input.deleted && input.items.length > 0) {
        throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'A deleted container must not contain items.', 400);
      }
    }

    const requestPayload = { regionId, containers: normalizedInputs };
    const requestFingerprint = stableStringify(requestPayload);

    return this.database.transaction((tx) => {
      const existing = this.batches.findByKeyIn(tx, serverId, idempotencyKey);
      if (existing) {
        if (stableStringify(existing.requestPayload) !== requestFingerprint) {
          throw new ApiRequestError(
            API_ERROR_CODES.CONFLICT,
            'The idempotency key has already been used for a different request.',
            409,
          );
        }
        if (existing.status === 'completed' && existing.resultPayload && typeof existing.resultPayload === 'object') {
          const result = existing.resultPayload as { containers?: ReturnType<ContainerRepository['listItemsByRegion']> };
          return { batchId: existing.id, idempotent: true, containers: result.containers ?? [] };
        }
        if (existing.status === 'processing') {
          throw new ApiRequestError(API_ERROR_CODES.CONFLICT, 'A batch with this idempotency key is already processing.', 409);
        }
        this.batches.markProcessingIn(tx, existing.id);
      }

      const batch = existing ?? this.batches.createIn(tx, serverId, idempotencyKey, requestPayload);
      try {
        const saved = this.regionService.savePluginContainers(serverId, regionId, normalizedInputs, tx);
        const resultPayload = { containers: saved.containers };
        this.batches.completeIn(tx, batch.id, resultPayload);
        return { batchId: batch.id, idempotent: false, containers: saved.containers };
      } catch (error) {
        if (error instanceof ContainerScopeError) {
          throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Container was not found.', 404);
        }
        throw error;
      }
    });
  }
}

function stableStringify(value: unknown): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map((entry) => stableStringify(entry)).join(',')}]`;
  const object = value as Record<string, unknown>;
  return `{${Object.keys(object)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${stableStringify(object[key])}`)
    .join(',')}}`;
}
