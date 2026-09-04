import { z } from 'zod';
import { getDatabaseHandle } from '../../../../db/client';
import { ChangeRepository } from '../../../../db/repositories/changes';
import { requirePluginAuth } from '../../../../services/auth';
import { apiSuccess, ApiRequestError, defineApiHandler } from '../../../../utils/api';
import { readSchemaBody, requiredRouteId, revisionSchema } from '../../../../utils/validation';
import { API_ERROR_CODES } from '../../../../../shared/types/api';

const schema = z.object({
  revision: revisionSchema,
  success: z.boolean(),
  result: z.record(z.unknown()).default({}),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const id = requiredRouteId(event);
  const body = await readSchemaBody(event, schema);
  const repository = new ChangeRepository(getDatabaseHandle().db);
  const change = repository.findById(id);
  if (!change || change.serverId !== server.id) {
    throw new ApiRequestError(API_ERROR_CODES.NOT_FOUND, 'Change was not found.', 404);
  }
  const updated = repository.applyResult(change.id, body.revision, body.success, body.result ?? {});
  return apiSuccess(event, { change: updated });
});
