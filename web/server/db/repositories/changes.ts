import { and, asc, eq, gt, inArray } from 'drizzle-orm';
import type { AppDatabase } from '../client';
import { changes, type Change, type NewChange } from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';
import { nextRevision, parseRevision, type Revision } from '../../../shared/types/revision';
import { RevisionConflictError } from '../../../shared/types/revision';

export type ChangeOperation = 'region.update' | 'region.delete' | 'acl.update';
export type ChangeStatus = 'pending' | 'sent' | 'applied' | 'failed' | 'cancelled';

export class ChangeRepository {
  constructor(private readonly database: AppDatabase) {}

  create(input: {
    serverId: string;
    regionId?: string | null;
    playerId: string;
    operation: ChangeOperation;
    payload: Record<string, unknown>;
  }): Change {
    const now = nowIsoDateTime();
    const values: NewChange = {
      id: createEntityId(),
      serverId: input.serverId,
      regionId: input.regionId ?? null,
      playerId: input.playerId,
      operation: input.operation,
      status: 'pending',
      payload: input.payload,
      result: null,
      createdAt: now,
      updatedAt: now,
      revision: 0,
    };
    const [created] = this.database.insert(changes).values(values).returning().all();
    if (!created) throw new Error('Failed to create change.');
    return created;
  }

  listForServer(serverId: string, options: { cursor?: string; limit?: number } = {}): Change[] {
    const limit = Math.min(Math.max(options.limit ?? 50, 1), 100);
    const conditions = [
      eq(changes.serverId, serverId),
      inArray(changes.status, ['pending', 'sent']),
      ...(options.cursor ? [gt(changes.createdAt, options.cursor)] : []),
    ];
    return this.database.select().from(changes).where(and(...conditions)).orderBy(asc(changes.createdAt)).limit(limit).all();
  }

  findById(id: string): Change | undefined {
    return this.database.select().from(changes).where(eq(changes.id, id)).get();
  }

  applyResult(id: string, expectedRevision: Revision | number, success: boolean, result: Record<string, unknown>): Change {
    const revision = parseRevision(Number(expectedRevision));
    const now = nowIsoDateTime();
    const [updated] = this.database
      .update(changes)
      .set({
        status: success ? 'applied' : 'failed',
        result,
        updatedAt: now,
        revision: nextRevision(revision),
      })
      .where(and(eq(changes.id, id), eq(changes.revision, revision)))
      .returning()
      .all();
    if (!updated) throw new RevisionConflictError();
    return updated;
  }
}
