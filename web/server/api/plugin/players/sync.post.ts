import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { requirePluginAuth } from '../../../services/auth';
import { SyncService } from '../../../services/sync';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody } from '../../../utils/validation';

const schema = z.object({
  minecraftUuid: z.string().uuid(),
  displayName: z.string().trim().min(1).max(64),
  permissions: z.array(z.string().trim().min(1).max(128)).max(128).default([]),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const body = await readSchemaBody(event, schema);
  const player = new SyncService(getDatabaseHandle().db).syncPlayer({ ...body, permissions: body.permissions ?? [] }, server.id);
  return apiSuccess(event, { player }, 200);
});
