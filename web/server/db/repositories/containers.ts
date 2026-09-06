import { and, eq } from 'drizzle-orm';
import type { AppDatabase, DatabaseExecutor } from '../client';
import { containerItems, containers, type Container, type ContainerItem } from '../schema';
import { createEntityId } from '../../../shared/types/id';
import { nowIsoDateTime } from '../../../shared/types/datetime';

export type { DatabaseExecutor } from '../client';

export interface ContainerItemInput {
  itemKey: string;
  variantKey?: string;
  amount: number;
}

export interface ContainerInput {
  id?: string;
  worldUuid: string;
  x: number;
  y: number;
  z: number;
  containerType: string;
  items: ContainerItemInput[];
  /** Explicit deletion is distinct from an empty inventory. */
  deleted?: boolean;
}

export interface SavedContainer {
  container: Container;
  items: ContainerItem[];
}

export class ContainerScopeError extends Error {
  constructor() {
    super('Container belongs to another region.');
    this.name = 'ContainerScopeError';
  }
}

export function normalizedPosition(x: number, y: number, z: number): string {
  return `${x},${y},${z}`;
}

export class ContainerRepository {
  constructor(private readonly database: AppDatabase) {}

  listItemsByRegion(regionId: string): SavedContainer[] {
    const rows = this.database
      .select({ container: containers, item: containerItems })
      .from(containers)
      .leftJoin(containerItems, eq(containerItems.containerId, containers.id))
      .where(eq(containers.regionId, regionId))
      .all();
    const grouped = new Map<string, SavedContainer>();
    for (const row of rows) {
      const current = grouped.get(row.container.id) ?? { container: row.container, items: [] };
      if (row.item) current.items.push(row.item);
      grouped.set(row.container.id, current);
    }
    return [...grouped.values()];
  }

  /** Applies one server batch in a transaction. */
  saveBatch(regionId: string, inputs: ContainerInput[]): SavedContainer[] {
    return this.database.transaction((tx) => this.saveBatchInTransaction(tx, regionId, inputs));
  }

  /** Applies a batch using a caller-owned transaction. */
  saveBatchInTransaction(database: DatabaseExecutor, regionId: string, inputs: ContainerInput[]): SavedContainer[] {
    const saved: SavedContainer[] = [];
    for (const input of inputs) {
      const position = normalizedPosition(input.x, input.y, input.z);
      const byPosition = database
        .select()
        .from(containers)
        .where(and(eq(containers.worldUuid, input.worldUuid), eq(containers.normalizedPosition, position)))
        .get();
      const byId = input.id ? database.select().from(containers).where(eq(containers.id, input.id)).get() : undefined;
      if (byId && byPosition && byId.id !== byPosition.id) {
        // A caller cannot use an ID from one physical container to overwrite a
        // second container at the submitted position.
        throw new ContainerScopeError();
      }
      const existing = byId ?? byPosition;
      if (existing && existing.regionId !== regionId) {
        throw new ContainerScopeError();
      }

      if (input.deleted) {
        if (existing) {
          database.delete(containers).where(eq(containers.id, existing.id)).run();
        }
        continue;
      }

      const now = nowIsoDateTime();
      let container: Container;
      if (existing) {
        const [updated] = database
          .update(containers)
          .set({
            worldUuid: input.worldUuid,
            x: input.x,
            y: input.y,
            z: input.z,
            containerType: input.containerType,
            normalizedPosition: position,
            lastVerifiedAt: now,
            updatedAt: now,
            revision: existing.revision + 1,
          })
          .where(eq(containers.id, existing.id))
          .returning()
          .all();
        if (!updated) throw new Error('Failed to update container.');
        container = updated;
      } else {
        const [created] = database
          .insert(containers)
          .values({
            id: input.id ?? createEntityId(),
            regionId,
            worldUuid: input.worldUuid,
            x: input.x,
            y: input.y,
            z: input.z,
            containerType: input.containerType,
            normalizedPosition: position,
            lastVerifiedAt: now,
            createdAt: now,
            updatedAt: now,
            revision: 0,
          })
          .returning()
          .all();
        if (!created) throw new Error('Failed to create container.');
        container = created;
      }

      database.delete(containerItems).where(eq(containerItems.containerId, container.id)).run();
      if (input.items.length > 0) {
        database
          .insert(containerItems)
          .values(
            input.items.map((item) => ({
              id: createEntityId(),
              containerId: container.id,
              itemKey: item.itemKey,
              variantKey: item.variantKey ?? '',
              amount: item.amount,
              createdAt: now,
              updatedAt: now,
              revision: 0,
            })),
          )
          .run();
      }
      saved.push({
        container,
        items: database.select().from(containerItems).where(eq(containerItems.containerId, container.id)).all(),
      });
    }
    return saved;
  }
}
