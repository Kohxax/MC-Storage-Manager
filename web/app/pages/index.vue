<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type { ApiSuccessResponse } from '#shared/types/api';

interface Player { id: string; displayName: string; minecraftUuid: string; permissions: string[] }
interface Region { id: string; ownerPlayerId: string; name: string; worldName: string; dimensionKey: string; status: 'active' | 'invalid' | 'deleted'; updatedAt: string; lastScanAt: string | null; revision: number }
interface RegionItems { region: Region; containers: Array<{ items: Array<{ itemKey: string; amount: number }> }> }

const theme = ref<'system' | 'light' | 'dark'>('system');
const selectedRegion = ref('all');
const query = ref('');
const sortKey = ref<'name' | 'amount'>('name');
const sortDirection = ref<'asc' | 'desc'>('asc');
const view = ref<'table' | 'grid'>('table');
const loadingItems = ref(false);
const actionError = ref('');
const inventories = ref<Record<string, RegionItems>>({});

const { data: meResponse, pending: sessionPending, error: sessionError } = await useFetch<ApiSuccessResponse<{ player: Player; csrfToken: string }>>('/api/me');
const { data: regionResponse, pending: regionsPending, error: regionsError, refresh: refreshRegions } = await useFetch<ApiSuccessResponse<{ regions: Region[] }>>('/api/regions');
const player = computed(() => meResponse.value?.data.player);
const csrfToken = computed(() => meResponse.value?.data.csrfToken ?? '');
const regions = computed(() => regionResponse.value?.data.regions ?? []);

async function loadInventories() {
  const targets = selectedRegion.value === 'all' ? regions.value : regions.value.filter(region => region.id === selectedRegion.value);
  const missing = targets.filter(region => !inventories.value[region.id]);
  if (!missing.length) return;
  loadingItems.value = true;
  actionError.value = '';
  try {
    const results = await Promise.all(missing.map(region => $fetch<ApiSuccessResponse<RegionItems>>(`/api/regions/${region.id}/items`)));
    const next = { ...inventories.value };
    for (const result of results) next[result.data.region.id] = result.data;
    inventories.value = next;
  } catch {
    actionError.value = '在庫データを取得できませんでした。しばらくしてから再試行してください。';
  } finally { loadingItems.value = false; }
}
watch([selectedRegion, regions], loadInventories, { immediate: true });

const items = computed(() => {
  const totals = new Map<string, number>();
  const ids = selectedRegion.value === 'all' ? regions.value.map(region => region.id) : [selectedRegion.value];
  for (const id of ids) for (const container of inventories.value[id]?.containers ?? []) for (const item of container.items) totals.set(item.itemKey, (totals.get(item.itemKey) ?? 0) + item.amount);
  const needle = query.value.trim().toLocaleLowerCase('ja');
  return [...totals].map(([itemKey, amount]) => ({ itemKey, amount }))
    .filter(item => !needle || item.itemKey.toLocaleLowerCase('ja').includes(needle) || displayName(item.itemKey).toLocaleLowerCase('ja').includes(needle))
    .sort((a, b) => { const result = sortKey.value === 'amount' ? a.amount - b.amount : a.itemKey.localeCompare(b.itemKey, 'ja'); return sortDirection.value === 'asc' ? result : -result; });
});
const latestUpdate = computed(() => (selectedRegion.value === 'all' ? regions.value : regions.value.filter(region => region.id === selectedRegion.value)).map(region => region.lastScanAt ?? region.updatedAt).filter(Boolean).sort().at(-1));

function displayName(key: string) { const value = key.includes(':') ? key.split(':').slice(1).join(':') : key; return value.replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()); }
function formatDate(value?: string | null) { return value ? new Intl.DateTimeFormat('ja-JP', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '未同期'; }
function canManage(region: Region) { const permissions = player.value?.permissions ?? []; return permissions.includes('storage.admin') || permissions.includes('storage.region.manage.any') || (region.ownerPlayerId === player.value?.id && permissions.includes('storage.region.manage.own')); }
function applyTheme(next: 'system' | 'light' | 'dark') { theme.value = next; if (typeof document === 'undefined') return; if (next === 'system') delete document.documentElement.dataset.theme; else document.documentElement.dataset.theme = next; localStorage.setItem('mcsm-theme', next); }
async function logout() { await $fetch('/api/auth/logout', { method: 'POST', headers: { 'x-csrf-token': csrfToken.value } }); location.reload(); }
async function renameRegion(region: Region) {
  const name = window.prompt('新しい範囲名', region.name)?.trim();
  if (!name || name === region.name) return;
  try { await $fetch(`/api/regions/${region.id}`, { method: 'PATCH', headers: { 'x-csrf-token': csrfToken.value }, body: { revision: region.revision, name } }); inventories.value = {}; await refreshRegions(); }
  catch { actionError.value = '範囲名を変更できませんでした。権限または更新競合を確認してください。'; }
}
async function deleteRegion(region: Region) {
  if (!window.confirm(`「${region.name}」を削除しますか？`)) return;
  try { await $fetch(`/api/regions/${region.id}`, { method: 'DELETE', headers: { 'x-csrf-token': csrfToken.value }, body: { revision: region.revision } }); selectedRegion.value = 'all'; inventories.value = {}; await refreshRegions(); }
  catch { actionError.value = '範囲を削除できませんでした。権限または更新競合を確認してください。'; }
}
onMounted(() => { const saved = localStorage.getItem('mcsm-theme'); if (saved === 'light' || saved === 'dark' || saved === 'system') applyTheme(saved); });
</script>

<template>
  <div class="min-h-screen bg-surface text-on-surface">
    <header class="sticky top-0 z-10 border-b border-outline/20 bg-surface/90 backdrop-blur"><div class="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6"><div class="flex items-center gap-3"><div class="grid size-10 place-items-center rounded-xl bg-primary font-bold text-on-primary">M</div><div><p class="font-semibold">MC Storage Manager</p><p class="text-xs text-on-surface-variant">ストレージ在庫管理</p></div></div><div class="flex items-center gap-2"><select :value="theme" class="md-field w-auto" aria-label="テーマ" @change="applyTheme(($event.target as HTMLSelectElement).value as 'system' | 'light' | 'dark')"><option value="system">システム</option><option value="light">ライト</option><option value="dark">ダーク</option></select><button v-if="player" class="md-button-tonal" type="button" @click="logout">ログアウト</button></div></div></header>
    <main class="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div v-if="sessionPending" class="md-card" role="status">セッションを確認しています…</div>
      <section v-else-if="sessionError || !player" class="mx-auto max-w-xl py-16 text-center"><div class="mx-auto grid size-16 place-items-center rounded-2xl bg-primary/15 text-2xl">⌘</div><h1 class="mt-6 text-3xl font-bold">Minecraftからログイン</h1><p class="mt-3 text-on-surface-variant">ゲーム内で <code>/storage web</code> を実行し、表示されたワンタイムURLを開いてください。</p></section>
      <template v-else>
        <div class="mb-8 flex flex-wrap items-end justify-between gap-4"><div><p class="text-sm text-on-surface-variant">{{ player.displayName }} さん</p><h1 class="mt-1 text-3xl font-bold">ストレージ</h1></div><p class="text-sm text-on-surface-variant">最終更新: {{ formatDate(latestUpdate) }}</p></div>
        <div v-if="regionsError" class="md-error" role="alert">範囲一覧を取得できませんでした。</div><div v-if="actionError" class="md-error mt-3" role="alert">{{ actionError }}</div>
        <div v-if="regionsPending" class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><div v-for="n in 3" :key="n" class="h-32 animate-pulse rounded-lg bg-surface-container" /></div>
        <section v-else-if="regions.length === 0" class="md-card mt-6 text-center"><h2 class="text-xl font-semibold">登録済みの範囲がありません</h2><p class="mt-2 text-on-surface-variant">WorldEditで選択後、ゲーム内で <code>/storage create &lt;名前&gt;</code> を実行してください。</p></section>
        <template v-else>
          <section class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="範囲一覧"><article v-for="region in regions" :key="region.id" class="md-card" :class="selectedRegion === region.id ? 'ring-2 ring-primary' : ''"><button class="w-full text-left" type="button" @click="selectedRegion = region.id"><div class="flex items-start justify-between gap-3"><h2 class="text-lg font-semibold">{{ region.name }}</h2><span class="status-chip" :class="region.status === 'invalid' ? 'text-error' : 'text-primary'">{{ region.status === 'invalid' ? '無効なワールド' : '同期済み' }}</span></div><p class="mt-2 text-sm text-on-surface-variant">{{ region.worldName }} · {{ region.dimensionKey }}</p><p class="mt-4 text-xs text-on-surface-variant">{{ formatDate(region.lastScanAt ?? region.updatedAt) }}</p></button><div v-if="canManage(region)" class="mt-4 flex gap-2 border-t border-outline/20 pt-3"><button class="md-button-tonal" type="button" @click="renameRegion(region)">名前変更</button><button class="md-button-tonal text-error" type="button" @click="deleteRegion(region)">削除</button></div></article></section>
          <section class="mt-8">
            <div class="flex flex-wrap gap-3"><select v-model="selectedRegion" class="md-field min-w-48"><option value="all">閲覧可能な全範囲</option><option v-for="region in regions" :key="region.id" :value="region.id">{{ region.name }}</option></select><input v-model="query" class="md-field min-w-56 flex-1" type="search" placeholder="アイテム名・キーで検索"><select v-model="sortKey" class="md-field w-auto"><option value="name">名前順</option><option value="amount">数量順</option></select><button class="md-button-tonal" type="button" @click="sortDirection = sortDirection === 'asc' ? 'desc' : 'asc'">{{ sortDirection === 'asc' ? '昇順' : '降順' }}</button><button class="md-button-tonal" type="button" @click="view = view === 'table' ? 'grid' : 'table'">{{ view === 'table' ? 'カード表示' : 'テーブル表示' }}</button></div>
            <div v-if="loadingItems" class="md-card mt-4" role="status">在庫を集計しています…</div><div v-else-if="items.length === 0" class="md-card mt-4 text-center text-on-surface-variant">{{ query ? '検索条件に一致するアイテムはありません。' : 'この範囲に在庫データはありません。' }}</div>
            <div v-else-if="view === 'grid'" class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><article v-for="item in items" :key="item.itemKey" class="md-card flex items-center gap-3"><MinecraftItemIcon :item-key="item.itemKey" /><div class="min-w-0"><h3 class="truncate font-semibold">{{ displayName(item.itemKey) }}</h3><p class="truncate text-xs text-on-surface-variant">{{ item.itemKey }}</p><p class="mt-1 text-lg font-bold">{{ item.amount.toLocaleString('ja-JP') }}</p></div></article></div>
            <div v-else class="mt-4 overflow-x-auto rounded-lg bg-surface-container shadow-md-elevation-1"><table class="w-full min-w-[34rem] text-left"><thead class="border-b border-outline/20 text-sm text-on-surface-variant"><tr><th class="px-5 py-3">アイテム</th><th class="px-5 py-3">名前空間キー</th><th class="px-5 py-3 text-right">数量</th></tr></thead><tbody><tr v-for="item in items" :key="item.itemKey" class="border-b border-outline/10 last:border-0"><td class="flex items-center gap-3 px-5 py-3 font-medium"><MinecraftItemIcon :item-key="item.itemKey" />{{ displayName(item.itemKey) }}</td><td class="px-5 py-3 text-sm text-on-surface-variant">{{ item.itemKey }}</td><td class="px-5 py-3 text-right font-semibold">{{ item.amount.toLocaleString('ja-JP') }}</td></tr></tbody></table></div>
          </section>
        </template>
      </template>
    </main>
  </div>
</template>
