import { getQuery } from 'h3';
import { getDatabaseHandle } from '../../../db/client';
import { ChangeRepository } from '../../../db/repositories/changes';
import { requirePluginAuth } from '../../../services/auth';
import { apiSuccess, defineApiHandler } from '../../../utils/api';

function queryString(value: unknown): string | undefined {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : undefined;
  return typeof value === 'string' ? value : undefined;
}

export default defineApiHandler((event) => {
  const { server } = requirePluginAuth(event);
  const query = getQuery(event);
  const parsedLimit = Number(query.limit ?? 50);
  const limit = Number.isSafeInteger(parsedLimit) ? Math.min(Math.max(parsedLimit, 1), 100) : 50;
  const cursor = queryString(query.cursor);
  const changes = new ChangeRepository(getDatabaseHandle().db).listForServer(server.id, { cursor, limit });
  return apiSuccess(event, {
    changes,
    nextCursor: changes.length === limit ? changes.at(-1)?.createdAt ?? null : null,
  });
});
