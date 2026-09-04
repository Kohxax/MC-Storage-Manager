import { and, eq } from 'drizzle-orm';
import type { AppDatabase } from '../client';
import { syncBatches, type NewSyncBatch, type SyncBatch } from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';
import type { DatabaseExecutor } from './containers';

export class SyncBatchRepository {
  constructor(private readonly database: AppDatabase) {}

  findByKey(serverId: string, idempotencyKey: string): SyncBatch | undefined {
    return this.findByKeyIn(this.database, serverId, idempotencyKey);
  }

  findByKeyIn(database: DatabaseExecutor, serverId: string, idempotencyKey: string): SyncBatch | undefined {
    return database
      .select()
      .from(syncBatches)
      .where(and(eq(syncBatches.serverId, serverId), eq(syncBatches.idempotencyKey, idempotencyKey)))
      .get();
  }

  create(serverId: string, idempotencyKey: string, requestPayload: unknown): SyncBatch {
    return this.createIn(this.database, serverId, idempotencyKey, requestPayload);
  }

  createIn(database: DatabaseExecutor, serverId: string, idempotencyKey: string, requestPayload: unknown): SyncBatch {
    const values: NewSyncBatch = {
      id: createEntityId(),
      serverId,
      idempotencyKey,
      status: 'processing',
      requestPayload,
      resultPayload: null,
      receivedAt: nowIsoDateTime(),
      completedAt: null,
      revision: 0,
    };
    const [created] = database.insert(syncBatches).values(values).returning().all();
    if (!created) throw new Error('Failed to create sync batch.');
    return created;
  }

  complete(id: string, resultPayload: unknown, status: 'completed' | 'failed' = 'completed'): SyncBatch {
    return this.completeIn(this.database, id, resultPayload, status);
  }

  completeIn(
    database: DatabaseExecutor,
    id: string,
    resultPayload: unknown,
    status: 'completed' | 'failed' = 'completed',
  ): SyncBatch {
    const [updated] = database
      .update(syncBatches)
      .set({ status, resultPayload, completedAt: nowIsoDateTime(), revision: 1 })
      .where(eq(syncBatches.id, id))
      .returning()
      .all();
    if (!updated) throw new Error('Sync batch was not found.');
    return updated;
  }

  markProcessingIn(database: DatabaseExecutor, id: string): void {
    database
      .update(syncBatches)
      .set({ status: 'processing', completedAt: null, resultPayload: null })
      .where(eq(syncBatches.id, id))
      .run();
  }
}
