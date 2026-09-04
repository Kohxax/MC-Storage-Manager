ALTER TABLE `regions` ADD `server_id` text REFERENCES `servers`(`id`) ON UPDATE no action ON DELETE cascade;
--> statement-breakpoint
CREATE INDEX `regions_server_idx` ON `regions` (`server_id`);
--> statement-breakpoint
CREATE TABLE `player_server_permissions` (
  `server_id` text NOT NULL,
  `player_id` text NOT NULL,
  `permissions` text DEFAULT '[]' NOT NULL,
  `synced_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  PRIMARY KEY(`server_id`, `player_id`),
  FOREIGN KEY (`server_id`) REFERENCES `servers`(`id`) ON UPDATE no action ON DELETE cascade,
  FOREIGN KEY (`player_id`) REFERENCES `players`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `player_server_permissions_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
ALTER TABLE `sync_batches` ADD `request_payload` text;
--> statement-breakpoint
ALTER TABLE `sync_batches` ADD `result_payload` text;
--> statement-breakpoint
ALTER TABLE `web_sessions` ADD `server_id` text REFERENCES `servers`(`id`) ON UPDATE no action ON DELETE cascade;
--> statement-breakpoint
CREATE INDEX `web_sessions_server_idx` ON `web_sessions` (`server_id`);
--> statement-breakpoint
CREATE TABLE `changes` (
  `id` text PRIMARY KEY NOT NULL,
  `server_id` text NOT NULL,
  `region_id` text,
  `player_id` text NOT NULL,
  `operation` text NOT NULL,
  `status` text DEFAULT 'pending' NOT NULL,
  `payload` text NOT NULL,
  `result` text,
  `created_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `updated_at` text DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')) NOT NULL,
  `revision` integer DEFAULT 0 NOT NULL,
  FOREIGN KEY (`server_id`) REFERENCES `servers`(`id`) ON UPDATE no action ON DELETE cascade,
  FOREIGN KEY (`region_id`) REFERENCES `regions`(`id`) ON UPDATE no action ON DELETE set null,
  FOREIGN KEY (`player_id`) REFERENCES `players`(`id`) ON UPDATE no action ON DELETE cascade,
  CONSTRAINT `changes_revision_non_negative` CHECK (`revision` >= 0)
);
--> statement-breakpoint
CREATE INDEX `changes_server_status_idx` ON `changes` (`server_id`,`status`,`created_at`);
--> statement-breakpoint
CREATE INDEX `changes_region_idx` ON `changes` (`region_id`);
