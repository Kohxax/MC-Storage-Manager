import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { requirePluginAuth } from '../../../services/auth';
import { RegionService } from '../../../services/regions';
import { publishStorageEvent } from '../../../services/events';
import { RegionRepository } from '../../../db/repositories/regions';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody } from '../../../utils/validation';

const schema = z.object({
  id: z.string().uuid(),
  ownerMinecraftUuid: z.string().uuid(),
  ownerCurrentName: z.string().trim().min(1).max(64).optional(),
  ownerPermissions: z.array(z.string().trim().min(1).max(128)).max(128).optional(),
  name: z.string().trim().min(1).max(128),
  worldUuid: z.string().trim().min(1).max(128),
  worldName: z.string().trim().min(1).max(128),
  dimensionKey: z.string().trim().min(1).max(128),
  status: z.enum(['active', 'invalid']).default('active'),
  bounds: z.object({
    minX: z.number().int().safe(),
    minY: z.number().int().safe(),
    minZ: z.number().int().safe(),
    maxX: z.number().int().safe(),
    maxY: z.number().int().safe(),
    maxZ: z.number().int().safe(),
  }).strict(),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const body = await readSchemaBody(event, schema);
  const database = getDatabaseHandle().db;
  const { bounds, ...metadata } = body;
  const before = new RegionRepository(database).findById(body.id);
  const region = new RegionService(database).syncPluginRegion(server.id, { ...metadata, ...bounds });
  if (!before || before.revision !== region.revision) {
    publishStorageEvent(server.id, { type: before ? 'region.updated' : 'region.created', regionId: region.id });
  }
  return apiSuccess(event, { region }, 201);
});
