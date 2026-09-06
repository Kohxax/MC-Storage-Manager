import { afterEach, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createDatabase, type DatabaseHandle } from '../server/db/client';
import { ChangeRepository } from '../server/db/repositories/changes';
import { ContainerRepository } from '../server/db/repositories/containers';
import { LoginTokenRepository } from '../server/db/repositories/login-tokens';
import { PlayerRepository } from '../server/db/repositories/players';
import { RegionRepository } from '../server/db/repositories/regions';
import { ServerRepository } from '../server/db/repositories/servers';
import { SessionRepository } from '../server/db/repositories/sessions';
import { SyncService } from '../server/services/sync';
import { RegionService } from '../server/services/regions';
import { canManageRegion } from '../server/services/permissions';
import { requireCsrf } from '../server/services/csrf';
import type { H3Event } from 'h3';
import { createOpaqueToken, hashApiKey, hashOpaqueToken, verifyApiKey } from '../server/services/security';
import { createEntityId } from '../shared/types/id';

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

function seed() {
  const current = migratedDatabase();
  const server = new ServerRepository(current.db).create({
    name: 'Test server',
    apiKeyHash: hashApiKey('test-api-key-123456'),
    publicUrl: 'https://storage.example.test',
  });
  const player = new PlayerRepository(current.db).sync({
    minecraftUuid: '00000000-0000-4000-8000-000000000001',
    displayName: 'Player',
    permissions: ['storage.web.login', 'storage.region.create', 'storage.region.manage.own'],
  });
  return { current, server, player };
}

describe('phase 3–4 persistence contracts', () => {
  it('rejects a state-changing request without a matching CSRF header', () => {
    const event = { node: { req: { headers: { cookie: 'mcsm_csrf=expected', 'x-csrf-token': 'wrong' } } } } as unknown as H3Event;
    expect(() => requireCsrf(event)).toThrow(/CSRF/);
  });

  it('uses a salted API-key hash and verifies it without storing plaintext', () => {
    const hash = hashApiKey('test-api-key-123456');
    expect(hash).toMatch(/^scrypt\$/);
    expect(verifyApiKey('test-api-key-123456', hash)).toBe(true);
    expect(verifyApiKey('wrong-api-key-123456', hash)).toBe(false);
  });

  it('consumes login tokens exactly once and stores only token hashes', () => {
    const { current, server, player } = seed();
    const raw = createOpaqueToken(32);
    const repository = new LoginTokenRepository(current.db);
    repository.create({
      serverId: server.id,
      playerId: player.id,
      tokenHash: hashOpaqueToken(raw),
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });
    expect(repository.consume(hashOpaqueToken(raw))).toBeTruthy();
    expect(repository.consume(hashOpaqueToken(raw))).toBeUndefined();
  });

  it('rejects expired login tokens without consuming them', () => {
    const { current, server, player } = seed();
    const raw = createOpaqueToken(32);
    const repository = new LoginTokenRepository(current.db);
    repository.create({
      serverId: server.id,
      playerId: player.id,
      tokenHash: hashOpaqueToken(raw),
      expiresAt: '2020-01-01T00:00:00.000Z',
    });
    expect(repository.consume(hashOpaqueToken(raw), '2026-09-04T00:00:00.000Z')).toBeUndefined();
  });

  it('keeps web session tokens hashed and can revoke a session', () => {
    const { current, player } = seed();
    const raw = createOpaqueToken(32);
    const sessionRepository = new SessionRepository(current.db);
    const session = sessionRepository.create({
      playerId: player.id,
      tokenHash: hashOpaqueToken(raw),
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });
    expect(session.tokenHash).not.toContain(raw);
    expect(sessionRepository.findActiveByTokenHash(hashOpaqueToken(raw), new Date().toISOString())?.id).toBe(session.id);
    expect(sessionRepository.revokeById(session.id)).toBe(true);
    expect(sessionRepository.findActiveByTokenHash(hashOpaqueToken(raw), new Date().toISOString())).toBeUndefined();
  });

  it('saves a container batch once and returns the same result for a replay', () => {
    const { current, server, player } = seed();
    const region = new RegionRepository(current.db).create({
      serverId: server.id,
      ownerPlayerId: player.id,
      name: 'Main',
      worldUuid: '00000000-0000-4000-8000-000000000002',
      worldName: 'world',
      dimensionKey: 'minecraft:overworld',
      minX: 0,
      minY: 0,
      minZ: 0,
      maxX: 10,
      maxY: 10,
      maxZ: 10,
    });
    const service = new SyncService(current.db);
    const input = [{
      worldUuid: region.worldUuid,
      x: 1,
      y: 1,
      z: 2,
      containerType: 'chest',
      items: [{ itemKey: 'minecraft:stone', amount: 32 }],
    }];
    const first = service.saveContainerBatch(server.id, region.id, 'batch-0001', input);
    const scanned = new RegionRepository(current.db).findById(region.id);
    expect(scanned?.lastScanAt).toBeTruthy();
    expect(scanned?.updatedAt).toBe(scanned?.lastScanAt);
    // Operational scan activity must not make an in-progress Web edit stale.
    expect(scanned?.revision).toBe(region.revision);
    const replay = service.saveContainerBatch(server.id, region.id, 'batch-0001', input);
    expect(first.idempotent).toBe(false);
    expect(replay.idempotent).toBe(true);
    expect(replay.batchId).toBe(first.batchId);
    expect(replay.containers).toHaveLength(1);
    expect(new ChangeRepository(current.db).listForServer(server.id)).toHaveLength(0);
  });

  it('applies an explicit container deletion and keeps idempotency scoped to the request body', () => {
    const { current, server, player } = seed();
    const region = new RegionRepository(current.db).create({
      serverId: server.id,
      ownerPlayerId: player.id,
      name: 'Main',
      worldUuid: '00000000-0000-4000-8000-000000000002',
      worldName: 'world',
      dimensionKey: 'minecraft:overworld',
      minX: 0,
      minY: 0,
      minZ: 0,
      maxX: 10,
      maxY: 10,
      maxZ: 10,
    });
    const service = new SyncService(current.db);
    const position = { worldUuid: region.worldUuid, x: 1, y: 1, z: 2, containerType: 'chest' };
    service.saveContainerBatch(server.id, region.id, 'batch-create', [{ ...position, items: [{ itemKey: 'minecraft:stone', amount: 1 }] }]);
    const deleted = service.saveContainerBatch(server.id, region.id, 'batch-delete', [{ ...position, deleted: true, items: [] }]);
    expect(deleted.containers).toHaveLength(0);
    expect(new ContainerRepository(current.db).listItemsByRegion(region.id)).toHaveLength(0);
    expect(service.saveContainerBatch(server.id, region.id, 'batch-delete', [{ ...position, deleted: true, items: [] }]).idempotent).toBe(true);
    expect(() => service.saveContainerBatch(server.id, region.id, 'batch-create', [{ ...position, items: [{ itemKey: 'minecraft:dirt', amount: 1 }] }])).toThrow(/different request/);
  });

  it('keeps player permissions isolated per server', () => {
    const current = migratedDatabase();
    const servers = new ServerRepository(current.db);
    const serverA = servers.create({ name: 'Server A', apiKeyHash: hashApiKey('server-a-api-key-123'), publicUrl: 'https://a.example.test' });
    const serverB = servers.create({ name: 'Server B', apiKeyHash: hashApiKey('server-b-api-key-123'), publicUrl: 'https://b.example.test' });
    const service = new SyncService(current.db);
    const playerA = service.syncPlayer({
      minecraftUuid: '00000000-0000-4000-8000-000000000011',
      displayName: 'Player',
      permissions: ['storage.admin'],
    }, serverA.id);
    service.syncPlayer({
      minecraftUuid: '00000000-0000-4000-8000-000000000011',
      displayName: 'Player',
      permissions: [],
    }, serverB.id);
    const regionA = new RegionRepository(current.db).create({
      serverId: serverA.id,
      ownerPlayerId: playerA.id,
      name: 'A',
      worldUuid: '00000000-0000-4000-8000-000000000012',
      worldName: 'world-a',
      dimensionKey: 'minecraft:overworld',
      minX: 0, minY: 0, minZ: 0, maxX: 1, maxY: 1, maxZ: 1,
    });
    const regionB = new RegionRepository(current.db).create({
      serverId: serverB.id,
      ownerPlayerId: playerA.id,
      name: 'B',
      worldUuid: '00000000-0000-4000-8000-000000000013',
      worldName: 'world-b',
      dimensionKey: 'minecraft:overworld',
      minX: 0, minY: 0, minZ: 0, maxX: 1, maxY: 1, maxZ: 1,
    });
    expect(canManageRegion(current.db, playerA, regionA, serverA.id)).toBe(true);
    expect(canManageRegion(current.db, playerA, regionB, serverB.id)).toBe(false);
  });

  it('registers a plugin region with the plugin UUID idempotently', () => {
    const { current, server } = seed();
    const input = {
      id: '00000000-0000-4000-8000-000000000021',
      ownerMinecraftUuid: '00000000-0000-4000-8000-000000000022',
      ownerCurrentName: 'Owner',
      ownerPermissions: ['storage.region.manage.own'],
      name: 'Warehouse',
      worldUuid: '00000000-0000-4000-8000-000000000023',
      worldName: 'world',
      dimensionKey: 'minecraft:overworld',
      minX: 0, minY: 0, minZ: 0, maxX: 4, maxY: 4, maxZ: 4,
    };
    const service = new RegionService(current.db);
    const first = service.syncPluginRegion(server.id, input);
    const replay = service.syncPluginRegion(server.id, input);
    expect(first.id).toBe(input.id);
    expect(replay.id).toBe(first.id);
    expect(replay.revision).toBe(first.revision);
  });

  it('revokes only sessions belonging to the authenticated server', () => {
    const { current, server, player } = seed();
    const otherServer = new ServerRepository(current.db).create({
      name: 'Other server',
      apiKeyHash: hashApiKey('other-api-key-123456'),
      publicUrl: 'https://other.example.test',
    });
    const repository = new SessionRepository(current.db);
    const first = repository.create({ serverId: server.id, playerId: player.id, tokenHash: hashOpaqueToken('session-a-1234567890123456'), expiresAt: new Date(Date.now() + 60_000).toISOString() });
    const second = repository.create({ serverId: otherServer.id, playerId: player.id, tokenHash: hashOpaqueToken('session-b-1234567890123456'), expiresAt: new Date(Date.now() + 60_000).toISOString() });
    expect(repository.revokeByPlayerAndServer(player.id, server.id)).toBe(1);
    expect(repository.findActiveByTokenHash(first.tokenHash, new Date().toISOString())).toBeUndefined();
    expect(repository.findActiveByTokenHash(second.tokenHash, new Date().toISOString())?.id).toBe(second.id);
  });
});
