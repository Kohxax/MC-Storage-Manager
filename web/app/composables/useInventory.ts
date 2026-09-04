import { computed, ref, watch, type Ref } from 'vue';
import type { ApiSuccessResponse } from '#shared/types/api';
import type { InventoryItem, Region, RegionItems } from '#shared/types/storage';
export function useInventory(regions: Ref<Region[]>, selectedRegion: Ref<string>) {
  const inventories = ref<Record<string, RegionItems>>({}); const loading = ref(false); const error = ref('');
  const query = ref(''); const sortKey = ref<'name' | 'amount'>('name'); const sortDirection = ref<'asc' | 'desc'>('asc'); const view = ref<'table' | 'grid'>('table');
  async function load() {
    const targets = selectedRegion.value === 'all' ? regions.value : regions.value.filter(region => region.id === selectedRegion.value);
    const missing = targets.filter(region => !inventories.value[region.id]); if (!missing.length) return;
    loading.value = true; error.value = '';
    try { const results = await Promise.all(missing.map(region => $fetch<ApiSuccessResponse<RegionItems>>(`/api/regions/${region.id}/items`))); inventories.value = Object.fromEntries([...Object.entries(inventories.value), ...results.map(result => [result.data.region.id, result.data])]); }
    catch { error.value = '在庫データを取得できませんでした。しばらくしてから再試行してください。'; } finally { loading.value = false; }
  }
  watch([selectedRegion, regions], load, { immediate: true });
  const items = computed<InventoryItem[]>(() => {
    const totals = new Map<string, number>(); const ids = selectedRegion.value === 'all' ? regions.value.map(region => region.id) : [selectedRegion.value];
    for (const id of ids) for (const container of inventories.value[id]?.containers ?? []) for (const item of container.items) totals.set(item.itemKey, (totals.get(item.itemKey) ?? 0) + item.amount);
    const needle = query.value.trim().toLocaleLowerCase('ja');
    return [...totals].map(([itemKey, amount]) => ({ itemKey, amount })).filter(item => !needle || item.itemKey.toLocaleLowerCase('ja').includes(needle) || displayName(item.itemKey).toLocaleLowerCase('ja').includes(needle)).sort((a, b) => { const result = sortKey.value === 'amount' ? a.amount - b.amount : a.itemKey.localeCompare(b.itemKey, 'ja'); return sortDirection.value === 'asc' ? result : -result; });
  });
  return { inventories, loading, error, query, sortKey, sortDirection, view, items, load };
}
export function displayName(key: string) { const value = key.includes(':') ? key.split(':').slice(1).join(':') : key; return value.replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()); }
