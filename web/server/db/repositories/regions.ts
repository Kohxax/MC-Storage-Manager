import { and, desc, eq } from 'drizzle-orm';
import type { RegionId } from '../../../shared/types/id';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';
import { nextRevision, parseRevision, type Revision } from '../../../shared/types/revision';
import type { AppDatabase } from '../client';
import { regions, type NewRegion, type Region } from '../schema';
import { RevisionConflictError } from '../../../shared/types/revision';

export interface RegionBounds {
  minX: number;
  minY: number;
  minZ: number;
  maxX: number;
  maxY: number;
  maxZ: number;
}

export interface CreateRegionInput extends RegionBounds {
  id?: string;
  serverId?: string | null;
  ownerPlayerId: string;
  name: string;
  worldUuid: string;
  worldName: string;
  dimensionKey: string;
  status?: Region['status'];
}

export interface UpdateRegionInput {
  name?: string;
  worldName?: string;
  dimensionKey?: string;
  status?: Region['status'];
  lastScanAt?: string | null;
}

export class RegionRepository {
  constructor(private readonly database: AppDatabase) {}

  create(input: CreateRegionInput): Region {
    const now = nowIsoDateTime();
    const values: NewRegion = {
      id: input.id ?? createEntityId(),
      ownerPlayerId: input.ownerPlayerId,
      name: input.name,
      worldUuid: input.worldUuid,
      worldName: input.worldName,
      dimensionKey: input.dimensionKey,
      minX: input.minX,
      minY: input.minY,
      minZ: input.minZ,
      maxX: input.maxX,
      maxY: input.maxY,
      maxZ: input.maxZ,
      status: input.status ?? 'active',
      createdAt: now,
      updatedAt: now,
      revision: 0,
    };
    if (input.serverId !== undefined) {
      values.serverId = input.serverId;
    }
    const [created] = this.database.insert(regions).values(values).returning().all();
    if (!created) {
      throw new Error('Failed to create region.');
    }
    return created;
  }

  findById(id: string): Region | undefined {
    return this.database.select().from(regions).where(eq(regions.id, id)).get();
  }

  listByOwner(ownerPlayerId: string): Region[] {
    return this.database
      .select()
      .from(regions)
      .where(eq(regions.ownerPlayerId, ownerPlayerId))
      .orderBy(desc(regions.updatedAt))
      .all();
  }

  listAll(): Region[] {
    return this.database.select().from(regions).orderBy(desc(regions.updatedAt)).all();
  }

  update(id: RegionId | string, expectedRevision: Revision | number, input: UpdateRegionInput): Region {
    const revision = parseRevision(Number(expectedRevision));
    const [updated] = this.database
      .update(regions)
      .set({
        ...input,
        updatedAt: nowIsoDateTime(),
        revision: nextRevision(revision),
      })
      .where(and(eq(regions.id, id), eq(regions.revision, revision)))
      .returning()
      .all();

    if (!updated) {
      throw new RevisionConflictError();
    }
    return updated;
  }

  delete(id: RegionId | string, expectedRevision: Revision | number): void {
    const revision = parseRevision(Number(expectedRevision));
    const result = this.database
      .delete(regions)
      .where(and(eq(regions.id, id), eq(regions.revision, revision)))
      .run();
    if (result.changes !== 1) {
      throw new RevisionConflictError();
    }
  }
}
