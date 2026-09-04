import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { requirePluginAuth } from '../../../services/auth';
import { RegionService } from '../../../services/regions';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody, requiredRouteId, revisionSchema } from '../../../utils/validation';

const schema = z.object({
  revision: revisionSchema,
  name: z.string().trim().min(1).max(128).optional(),
  worldName: z.string().trim().min(1).max(128).optional(),
  dimensionKey: z.string().trim().min(1).max(128).optional(),
  status: z.enum(['active', 'invalid', 'deleted']).optional(),
  lastScanAt: z.string().datetime({ offset: true }).nullable().optional(),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const id = requiredRouteId(event);
  const body = await readSchemaBody(event, schema);
  const { revision, ...patch } = body;
  const region = new RegionService(getDatabaseHandle().db).updatePluginRegion(server.id, id, revision, patch);
  return apiSuccess(event, { region });
});
