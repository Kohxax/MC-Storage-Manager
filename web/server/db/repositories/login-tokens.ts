import { and, eq, gt, isNull } from 'drizzle-orm';
import type { AppDatabase } from '../client';
import { loginTokens, type LoginToken, type NewLoginToken } from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';

export class LoginTokenRepository {
  constructor(private readonly database: AppDatabase) {}

  create(input: { serverId: string; playerId?: string | null; tokenHash: string; expiresAt: string }): LoginToken {
    const values: NewLoginToken = {
      id: createEntityId(),
      serverId: input.serverId,
      playerId: input.playerId ?? null,
      tokenHash: input.tokenHash,
      expiresAt: input.expiresAt,
      consumedAt: null,
      createdAt: nowIsoDateTime(),
    };
    const [created] = this.database.insert(loginTokens).values(values).returning().all();
    if (!created) throw new Error('Failed to create login token.');
    return created;
  }

  /** Atomically consumes a token only while it is unused and unexpired. */
  consume(tokenHash: string, now = nowIsoDateTime()): LoginToken | undefined {
    const [consumed] = this.database
      .update(loginTokens)
      .set({ consumedAt: now })
      .where(and(eq(loginTokens.tokenHash, tokenHash), isNull(loginTokens.consumedAt), gt(loginTokens.expiresAt, now)))
      .returning()
      .all();
    return consumed;
  }
}
