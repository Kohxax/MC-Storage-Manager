/**
 * In-process, server-scoped fan-out for live Web updates.
 *
 * The event payload intentionally contains no data. Clients must re-fetch
 * through the authenticated API, which remains the source of authorization.
 */
export type StorageUpdateType = 'region.created' | 'region.updated' | 'region.deleted' | 'inventory.updated';

export interface StorageUpdateEvent {
  type: StorageUpdateType;
  regionId: string;
}

export interface StorageEventSubscriber {
  push(message: { event: string; data: string }): Promise<void>;
}

const subscribersByServer = new Map<string, Set<StorageEventSubscriber>>();

export function subscribeStorageEvents(serverId: string, subscriber: StorageEventSubscriber): () => void {
  let subscribers = subscribersByServer.get(serverId);
  if (!subscribers) {
    subscribers = new Set();
    subscribersByServer.set(serverId, subscribers);
  }
  subscribers.add(subscriber);

  let active = true;
  return () => {
    if (!active) return;
    active = false;
    subscribers?.delete(subscriber);
    if (subscribers?.size === 0) subscribersByServer.delete(serverId);
  };
}

export function publishStorageEvent(serverId: string, update: StorageUpdateEvent): void {
  const subscribers = subscribersByServer.get(serverId);
  if (!subscribers?.size) return;
  const message = { event: 'storage:update', data: JSON.stringify(update) };
  for (const subscriber of [...subscribers]) {
    void subscriber.push(message).catch(() => {
      // Closed streams are normally removed by onClosed; this also prevents
      // a rejected write from becoming an unhandled promise rejection.
    });
  }
}
