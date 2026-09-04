import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { requirePluginAuth } from '../../../services/auth';
import { RegionService } from '../../../services/regions';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody, requiredRouteId, revisionSchema } from '../../../utils/validation';

const schema = z.object({ revision: revisionSchema }).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const id = requiredRouteId(event);
  const body = await readSchemaBody(event, schema);
  new RegionService(getDatabaseHandle().db).deletePluginRegion(server.id, id, body.revision);
  return apiSuccess(event, { deleted: true });
});
