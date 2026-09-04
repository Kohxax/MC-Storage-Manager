import { getDatabaseHandle } from '../../../db/client';
import { requireWebSession } from '../../../services/auth';
import { RegionService } from '../../../services/regions';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { requiredRouteId } from '../../../utils/validation';

export default defineApiHandler((event) => {
  const { player, serverId } = requireWebSession(event);
  const id = requiredRouteId(event);
  const result = new RegionService(getDatabaseHandle().db).getWebItems(player, id, serverId);
  return apiSuccess(event, result);
});
