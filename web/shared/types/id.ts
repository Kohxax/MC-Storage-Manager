/** Canonical identifier format used by all persisted entities and API request IDs. */
export type EntityId = string & { readonly __entityId: unique symbol };
export type ServerId = EntityId & { readonly __serverId: unique symbol };
export type PlayerId = EntityId & { readonly __playerId: unique symbol };
export type RegionId = EntityId & { readonly __regionId: unique symbol };
export type ContainerId = EntityId & { readonly __containerId: unique symbol };
export type SessionId = EntityId & { readonly __sessionId: unique symbol };
export type SyncBatchId = EntityId & { readonly __syncBatchId: unique symbol };

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isEntityId(value: unknown): value is EntityId {
  return typeof value === 'string' && UUID_PATTERN.test(value);
}

export function parseEntityId(value: string): EntityId {
  const normalized = value.trim().toLowerCase();
  if (!isEntityId(normalized)) {
    throw new TypeError('Expected a canonical UUID v4 entity ID.');
  }
  return normalized;
}

export function createEntityId(): EntityId {
  const randomUUID = globalThis.crypto?.randomUUID;
  if (!randomUUID) {
    throw new Error('Web Crypto randomUUID is unavailable in this runtime.');
  }
  return randomUUID.call(globalThis.crypto) as EntityId;
}

export function asServerId(value: string): ServerId {
  return parseEntityId(value) as ServerId;
}

export function asPlayerId(value: string): PlayerId {
  return parseEntityId(value) as PlayerId;
}

export function asRegionId(value: string): RegionId {
  return parseEntityId(value) as RegionId;
}

export function asContainerId(value: string): ContainerId {
  return parseEntityId(value) as ContainerId;
}
