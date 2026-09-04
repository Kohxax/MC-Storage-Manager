CREATE TABLE `servers` (
  `id` text PRIMARY KEY NOT NULL,
  `name` text NOT NULL,
  `api_key_hash` text NOT NULL,
  `public_url` text NOT NULL,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  CONSTRAINT `servers_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `servers_name_unique` ON `servers` (`name`);
--> statement-breakpoint
CREATE TABLE `players` (
  `id` text PRIMARY KEY NOT NULL,
  `minecraft_uuid` text NOT NULL,
  `display_name` text NOT NULL,
  `permissions` text DEFAULT '[]' NOT NULL,
  `linked_at` text,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  CONSTRAINT `players_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `players_minecraft_uuid_unique` ON `players` (`minecraft_uuid`);
--> statement-breakpoint
CREATE TABLE `web_sessions` (
  `id` text PRIMARY KEY NOT NULL,
  `player_id` text NOT NULL,
  `token_hash` text NOT NULL,
  `expires_at` text NOT NULL,
  `revoked_at` text,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  FOREIGN KEY (`player_id`) REFERENCES `players`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `web_sessions_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `web_sessions_token_hash_unique` ON `web_sessions` (`token_hash`);
--> statement-breakpoint
CREATE INDEX `web_sessions_player_idx` ON `web_sessions` (`player_id`);
--> statement-breakpoint
CREATE TABLE `login_tokens` (
  `id` text PRIMARY KEY NOT NULL,
  `server_id` text NOT NULL,
  `player_id` text,
  `token_hash` text NOT NULL,
  `expires_at` text NOT NULL,
  `consumed_at` text,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  FOREIGN KEY (`server_id`) REFERENCES `servers`(`id`) ON UPDATE no action ON DELETE cascade,
  FOREIGN KEY (`player_id`) REFERENCES `players`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE UNIQUE INDEX `login_tokens_token_hash_unique` ON `login_tokens` (`token_hash`);
--> statement-breakpoint
CREATE INDEX `login_tokens_server_idx` ON `login_tokens` (`server_id`);
--> statement-breakpoint
CREATE TABLE `regions` (
  `id` text PRIMARY KEY NOT NULL,
  `owner_player_id` text NOT NULL,
  `name` text NOT NULL,
  `world_uuid` text NOT NULL,
  `world_name` text NOT NULL,
  `dimension_key` text NOT NULL,
  `min_x` integer NOT NULL,
  `min_y` integer NOT NULL,
  `min_z` integer NOT NULL,
  `max_x` integer NOT NULL,
  `max_y` integer NOT NULL,
  `max_z` integer NOT NULL,
  `status` text DEFAULT 'active' NOT NULL,
  `last_scan_at` text,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  FOREIGN KEY (`owner_player_id`) REFERENCES `players`(`id`) ON UPDATE no action ON DELETE restrict,
  CONSTRAINT `regions_min_x_le_max_x` CHECK (`min_x` <= `max_x`),
  CONSTRAINT `regions_min_y_le_max_y` CHECK (`min_y` <= `max_y`),
  CONSTRAINT `regions_min_z_le_max_z` CHECK (`min_z` <= `max_z`),
  CONSTRAINT `regions_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE INDEX `regions_owner_idx` ON `regions` (`owner_player_id`);
--> statement-breakpoint
CREATE INDEX `regions_world_idx` ON `regions` (`world_uuid`,`dimension_key`);
--> statement-breakpoint
CREATE TABLE `region_acl` (
  `region_id` text NOT NULL,
  `player_id` text,
  `group_key` text,
  `principal_type` text NOT NULL,
  `permission` text NOT NULL,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  PRIMARY KEY(`region_id`, `principal_type`, `player_id`, `group_key`),
  FOREIGN KEY (`region_id`) REFERENCES `regions`(`id`) ON UPDATE no action ON DELETE cascade,
  FOREIGN KEY (`player_id`) REFERENCES `players`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `region_acl_principal_present` CHECK ((`player_id` IS NOT NULL AND `principal_type` = 'player') OR (`group_key` IS NOT NULL AND `principal_type` = 'group'))
);
--> statement-breakpoint
CREATE INDEX `region_acl_player_idx` ON `region_acl` (`player_id`);
--> statement-breakpoint
CREATE TABLE `containers` (
  `id` text PRIMARY KEY NOT NULL,
  `region_id` text NOT NULL,
  `world_uuid` text NOT NULL,
  `x` integer NOT NULL,
  `y` integer NOT NULL,
  `z` integer NOT NULL,
  `container_type` text NOT NULL,
  `normalized_position` text NOT NULL,
  `last_verified_at` text,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  FOREIGN KEY (`region_id`) REFERENCES `regions`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `containers_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `containers_position_unique` ON `containers` (`world_uuid`,`normalized_position`);
--> statement-breakpoint
CREATE INDEX `containers_region_idx` ON `containers` (`region_id`);
--> statement-breakpoint
CREATE TABLE `container_items` (
  `id` text PRIMARY KEY NOT NULL,
  `container_id` text NOT NULL,
  `item_key` text NOT NULL,
  `variant_key` text DEFAULT '' NOT NULL,
  `amount` integer DEFAULT 0 NOT NULL,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  FOREIGN KEY (`container_id`) REFERENCES `containers`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `container_items_amount_non_negative` CHECK (`amount` >= 0),
  CONSTRAINT `container_items_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `container_items_key_unique` ON `container_items` (`container_id`,`item_key`,`variant_key`);
--> statement-breakpoint
CREATE TABLE `sync_batches` (
  `id` text PRIMARY KEY NOT NULL,
  `server_id` text NOT NULL,
  `idempotency_key` text NOT NULL,
  `status` text NOT NULL,
  `received_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `completed_at` text,
  `revision` integer DEFAULT 0 NOT NULL,
  FOREIGN KEY (`server_id`) REFERENCES `servers`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `sync_batches_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE UNIQUE INDEX `sync_batches_idempotency_unique` ON `sync_batches` (`server_id`,`idempotency_key`);
--> statement-breakpoint
CREATE INDEX `sync_batches_status_idx` ON `sync_batches` (`status`);
