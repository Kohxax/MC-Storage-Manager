import { z } from 'zod';
import { getDatabaseHandle } from '../../db/client';
import { requireWebSession } from '../../services/auth';
import { requireCsrf } from '../../services/csrf';
import { RegionService } from '../../services/regions';
import { apiSuccess, defineApiHandler } from '../../utils/api';
import { readSchemaBody, requiredRouteId, revisionSchema } from '../../utils/validation';

const schema = z.object({ revision: revisionSchema }).strict();

export default defineApiHandler(async (event) => {
  const { player, serverId } = requireWebSession(event);
  requireCsrf(event);
  const id = requiredRouteId(event);
  const body = await readSchemaBody(event, schema);
  const region = new RegionService(getDatabaseHandle().db).deleteWebRegion(player, id, body.revision, serverId);
  return apiSuccess(event, { region, deleted: true });
});
