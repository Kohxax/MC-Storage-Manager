import { relations, sql } from 'drizzle-orm';
import {
  check,
  index,
  integer,
  primaryKey,
  sqliteTable,
  text,
  uniqueIndex,
} from 'drizzle-orm/sqlite-core';

const revisionDefault = sql`0`;
const nowDefault = sql`(strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))`;

export const servers = sqliteTable(
  'servers',
  {
    id: text('id').primaryKey(),
    name: text('name').notNull(),
    apiKeyHash: text('api_key_hash').notNull(),
    publicUrl: text('public_url').notNull(),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    uniqueIndex('servers_name_unique').on(table.name),
    check('servers_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const players = sqliteTable(
  'players',
  {
    id: text('id').primaryKey(),
    minecraftUuid: text('minecraft_uuid').notNull(),
    displayName: text('display_name').notNull(),
    permissions: text('permissions', { mode: 'json' }).$type<string[]>().notNull().default([]),
    linkedAt: text('linked_at'),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    uniqueIndex('players_minecraft_uuid_unique').on(table.minecraftUuid),
    check('players_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const playerServerPermissions = sqliteTable(
  'player_server_permissions',
  {
    serverId: text('server_id')
      .notNull()
      .references(() => servers.id, { onDelete: 'cascade' }),
    playerId: text('player_id')
      .notNull()
      .references(() => players.id, { onDelete: 'cascade' }),
    permissions: text('permissions', { mode: 'json' }).$type<string[]>().notNull().default([]),
    syncedAt: text('synced_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    primaryKey({ columns: [table.serverId, table.playerId] }),
    check('player_server_permissions_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const webSessions = sqliteTable(
  'web_sessions',
  {
    id: text('id').primaryKey(),
    serverId: text('server_id').references(() => servers.id, { onDelete: 'cascade' }),
    playerId: text('player_id')
      .notNull()
      .references(() => players.id, { onDelete: 'cascade' }),
    tokenHash: text('token_hash').notNull(),
    expiresAt: text('expires_at').notNull(),
    revokedAt: text('revoked_at'),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    uniqueIndex('web_sessions_token_hash_unique').on(table.tokenHash),
    index('web_sessions_player_idx').on(table.playerId),
    check('web_sessions_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const loginTokens = sqliteTable(
  'login_tokens',
  {
    id: text('id').primaryKey(),
    serverId: text('server_id')
      .notNull()
      .references(() => servers.id, { onDelete: 'cascade' }),
    playerId: text('player_id').references(() => players.id, { onDelete: 'set null' }),
    tokenHash: text('token_hash').notNull(),
    expiresAt: text('expires_at').notNull(),
    consumedAt: text('consumed_at'),
    createdAt: text('created_at').notNull().default(nowDefault),
  },
  (table) => [
    uniqueIndex('login_tokens_token_hash_unique').on(table.tokenHash),
    index('login_tokens_server_idx').on(table.serverId),
  ],
);

export const regions = sqliteTable(
  'regions',
  {
    id: text('id').primaryKey(),
    serverId: text('server_id').references(() => servers.id, { onDelete: 'cascade' }),
    ownerPlayerId: text('owner_player_id')
      .notNull()
      .references(() => players.id, { onDelete: 'restrict' }),
    name: text('name').notNull(),
    worldUuid: text('world_uuid').notNull(),
    worldName: text('world_name').notNull(),
    dimensionKey: text('dimension_key').notNull(),
    minX: integer('min_x').notNull(),
    minY: integer('min_y').notNull(),
    minZ: integer('min_z').notNull(),
    maxX: integer('max_x').notNull(),
    maxY: integer('max_y').notNull(),
    maxZ: integer('max_z').notNull(),
    status: text('status').$type<'active' | 'invalid' | 'deleted'>().notNull().default('active'),
    lastScanAt: text('last_scan_at'),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    index('regions_owner_idx').on(table.ownerPlayerId),
    index('regions_world_idx').on(table.worldUuid, table.dimensionKey),
    check('regions_min_x_le_max_x', sql`${table.minX} <= ${table.maxX}`),
    check('regions_min_y_le_max_y', sql`${table.minY} <= ${table.maxY}`),
    check('regions_min_z_le_max_z', sql`${table.minZ} <= ${table.maxZ}`),
    check('regions_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const regionAcl = sqliteTable(
  'region_acl',
  {
    regionId: text('region_id')
      .notNull()
      .references(() => regions.id, { onDelete: 'cascade' }),
    playerId: text('player_id').references(() => players.id, { onDelete: 'cascade' }),
    groupKey: text('group_key'),
    principalType: text('principal_type').$type<'player' | 'group'>().notNull(),
    permission: text('permission').$type<'viewer' | 'manager'>().notNull(),
    createdAt: text('created_at').notNull().default(nowDefault),
  },
  (table) => [
    primaryKey({ columns: [table.regionId, table.principalType, table.playerId, table.groupKey] }),
    index('region_acl_player_idx').on(table.playerId),
    check(
      'region_acl_principal_present',
      sql`(${table.playerId} IS NOT NULL AND ${table.principalType} = 'player') OR (${table.groupKey} IS NOT NULL AND ${table.principalType} = 'group')`,
    ),
  ],
);

export const containers = sqliteTable(
  'containers',
  {
    id: text('id').primaryKey(),
    regionId: text('region_id')
      .notNull()
      .references(() => regions.id, { onDelete: 'cascade' }),
    worldUuid: text('world_uuid').notNull(),
    x: integer('x').notNull(),
    y: integer('y').notNull(),
    z: integer('z').notNull(),
    containerType: text('container_type').notNull(),
    normalizedPosition: text('normalized_position').notNull(),
    lastVerifiedAt: text('last_verified_at'),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    uniqueIndex('containers_position_unique').on(table.worldUuid, table.normalizedPosition),
    index('containers_region_idx').on(table.regionId),
    check('containers_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const containerItems = sqliteTable(
  'container_items',
  {
    id: text('id').primaryKey(),
    containerId: text('container_id')
      .notNull()
      .references(() => containers.id, { onDelete: 'cascade' }),
    itemKey: text('item_key').notNull(),
    variantKey: text('variant_key').notNull().default(''),
    amount: integer('amount').notNull().default(0),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    uniqueIndex('container_items_key_unique').on(table.containerId, table.itemKey, table.variantKey),
    check('container_items_amount_non_negative', sql`${table.amount} >= 0`),
    check('container_items_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const syncBatches = sqliteTable(
  'sync_batches',
  {
    id: text('id').primaryKey(),
    serverId: text('server_id')
      .notNull()
      .references(() => servers.id, { onDelete: 'cascade' }),
    idempotencyKey: text('idempotency_key').notNull(),
    status: text('status').$type<'received' | 'processing' | 'completed' | 'failed'>().notNull(),
    requestPayload: text('request_payload', { mode: 'json' }).$type<unknown>(),
    resultPayload: text('result_payload', { mode: 'json' }).$type<unknown>(),
    receivedAt: text('received_at').notNull().default(nowDefault),
    completedAt: text('completed_at'),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    uniqueIndex('sync_batches_idempotency_unique').on(table.serverId, table.idempotencyKey),
    index('sync_batches_status_idx').on(table.status),
    check('sync_batches_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const changes = sqliteTable(
  'changes',
  {
    id: text('id').primaryKey(),
    serverId: text('server_id')
      .notNull()
      .references(() => servers.id, { onDelete: 'cascade' }),
    regionId: text('region_id').references(() => regions.id, { onDelete: 'set null' }),
    playerId: text('player_id')
      .notNull()
      .references(() => players.id, { onDelete: 'cascade' }),
    operation: text('operation').$type<'region.update' | 'region.delete' | 'acl.update'>().notNull(),
    status: text('status').$type<'pending' | 'sent' | 'applied' | 'failed' | 'cancelled'>().notNull().default('pending'),
    payload: text('payload', { mode: 'json' }).$type<Record<string, unknown>>().notNull(),
    result: text('result', { mode: 'json' }).$type<Record<string, unknown> | null>(),
    createdAt: text('created_at').notNull().default(nowDefault),
    updatedAt: text('updated_at').notNull().default(nowDefault),
    revision: integer('revision').notNull().default(revisionDefault),
  },
  (table) => [
    index('changes_server_status_idx').on(table.serverId, table.status, table.createdAt),
    index('changes_region_idx').on(table.regionId),
    check('changes_revision_non_negative', sql`${table.revision} >= 0`),
  ],
);

export const playersRelations = relations(players, ({ many }) => ({
  regions: many(regions),
  sessions: many(webSessions),
  aclEntries: many(regionAcl),
  serverPermissions: many(playerServerPermissions),
}));

export const playerServerPermissionsRelations = relations(playerServerPermissions, ({ one }) => ({
  server: one(servers, { fields: [playerServerPermissions.serverId], references: [servers.id] }),
  player: one(players, { fields: [playerServerPermissions.playerId], references: [players.id] }),
}));

export const regionsRelations = relations(regions, ({ one, many }) => ({
  server: one(servers, { fields: [regions.serverId], references: [servers.id] }),
  owner: one(players, { fields: [regions.ownerPlayerId], references: [players.id] }),
  aclEntries: many(regionAcl),
  containers: many(containers),
}));

export const regionAclRelations = relations(regionAcl, ({ one }) => ({
  region: one(regions, { fields: [regionAcl.regionId], references: [regions.id] }),
  player: one(players, { fields: [regionAcl.playerId], references: [players.id] }),
}));

export const containersRelations = relations(containers, ({ one, many }) => ({
  region: one(regions, { fields: [containers.regionId], references: [regions.id] }),
  items: many(containerItems),
}));

export const containerItemsRelations = relations(containerItems, ({ one }) => ({
  container: one(containers, { fields: [containerItems.containerId], references: [containers.id] }),
}));

export const webSessionsRelations = relations(webSessions, ({ one }) => ({
  player: one(players, { fields: [webSessions.playerId], references: [players.id] }),
}));

export const changesRelations = relations(changes, ({ one }) => ({
  server: one(servers, { fields: [changes.serverId], references: [servers.id] }),
  region: one(regions, { fields: [changes.regionId], references: [regions.id] }),
  player: one(players, { fields: [changes.playerId], references: [players.id] }),
}));

export const serversRelations = relations(servers, ({ many }) => ({
  playerPermissions: many(playerServerPermissions),
  sessions: many(webSessions),
  changes: many(changes),
}));

export const schema = {
  servers,
  players,
  playerServerPermissions,
  webSessions,
  loginTokens,
  regions,
  regionAcl,
  containers,
  containerItems,
  syncBatches,
  changes,
};

export type Server = typeof servers.$inferSelect;
export type NewServer = typeof servers.$inferInsert;
export type Player = typeof players.$inferSelect;
export type NewPlayer = typeof players.$inferInsert;
export type PlayerServerPermissions = typeof playerServerPermissions.$inferSelect;
export type NewPlayerServerPermissions = typeof playerServerPermissions.$inferInsert;
export type Region = typeof regions.$inferSelect;
export type NewRegion = typeof regions.$inferInsert;
export type Container = typeof containers.$inferSelect;
export type NewContainer = typeof containers.$inferInsert;
export type ContainerItem = typeof containerItems.$inferSelect;
export type NewContainerItem = typeof containerItems.$inferInsert;
export type WebSession = typeof webSessions.$inferSelect;
export type NewWebSession = typeof webSessions.$inferInsert;
export type LoginToken = typeof loginTokens.$inferSelect;
export type NewLoginToken = typeof loginTokens.$inferInsert;
export type Change = typeof changes.$inferSelect;
export type NewChange = typeof changes.$inferInsert;
export type SyncBatch = typeof syncBatches.$inferSelect;
export type NewSyncBatch = typeof syncBatches.$inferInsert;
