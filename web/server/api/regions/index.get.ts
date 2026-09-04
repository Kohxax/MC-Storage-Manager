import { getDatabaseHandle } from '../../db/client';
import { requireWebSession } from '../../services/auth';
import { RegionService } from '../../services/regions';
import { apiSuccess, defineApiHandler } from '../../utils/api';

export default defineApiHandler((event) => {
  const { player, serverId } = requireWebSession(event);
  const regions = new RegionService(getDatabaseHandle().db).listWebRegions(player, serverId);
  return apiSuccess(event, { regions });
});
