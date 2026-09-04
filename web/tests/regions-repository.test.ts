import { afterEach, describe, expect, it } from 'vitest';
import { createDatabase, type DatabaseHandle } from '../server/db/client';
import { RegionRepository } from '../server/db/repositories/regions';
import { createEntityId } from '../shared/types/id';
import { RevisionConflictError } from '../shared/types/revision';

let handle: DatabaseHandle | undefined;

afterEach(() => {
  handle?.close();
  handle = undefined;
});

function createMinimalSchema() {
  handle = createDatabase(':memory:');
  handle.sqlite.exec(`
    CREATE TABLE players (
      id text PRIMARY KEY NOT NULL,
      minecraft_uuid text NOT NULL UNIQUE,
      display_name text NOT NULL,
      permissions text NOT NULL DEFAULT '[]',
      linked_at text,
      created_at text NOT NULL,
      updated_at text NOT NULL,
      revision integer NOT NULL DEFAULT 0
    );
    CREATE TABLE regions (
      id text PRIMARY KEY NOT NULL,
      server_id text,
      owner_player_id text NOT NULL,
      name text NOT NULL,
      world_uuid text NOT NULL,
      world_name text NOT NULL,
      dimension_key text NOT NULL,
      min_x integer NOT NULL,
      min_y integer NOT NULL,
      min_z integer NOT NULL,
      max_x integer NOT NULL,
      max_y integer NOT NULL,
      max_z integer NOT NULL,
      status text NOT NULL DEFAULT 'active',
      last_scan_at text,
      created_at text NOT NULL,
      updated_at text NOT NULL,
      revision integer NOT NULL DEFAULT 0
    );
  `);
}

describe('RegionRepository', () => {
  it('updates using optimistic concurrency and rejects stale revisions', () => {
    createMinimalSchema();
    const ownerPlayerId = createEntityId();
    const repository = new RegionRepository(handle!.db);
    handle!.sqlite
      .prepare(
        `INSERT INTO players (id, minecraft_uuid, display_name, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?)`,
      )
      .run(ownerPlayerId, '00000000-0000-4000-8000-000000000001', 'Player', '2025-01-01T00:00:00.000Z', '2025-01-01T00:00:00.000Z');

    const created = repository.create({
      ownerPlayerId,
      name: 'Main storage',
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

    expect(created.revision).toBe(0);
    const updated = repository.update(created.id, created.revision, { name: 'Renamed storage' });
    expect(updated.name).toBe('Renamed storage');
    expect(updated.revision).toBe(1);
    expect(() => repository.update(created.id, created.revision, { name: 'Stale write' })).toThrow(RevisionConflictError);
  });
});
