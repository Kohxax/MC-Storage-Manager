import { computed, ref, watch, type Ref } from 'vue';
import type { ApiSuccessResponse } from '#shared/types/api';
import type { InventoryItem, Region, RegionItems } from '#shared/types/storage';

export function useInventory(regions: Ref<Region[]>, selectedRegion: Ref<string>) {
  const inventories = ref<Record<string, RegionItems>>({});
  const loading = ref(false);
  const error = ref('');
  const query = ref('');
  const sortKey = ref<'name' | 'amount'>('name');
  const sortDirection = ref<'asc' | 'desc'>('asc');
  const view = ref<'table' | 'grid'>('table');

  // A region can stay selected while the plugin updates its containers, so
  // presence in `inventories` alone is not sufficient to decide freshness.
  const loadedScanAt = new Map<string, string>();
  const inFlight = new Map<string, Promise<ApiSuccessResponse<RegionItems>>>();
  let activeLoads = 0;

  function scanKey(region: Region): string {
    return region.lastScanAt ?? '';
  }

  async function load() {
    const targets = selectedRegion.value === 'all'
      ? regions.value
      : regions.value.filter(region => region.id === selectedRegion.value);
    // Do not retain responses for deleted regions indefinitely.
    for (const id of Object.keys(inventories.value)) {
      if (!regions.value.some(region => region.id === id)) {
        delete inventories.value[id];
        loadedScanAt.delete(id);
      }
    }

    const missing = targets.filter(region => loadedScanAt.get(region.id) !== scanKey(region));
    if (!missing.length) return;

    activeLoads += 1;
    loading.value = true;
    error.value = '';
    let retryForChangedRegion = false;
    try {
      const requests = missing.map((region) => {
        const existing = inFlight.get(region.id);
        if (existing) return existing;

        const request = $fetch<ApiSuccessResponse<RegionItems>>(`/api/regions/${region.id}/items`)
          .finally(() => {
            if (inFlight.get(region.id) === request) inFlight.delete(region.id);
          });
        inFlight.set(region.id, request);
        return request;
      });
      const results = await Promise.all(requests);
      const next = { ...inventories.value };
      for (const result of results) {
        const region = regions.value.find(candidate => candidate.id === result.data.region.id);
        if (!region) continue;
        next[region.id] = result.data;
        loadedScanAt.set(region.id, scanKey(result.data.region));
        if (scanKey(region) !== scanKey(result.data.region)) {
          loadedScanAt.delete(region.id);
          retryForChangedRegion = true;
        }
      }
      inventories.value = next;
    } catch {
      error.value = '在庫データを取得できませんでした。しばらくしてから再取得してください。';
    } finally {
      activeLoads -= 1;
      if (activeLoads === 0) loading.value = false;
    }

    // If an event changed a region while its request was in flight, fetch the
    // newest version once the shared request has settled.
    if (retryForChangedRegion) await load();
  }

  watch([selectedRegion, regions], load, { immediate: true, deep: true });
  const items = computed<InventoryItem[]>(() => {
    const totals = new Map<string, number>();
    const ids = selectedRegion.value === 'all' ? regions.value.map(region => region.id) : [selectedRegion.value];
    for (const id of ids) {
      for (const container of inventories.value[id]?.containers ?? []) {
        for (const item of container.items) totals.set(item.itemKey, (totals.get(item.itemKey) ?? 0) + item.amount);
      }
    }
    const needle = query.value.trim().toLocaleLowerCase('ja');
    return [...totals]
      .map(([itemKey, amount]) => ({ itemKey, amount }))
      .filter(item => !needle || item.itemKey.toLocaleLowerCase('ja').includes(needle) || displayName(item.itemKey).toLocaleLowerCase('ja').includes(needle))
      .sort((a, b) => {
        const result = sortKey.value === 'amount' ? a.amount - b.amount : a.itemKey.localeCompare(b.itemKey, 'ja');
        return sortDirection.value === 'asc' ? result : -result;
      });
  });
  return { inventories, loading, error, query, sortKey, sortDirection, view, items, load };
}

export function displayName(key: string) {
  const value = key.includes(':') ? key.split(':').slice(1).join(':') : key;
  return value.replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase());
}
