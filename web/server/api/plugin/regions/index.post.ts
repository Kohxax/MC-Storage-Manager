import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { requirePluginAuth } from '../../../services/auth';
import { RegionService } from '../../../services/regions';
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
  const { bounds, ...metadata } = body;
  const region = new RegionService(getDatabaseHandle().db).syncPluginRegion(server.id, { ...metadata, ...bounds });
  return apiSuccess(event, { region }, 201);
});
