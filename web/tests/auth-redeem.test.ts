import { afterEach, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { eq } from 'drizzle-orm';
import { createDatabase, type DatabaseHandle } from '../server/db/client';
import { loginTokens, webSessions } from '../server/db/schema';
import { LoginTokenRepository } from '../server/db/repositories/login-tokens';
import { PlayerRepository } from '../server/db/repositories/players';
import { ServerRepository } from '../server/db/repositories/servers';
import { redeemLoginTokenInDatabase } from '../server/services/auth';
import { createOpaqueToken, hashApiKey, hashOpaqueToken } from '../server/services/security';
import { PERMISSIONS } from '../server/services/permissions';

let handle: DatabaseHandle | undefined;

afterEach(() => {
  handle?.close();
  handle = undefined;
});

function migratedDatabase() {
  handle = createDatabase(':memory:');
  const migrationDirectory = fileURLToPath(new URL('../server/db/migrations/', import.meta.url));
  const initial = readFileSync(`${migrationDirectory}0000_initial.sql`, 'utf8').replaceAll('--> statement-breakpoint', '');
  const phase34 = readFileSync(`${migrationDirectory}0001_phase34.sql`, 'utf8').replaceAll('--> statement-breakpoint', '');
  handle.sqlite.exec(initial);
  handle.sqlite.exec(phase34);
  return handle;
}

function seed(permissions: string[] = [PERMISSIONS.WEB_LOGIN]) {
  const current = migratedDatabase();
  const server = new ServerRepository(current.db).create({
    name: 'Redeem test server',
    apiKeyHash: hashApiKey('redeem-test-api-key-123456'),
    publicUrl: 'https://storage.example.test',
  });
  const player = new PlayerRepository(current.db).sync(
    {
      minecraftUuid: '00000000-0000-4000-8000-000000000031',
      displayName: 'Redeem player',
      permissions,
    },
    server.id,
  );
  const rawToken = createOpaqueToken(32);
  new LoginTokenRepository(current.db).create({
    serverId: server.id,
    playerId: player.id,
    tokenHash: hashOpaqueToken(rawToken),
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
  });
  return { current, server, player, rawToken };
}

function tokenRow(current: DatabaseHandle, rawToken: string) {
  return current.db
    .select({ consumedAt: loginTokens.consumedAt })
    .from(loginTokens)
    .where(eq(loginTokens.tokenHash, hashOpaqueToken(rawToken)))
    .get();
}

describe('web login token redemption transaction', () => {
  it('creates one session and consumes the token on the first successful redemption', () => {
    const { current, player, rawToken } = seed();

    const context = redeemLoginTokenInDatabase(current.db, rawToken);

    expect(context.player.id).toBe(player.id);
    expect(current.db.select().from(webSessions).all()).toHaveLength(1);
    expect(tokenRow(current, rawToken)?.consumedAt).not.toBeNull();
  });

  it('rejects a second redemption of the same token without creating another session', () => {
    const { current, rawToken } = seed();
    redeemLoginTokenInDatabase(current.db, rawToken);

    expect(() => redeemLoginTokenInDatabase(current.db, rawToken)).toThrow(/invalid or expired/i);
    expect(current.db.select().from(webSessions).all()).toHaveLength(1);
  });

  it('leaves the token unconsumed when the player lacks web-login permission', () => {
    const { current, rawToken } = seed([]);

    expect(() => redeemLoginTokenInDatabase(current.db, rawToken)).toThrow(/permission/i);
    expect(tokenRow(current, rawToken)?.consumedAt).toBeNull();
    expect(current.db.select().from(webSessions).all()).toHaveLength(0);
  });

  it('rolls back token consumption when session creation fails', () => {
    const { current, rawToken } = seed();
    current.sqlite.exec(`
      CREATE TRIGGER fail_session_insert
      BEFORE INSERT ON web_sessions
      BEGIN
        SELECT RAISE(ABORT, 'forced session creation failure');
      END;
    `);

    expect(() => redeemLoginTokenInDatabase(current.db, rawToken)).toThrow(/forced session creation failure/);
    expect(tokenRow(current, rawToken)?.consumedAt).toBeNull();
    expect(current.db.select().from(webSessions).all()).toHaveLength(0);
  });
});
