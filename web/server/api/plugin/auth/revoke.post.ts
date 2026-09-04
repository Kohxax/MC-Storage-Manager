import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { PlayerRepository } from '../../../db/repositories/players';
import { SessionRepository } from '../../../db/repositories/sessions';
import { requirePluginAuth } from '../../../services/auth';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody } from '../../../utils/validation';

const schema = z.object({
  minecraftUuid: z.string().uuid(),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const body = await readSchemaBody(event, schema);
  const database = getDatabaseHandle().db;
  const players = new PlayerRepository(database);
  const player = players.findByMinecraftUuid(body.minecraftUuid);

  // Do not reveal whether a UUID exists on another server. A player is
  // considered server-owned only after that server has synced its permissions.
  if (!player || !players.findServerPermissions(server.id, player.id)) {
    return apiSuccess(event, { revoked: 0 });
  }

  const revoked = new SessionRepository(database).revokeByPlayerAndServer(player.id, server.id);
  return apiSuccess(event, { revoked });
});
