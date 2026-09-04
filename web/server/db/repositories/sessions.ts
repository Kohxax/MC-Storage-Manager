import { and, eq, isNull, sql } from 'drizzle-orm';
import type { AppDatabase } from '../client';
import { webSessions, type NewWebSession, type WebSession } from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';

export interface CreateSessionInput {
  serverId?: string | null;
  playerId: string;
  tokenHash: string;
  expiresAt: string;
}

export class SessionRepository {
  constructor(private readonly database: AppDatabase) {}

  create(input: CreateSessionInput): WebSession {
    const now = nowIsoDateTime();
    const values: NewWebSession = {
      id: createEntityId(),
      serverId: input.serverId ?? null,
      playerId: input.playerId,
      tokenHash: input.tokenHash,
      expiresAt: input.expiresAt,
      revokedAt: null,
      createdAt: now,
      updatedAt: now,
      revision: 0,
    };
    const [created] = this.database.insert(webSessions).values(values).returning().all();
    if (!created) throw new Error('Failed to create session.');
    return created;
  }

  findActiveByTokenHash(tokenHash: string, now: string): WebSession | undefined {
    return this.database
      .select()
      .from(webSessions)
      .where(and(eq(webSessions.tokenHash, tokenHash), isNull(webSessions.revokedAt)))
      .all()
      .find((session) => session.expiresAt > now);
  }

  revokeById(id: string, now = nowIsoDateTime()): boolean {
    const result = this.database
      .update(webSessions)
      .set({ revokedAt: now, updatedAt: now, revision: sql`${webSessions.revision} + 1` })
      .where(and(eq(webSessions.id, id), isNull(webSessions.revokedAt)))
      .run();
    return result.changes === 1;
  }

  revokeByPlayerAndServer(playerId: string, serverId: string, now = nowIsoDateTime()): number {
    const result = this.database
      .update(webSessions)
      .set({ revokedAt: now, updatedAt: now, revision: sql`${webSessions.revision} + 1` })
      .where(and(eq(webSessions.playerId, playerId), eq(webSessions.serverId, serverId), isNull(webSessions.revokedAt)))
      .run();
    return result.changes;
  }
}
