import { nextTick, ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { useInventory } from '../app/composables/useInventory';
import type { ApiSuccessResponse } from '../shared/types/api';
import type { Region, RegionItems } from '../shared/types/storage';

function makeRegion(lastScanAt: string | null): Region {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    ownerPlayerId: '00000000-0000-4000-8000-000000000002',
    name: 'Main',
    worldName: 'world',
    dimensionKey: 'minecraft:overworld',
    status: 'active',
    updatedAt: '2026-09-06T00:00:00.000Z',
    lastScanAt,
    revision: 0,
  };
}

function response(region: Region, itemKey: string): ApiSuccessResponse<RegionItems> {
  return { data: { region, containers: [{ items: [{ itemKey, amount: 1 }] }] } };
}

describe('useInventory refresh behavior', () => {
  it('reloads a cached region when its scan marker changes', async () => {
    const first = makeRegion(null);
    const scanned = makeRegion('2026-09-06T00:00:10.000Z');
    const fetch = vi.fn()
      .mockResolvedValueOnce(response(first, 'minecraft:stone'))
      .mockResolvedValueOnce(response(scanned, 'minecraft:dirt'));
    vi.stubGlobal('$fetch', fetch);
    const regions = ref([first]);
    const selected = ref('all');
    const state = useInventory(regions, selected);

    await vi.waitFor(() => expect(state.items.value).toEqual([{ itemKey: 'minecraft:stone', amount: 1 }]));
    expect(fetch).toHaveBeenCalledTimes(1);
    regions.value = [scanned];
    await vi.waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    await nextTick();
    expect(state.items.value).toEqual([{ itemKey: 'minecraft:dirt', amount: 1 }]);
  });

  it('shares an in-flight inventory request between concurrent loads', async () => {
    const region = makeRegion(null);
    let resolveRequest!: (value: ApiSuccessResponse<RegionItems>) => void;
    const request = new Promise<ApiSuccessResponse<RegionItems>>(resolve => { resolveRequest = resolve; });
    const fetch = vi.fn().mockReturnValue(request);
    vi.stubGlobal('$fetch', fetch);
    const regions = ref([region]);
    const selected = ref('all');
    const state = useInventory(regions, selected);

    void state.load();
    void state.load();
    expect(fetch).toHaveBeenCalledTimes(1);
    resolveRequest(response(region, 'minecraft:stone'));
    await vi.waitFor(() => expect(state.items.value).toEqual([{ itemKey: 'minecraft:stone', amount: 1 }]));
  });
});
