import { z } from 'zod';
import { getDatabaseHandle } from '../../db/client';
import { requireWebSession } from '../../services/auth';
import { requireCsrf } from '../../services/csrf';
import { RegionService } from '../../services/regions';
import { apiSuccess, defineApiHandler } from '../../utils/api';
import { readSchemaBody, requiredRouteId, revisionSchema } from '../../utils/validation';

const schema = z.object({
  revision: revisionSchema,
  name: z.string().trim().min(1).max(128).optional(),
  worldName: z.string().trim().min(1).max(128).optional(),
  dimensionKey: z.string().trim().min(1).max(128).optional(),
  status: z.enum(['active', 'invalid']).optional(),
}).strict();

export default defineApiHandler(async (event) => {
  const { player, serverId } = requireWebSession(event);
  requireCsrf(event);
  const id = requiredRouteId(event);
  const body = await readSchemaBody(event, schema);
  const { revision, ...patch } = body;
  const region = new RegionService(getDatabaseHandle().db).updateWebRegion(player, id, revision, patch, serverId);
  return apiSuccess(event, { region });
});
