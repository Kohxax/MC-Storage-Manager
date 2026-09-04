<script setup lang="ts">
import { computed, ref } from 'vue';
import type { ApiSuccessResponse } from '#shared/types/api';
import type { Player, Region } from '#shared/types/storage';
import { useInventory } from '~/composables/useInventory';
import { useTheme } from '~/composables/useTheme';

const selectedRegion = ref('all');
const actionError = ref('');
const editingRegion = ref<Region>();
const editOpen = ref(false);
const editName = ref('');
const deleteRegion = ref<Region>();
const deleteOpen = ref(false);
const { theme, applyTheme } = useTheme();
const { data: meResponse, pending: sessionPending, error: sessionError } = await useFetch<ApiSuccessResponse<{ player: Player; csrfToken: string }>>('/api/me');
const { data: regionResponse, pending: regionsPending, error: regionsError, refresh: refreshRegions } = await useFetch<ApiSuccessResponse<{ regions: Region[] }>>('/api/regions');
const player = computed(() => meResponse.value?.data.player);
const csrfToken = computed(() => meResponse.value?.data.csrfToken ?? '');
const regions = computed(() => regionResponse.value?.data.regions ?? []);
const { inventories, loading: inventoryLoading, error: inventoryError, query, sortKey, sortDirection, view, items } = useInventory(regions, selectedRegion);
const latestUpdate = computed(() => (selectedRegion.value === 'all' ? regions.value : regions.value.filter(region => region.id === selectedRegion.value)).map(region => region.lastScanAt ?? region.updatedAt).filter(Boolean).sort().at(-1));

function formatDate(value?: string | null) { return value ? new Intl.DateTimeFormat('ja-JP', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '未同期'; }
function canManage(region: Region) { const permissions = player.value?.permissions ?? []; return permissions.includes('storage.admin') || permissions.includes('storage.region.manage.any') || (region.ownerPlayerId === player.value?.id && permissions.includes('storage.region.manage.own')); }
function openRename(region: Region) { editingRegion.value = region; editName.value = region.name; editOpen.value = true; }
function openDelete(region: Region) { deleteRegion.value = region; deleteOpen.value = true; }
async function renameRegion() { const region = editingRegion.value; const name = editName.value.trim(); if (!region || !name || name === region.name) { editOpen.value = false; return; } try { await $fetch(`/api/regions/${region.id}`, { method: 'PATCH', headers: { 'x-csrf-token': csrfToken.value }, body: { revision: region.revision, name } }); inventories.value = {}; editOpen.value = false; await refreshRegions(); } catch { actionError.value = '範囲名を変更できませんでした。権限または更新競合を確認してください。'; } }
async function removeRegion() { const region = deleteRegion.value; if (!region) return; try { await $fetch(`/api/regions/${region.id}`, { method: 'DELETE', headers: { 'x-csrf-token': csrfToken.value }, body: { revision: region.revision } }); selectedRegion.value = 'all'; inventories.value = {}; deleteOpen.value = false; await refreshRegions(); } catch { actionError.value = '範囲を削除できませんでした。権限または更新競合を確認してください。'; } }
async function logout() { await $fetch('/api/auth/logout', { method: 'POST', headers: { 'x-csrf-token': csrfToken.value } }); location.reload(); }
</script>
<template><div class="min-h-screen bg-surface text-on-surface"><AppHeader :player="player" :theme="theme" @update:theme="applyTheme" @logout="logout" /><main class="mx-auto max-w-7xl px-4 py-8 sm:px-6"><UCard v-if="sessionPending" role="status">セッションを確認しています…</UCard><section v-else-if="sessionError || !player" class="mx-auto max-w-xl py-16 text-center"><div class="mx-auto grid size-16 place-items-center rounded-2xl bg-primary/15 text-2xl text-primary">⌘</div><h1 class="mt-6 text-3xl font-bold">Minecraftからログイン</h1><p class="mt-3 text-on-surface-variant">ゲーム内で <code>/storage web</code> を実行し、表示されたワンタイムURLを開いてください。</p></section><template v-else><div class="mb-8 flex flex-wrap items-end justify-between gap-4"><div><p class="text-sm text-on-surface-variant">{{ player.displayName }} さん</p><h1 class="mt-1 text-3xl font-bold">ストレージ</h1></div><p class="text-sm text-on-surface-variant">最終更新: {{ formatDate(latestUpdate) }}</p></div><UAlert v-if="regionsError" color="error" title="範囲一覧を取得できませんでした。" /><UAlert v-if="actionError || inventoryError" class="mt-3" color="error" :title="actionError || inventoryError" /><div v-if="regionsPending" class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3"><USkeleton v-for="n in 3" :key="n" class="h-32" /></div><UCard v-else-if="regions.length === 0" class="mt-6 text-center"><h2 class="text-xl font-semibold">登録済みの範囲がありません</h2><p class="mt-2 text-on-surface-variant">WorldEditで選択後、ゲーム内で <code>/storage create &lt;名前&gt;</code> を実行してください。</p></UCard><template v-else><section class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label="範囲一覧"><RegionCard v-for="region in regions" :key="region.id" :region="region" :selected="selectedRegion === region.id" :manageable="canManage(region)" :updated-label="formatDate(region.lastScanAt ?? region.updatedAt)" @select="selectedRegion = region.id" @rename="openRename" @remove="openDelete" /></section><section class="mt-8"><InventoryFilters v-model:selected-region="selectedRegion" v-model:query="query" v-model:sort-key="sortKey" v-model:sort-direction="sortDirection" v-model:view="view" :regions="regions" /><UCard v-if="inventoryLoading" class="mt-4" role="status">在庫を集計しています…</UCard><UCard v-else-if="items.length === 0" class="mt-4 text-center text-on-surface-variant">{{ query ? '検索条件に一致するアイテムはありません。' : 'この範囲に在庫データはありません。' }}</UCard><InventoryList v-else class="mt-4" :items="items" :view="view" /></section></template></template></main><UModal v-model:open="editOpen" title="範囲名を変更"><template #body><UInput v-model="editName" label="範囲名" autofocus @keyup.enter="renameRegion" /></template><template #footer><UButton color="neutral" variant="ghost" @click="editOpen = false">キャンセル</UButton><UButton @click="renameRegion">保存</UButton></template></UModal><UModal v-model:open="deleteOpen" title="範囲を削除"><template #body>「{{ deleteRegion?.name }}」を削除しますか？ この操作は取り消せません。</template><template #footer><UButton color="neutral" variant="ghost" @click="deleteOpen = false">キャンセル</UButton><UButton color="error" @click="removeRegion">削除</UButton></template></UModal></div></template>
