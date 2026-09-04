import { eq } from 'drizzle-orm';
import type { AppDatabase } from '../client';
import { servers, type NewServer, type Server } from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';

export class ServerRepository {
  constructor(private readonly database: AppDatabase) {}

  findById(id: string): Server | undefined {
    return this.database.select().from(servers).where(eq(servers.id, id)).get();
  }

  listCredentials(): Pick<Server, 'id' | 'apiKeyHash'>[] {
    return this.database
      .select({ id: servers.id, apiKeyHash: servers.apiKeyHash })
      .from(servers)
      .all();
  }

  create(input: { name: string; apiKeyHash: string; publicUrl: string; id?: string }): Server {
    const now = nowIsoDateTime();
    const values: NewServer = {
      id: input.id ?? createEntityId(),
      name: input.name,
      apiKeyHash: input.apiKeyHash,
      publicUrl: input.publicUrl,
      createdAt: now,
      updatedAt: now,
      revision: 0,
    };
    const [created] = this.database.insert(servers).values(values).returning().all();
    if (!created) throw new Error('Failed to create server.');
    return created;
  }
}
