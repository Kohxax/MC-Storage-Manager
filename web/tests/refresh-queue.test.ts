import { describe, expect, it, vi } from 'vitest';
import { createRefreshQueue } from '../app/composables/useRefreshQueue';

describe('refresh queue', () => {
  it('runs one trailing refresh when another event arrives in flight', async () => {
    let resolveFirst!: () => void;
    let resolveSecond!: () => void;
    const refresh = vi.fn()
      .mockImplementationOnce(() => new Promise<void>(resolve => { resolveFirst = resolve; }))
      .mockImplementationOnce(() => new Promise<void>(resolve => { resolveSecond = resolve; }));
    const queue = createRefreshQueue(refresh);

    const first = queue.request();
    queue.request();
    queue.request();
    expect(refresh).toHaveBeenCalledTimes(1);

    resolveFirst();
    await first;
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
    resolveSecond();
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  });
});
