import { sql } from 'drizzle-orm';
import type { IsoDateTime } from '../../shared/types/datetime';
import { nowIsoDateTime } from '../../shared/types/datetime';
import { apiSuccess, defineApiHandler } from '../utils/api';
import { getDatabaseHandle } from '../db/client';

export interface HealthResponse {
  status: 'ok';
  service: 'mc-storage-manager-web';
  database: 'ok';
  timestamp: IsoDateTime;
}

export default defineApiHandler((event) => {
  const { db } = getDatabaseHandle();
  db.run(sql`SELECT 1`);
  const data: HealthResponse = {
    status: 'ok',
    service: 'mc-storage-manager-web',
    database: 'ok',
    timestamp: nowIsoDateTime(),
  };
  return apiSuccess(event, data);
});
