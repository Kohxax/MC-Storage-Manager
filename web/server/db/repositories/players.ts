import { and, eq } from 'drizzle-orm';
import type { AppDatabase } from '../client';
import {
  playerServerPermissions,
  players,
  type NewPlayer,
  type NewPlayerServerPermissions,
  type Player,
  type PlayerServerPermissions,
} from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';

export interface SyncPlayerInput {
  minecraftUuid: string;
  displayName: string;
  permissions: string[];
}

export class PlayerRepository {
  constructor(private readonly database: AppDatabase) {}

  findById(id: string): Player | undefined {
    return this.database.select().from(players).where(eq(players.id, id)).get();
  }

  findByMinecraftUuid(minecraftUuid: string): Player | undefined {
    return this.database.select().from(players).where(eq(players.minecraftUuid, minecraftUuid)).get();
  }

  /**
   * Synchronizes the identity globally and permissions for one server only.
   *
   * The optional serverId keeps the repository usable by the bootstrap tests and
   * legacy data import, while all plugin requests must provide it. A plugin
   * sync never writes the legacy global `players.permissions` column, preventing
   * one server from changing the effective permissions of another server.
   */
  sync(input: SyncPlayerInput, serverId?: string): Player {
    return this.database.transaction((tx) => {
      const now = nowIsoDateTime();
      const existing = tx.select().from(players).where(eq(players.minecraftUuid, input.minecraftUuid)).get();
      let player: Player;

      if (!existing) {
        const values: NewPlayer = {
          id: createEntityId(),
          minecraftUuid: input.minecraftUuid,
          displayName: input.displayName,
          // This column is retained for old unscoped installations only.
          permissions: serverId ? [] : input.permissions,
          linkedAt: now,
          createdAt: now,
          updatedAt: now,
          revision: 0,
        };
        const [created] = tx.insert(players).values(values).returning().all();
        if (!created) throw new Error('Failed to sync player.');
        player = created;
      } else {
        const [updated] = tx
          .update(players)
          .set({
            displayName: input.displayName,
            ...(serverId ? {} : { permissions: input.permissions }),
            linkedAt: existing.linkedAt ?? now,
            updatedAt: now,
            revision: existing.revision + 1,
          })
          .where(eq(players.id, existing.id))
          .returning()
          .all();
        if (!updated) throw new Error('Failed to update synced player.');
        player = updated;
      }

      if (serverId) {
        const existingPermissions = tx
          .select()
          .from(playerServerPermissions)
          .where(
            and(
              eq(playerServerPermissions.serverId, serverId),
              eq(playerServerPermissions.playerId, player.id),
            ),
          )
          .get();
        if (existingPermissions) {
          tx.update(playerServerPermissions)
            .set({
              permissions: input.permissions,
              syncedAt: now,
              revision: existingPermissions.revision + 1,
            })
            .where(
              and(
                eq(playerServerPermissions.serverId, serverId),
                eq(playerServerPermissions.playerId, player.id),
              ),
            )
            .run();
        } else {
          const values: NewPlayerServerPermissions = {
            serverId,
            playerId: player.id,
            permissions: input.permissions,
            syncedAt: now,
            revision: 0,
          };
          tx.insert(playerServerPermissions).values(values).run();
        }
      }

      return player;
    });
  }

  findServerPermissions(serverId: string, playerId: string): PlayerServerPermissions | undefined {
    return this.database
      .select()
      .from(playerServerPermissions)
      .where(and(eq(playerServerPermissions.serverId, serverId), eq(playerServerPermissions.playerId, playerId)))
      .get();
  }

  permissionsFor(player: Player, serverId?: string | null): string[] {
    if (!serverId) return Array.isArray(player.permissions) ? [...player.permissions] : [];
    return this.findServerPermissions(serverId, player.id)?.permissions ?? [];
  }
}
