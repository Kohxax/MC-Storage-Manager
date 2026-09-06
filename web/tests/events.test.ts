import { describe, expect, it, vi } from 'vitest';
import { publishStorageEvent, subscribeStorageEvents } from '../server/services/events';

describe('storage event hub', () => {
  it('fans out only within the matching server scope', async () => {
    const first = { push: vi.fn().mockResolvedValue(undefined) };
    const other = { push: vi.fn().mockResolvedValue(undefined) };
    const unsubscribeFirst = subscribeStorageEvents('server-a', first);
    const unsubscribeOther = subscribeStorageEvents('server-b', other);

    publishStorageEvent('server-a', { type: 'inventory.updated', regionId: 'region-1' });
    await Promise.resolve();

    expect(first.push).toHaveBeenCalledWith({
      event: 'storage:update',
      data: JSON.stringify({ type: 'inventory.updated', regionId: 'region-1' }),
    });
    expect(other.push).not.toHaveBeenCalled();
    unsubscribeFirst();
    unsubscribeOther();
  });

  it('stops publishing after a subscriber is closed', async () => {
    const subscriber = { push: vi.fn().mockResolvedValue(undefined) };
    const unsubscribe = subscribeStorageEvents('server-a', subscriber);
    unsubscribe();
    publishStorageEvent('server-a', { type: 'region.created', regionId: 'region-2' });
    await Promise.resolve();
    expect(subscriber.push).not.toHaveBeenCalled();
  });
});
