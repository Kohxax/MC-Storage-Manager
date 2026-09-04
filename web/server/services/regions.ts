import type { AppDatabase } from '../db/client';
import { ContainerRepository, type ContainerInput, type DatabaseExecutor } from '../db/repositories/containers';
import { ChangeRepository } from '../db/repositories/changes';
import { PlayerRepository } from '../db/repositories/players';
import { RegionRepository, type CreateRegionInput, type UpdateRegionInput } from '../db/repositories/regions';
import type { Player, Region } from '../db/schema';
import { API_ERROR_CODES } from '../../shared/types/api';
import { ApiRequestError } from '../utils/api';
import { assertPermission, assertPluginRegion, assertRegionManage, assertRegionView, PERMISSIONS } from './permissions';

export class RegionService {
  private readonly regions: RegionRepository;
  private readonly players: PlayerRepository;
  private readonly containers: ContainerRepository;
  private readonly changes: ChangeRepository;

  constructor(private readonly database: AppDatabase) {
    this.regions = new RegionRepository(database);
    this.players = new PlayerRepository(database);
    this.containers = new ContainerRepository(database);
    this.changes = new ChangeRepository(database);
  }

  createPluginRegion(serverId: string, input: CreateRegionInput): Region {
    const owner = this.players.findById(input.ownerPlayerId);
    if (!owner || !this.players.findServerPermissions(serverId, owner.id)) {
      throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'ownerPlayerId does not identify a synced player.', 400);
    }
    validateBounds(input);
    return this.regions.create({ ...input, serverId });
  }

  syncPluginRegion(
    serverId: string,
    input: Omit<CreateRegionInput, 'serverId' | 'ownerPlayerId'> & {
      id: string;
      ownerMinecraftUuid: string;
      ownerCurrentName?: string;
      ownerPermissions?: string[];
    },
  ): Region {
    let owner = this.players.findByMinecraftUuid(input.ownerMinecraftUuid);
    const serverPermissions = owner ? this.players.findServerPermissions(serverId, owner.id) : undefined;
    if (!owner || !serverPermissions || input.ownerPermissions !== undefined) {
      owner = this.players.sync({
        minecraftUuid: input.ownerMinecraftUuid,
        displayName: input.ownerCurrentName ?? owner?.displayName ?? input.ownerMinecraftUuid,
        permissions: input.ownerPermissions ?? serverPermissions?.permissions ?? [],
        }, serverId);
    }

    const existingRegion = this.regions.findById(input.id);
    if (existingRegion) {
      if (existingRegion.serverId !== serverId) {
        throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Region was not found.', 404);
      }
      // A Web-requested deletion stays authoritative until the plugin acknowledges
      // its change. Operational world metadata and validity may otherwise recover.
      if (existingRegion.status === 'deleted') return existingRegion;
      const nextStatus = input.status ?? 'active';
      if (existingRegion.worldName !== input.worldName || existingRegion.dimensionKey !== input.dimensionKey
          || existingRegion.status !== nextStatus) {
        return this.regions.update(existingRegion.id, existingRegion.revision, {
          worldName: input.worldName,
          dimensionKey: input.dimensionKey,
          status: nextStatus,
        });
      }
      return existingRegion;
    }

    const { ownerMinecraftUuid: _ownerMinecraftUuid, ownerCurrentName: _ownerCurrentName,
      ownerPermissions: _ownerPermissions, ...region } = input;
    return this.createPluginRegion(serverId, { ...region, ownerPlayerId: owner.id });
  }

  updatePluginRegion(serverId: string, id: string, expectedRevision: number, input: UpdateRegionInput): Region {
    const region = assertPluginRegion(this.regions.findById(id), serverId);
    return this.regions.update(region.id, expectedRevision, input);
  }

  deletePluginRegion(serverId: string, id: string, expectedRevision: number): void {
    const region = assertPluginRegion(this.regions.findById(id), serverId);
    this.regions.delete(region.id, expectedRevision);
  }

  listWebRegions(player: Player, currentServerId?: string | null): Region[] {
    return this.regions
      .listAll()
      .filter((region) => region.status !== 'deleted' && this.canView(region, player, currentServerId));
  }

  getWebRegion(player: Player, id: string, currentServerId?: string | null): Region {
    return assertRegionView(this.database, player, this.regions.findById(id), currentServerId);
  }

  getWebItems(player: Player, id: string, currentServerId?: string | null) {
    const region = this.getWebRegion(player, id, currentServerId);
    return { region, containers: this.containers.listItemsByRegion(region.id) };
  }

  updateWebRegion(
    player: Player,
    id: string,
    expectedRevision: number,
    input: UpdateRegionInput,
    currentServerId?: string | null,
  ): Region {
    const region = assertRegionManage(this.database, player, this.regions.findById(id), currentServerId);
    validatePatch(input);
    const updated = this.regions.update(region.id, expectedRevision, input);
    if (updated.serverId) {
      this.changes.create({
        serverId: updated.serverId,
        regionId: updated.id,
        playerId: player.id,
        operation: 'region.update',
        payload: { id: updated.id, revision: updated.revision, patch: input },
      });
    }
    return updated;
  }

  deleteWebRegion(player: Player, id: string, expectedRevision: number, currentServerId?: string | null): Region {
    const region = assertRegionManage(this.database, player, this.regions.findById(id), currentServerId);
    const updated = this.regions.update(region.id, expectedRevision, { status: 'deleted' });
    if (updated.serverId) {
      this.changes.create({
        serverId: updated.serverId,
        regionId: updated.id,
        playerId: player.id,
        operation: 'region.delete',
        payload: { id: updated.id, revision: updated.revision },
      });
    }
    return updated;
  }

  savePluginContainers(
    serverId: string,
    regionId: string,
    inputs: ContainerInput[],
    transaction?: DatabaseExecutor,
  ) {
    const region = assertPluginRegion(this.regions.findById(regionId), serverId);
    for (const input of inputs) {
      if (input.worldUuid !== region.worldUuid) {
        throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Container worldUuid does not match the region world.', 400);
      }
      if (
        input.x < region.minX || input.x > region.maxX ||
        input.y < region.minY || input.y > region.maxY ||
        input.z < region.minZ || input.z > region.maxZ
      ) {
        throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Container coordinates must be inside the region.', 400);
      }
    }
    return {
      region,
      containers: transaction
        ? this.containers.saveBatchInTransaction(transaction, region.id, inputs)
        : this.containers.saveBatch(region.id, inputs),
    };
  }

  canView(region: Region, player: Player, currentServerId?: string | null): boolean {
    try {
      assertRegionView(this.database, player, region, currentServerId);
      return true;
    } catch {
      return false;
    }
  }

  assertCanCreate(player: Player): void {
    assertPermission(this.database, player, PERMISSIONS.REGION_CREATE);
  }
}

function validateBounds(input: Pick<CreateRegionInput, 'minX' | 'minY' | 'minZ' | 'maxX' | 'maxY' | 'maxZ'>): void {
  const values = [input.minX, input.minY, input.minZ, input.maxX, input.maxY, input.maxZ];
  if (values.some((value) => !Number.isSafeInteger(value))) {
    throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Region coordinates must be safe integers.', 400);
  }
  if (input.minX > input.maxX || input.minY > input.maxY || input.minZ > input.maxZ) {
    throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Region minimum coordinates must not exceed maximum coordinates.', 400);
  }
}

function validatePatch(input: UpdateRegionInput): void {
  if (input.name !== undefined && (input.name.trim().length === 0 || input.name.length > 128)) {
    throw new ApiRequestError(API_ERROR_CODES.BAD_REQUEST, 'Region name must be between 1 and 128 characters.', 400);
  }
}
