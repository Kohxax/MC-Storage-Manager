import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { createLoginLink, requirePluginAuth } from '../../../services/auth';
import { PERMISSIONS, assertPermission } from '../../../services/permissions';
import { SyncService } from '../../../services/sync';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody } from '../../../utils/validation';

const schema = z.object({
  minecraftUuid: z.string().uuid(),
  currentName: z.string().trim().min(1).max(64),
  permissions: z.array(z.string().trim().min(1).max(128)).max(128).default([]),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const body = await readSchemaBody(event, schema);
  const database = getDatabaseHandle().db;
  const player = new SyncService(database).syncPlayer({
    minecraftUuid: body.minecraftUuid,
    displayName: body.currentName,
    permissions: body.permissions ?? [],
  }, server.id);
  assertPermission(database, player, PERMISSIONS.WEB_LOGIN, server.id);
  return apiSuccess(event, createLoginLink(event, server.id, player.id), 201);
});
