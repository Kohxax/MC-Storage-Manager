import { z } from 'zod';
import { getDatabaseHandle } from '../../../db/client';
import { requirePluginAuth } from '../../../services/auth';
import { SyncService } from '../../../services/sync';
import { apiSuccess, defineApiHandler } from '../../../utils/api';
import { readSchemaBody } from '../../../utils/validation';

const itemSchema = z.object({
  itemKey: z.string().trim().min(1).max(256),
  variantKey: z.string().trim().max(256).optional(),
  amount: z.number().int().nonnegative().safe(),
}).strict();

const containerSchema = z.object({
  id: z.string().uuid().optional(),
  worldUuid: z.string().uuid(),
  x: z.number().int().safe(),
  y: z.number().int().safe(),
  z: z.number().int().safe(),
  containerType: z.string().regex(/^[a-z0-9_.-]+:[a-z0-9/._-]+$/),
  deleted: z.boolean().default(false),
  items: z.array(itemSchema).max(256),
}).strict().superRefine((container, context) => {
  if (container.deleted && container.items.length > 0) {
    context.addIssue({ code: z.ZodIssueCode.custom, path: ['items'], message: 'Deleted containers must not contain items.' });
  }
});

const schema = z.object({
  regionId: z.string().uuid(),
  idempotencyKey: z.string().trim().min(8).max(128),
  containers: z.array(containerSchema).max(500),
}).strict();

export default defineApiHandler(async (event) => {
  const { server } = requirePluginAuth(event);
  const body = await readSchemaBody(event, schema);
  const result = new SyncService(getDatabaseHandle().db).saveContainerBatch(
    server.id,
    body.regionId,
    body.idempotencyKey,
    body.containers,
  );
  return apiSuccess(event, result);
});
