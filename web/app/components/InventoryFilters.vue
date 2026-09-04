<script setup lang="ts">
import { computed } from 'vue';
import type { Region } from '#shared/types/storage';
const props = defineProps<{ regions: Region[]; selectedRegion: string; query: string; sortKey: 'name' | 'amount'; sortDirection: 'asc' | 'desc'; view: 'table' | 'grid' }>();
const emit = defineEmits<{ 'update:selectedRegion': [string]; 'update:query': [string]; 'update:sortKey': ['name' | 'amount']; 'update:sortDirection': ['asc' | 'desc']; 'update:view': ['table' | 'grid'] }>();
const regionItems = computed(() => [{ label: '閲覧可能な全範囲', value: 'all' }, ...props.regions.map(region => ({ label: region.name, value: region.id }))]);
const sortItems = [{ label: '名前順', value: 'name' }, { label: '数量順', value: 'amount' }];
</script>
<template><div class="flex flex-wrap gap-3"><USelect :model-value="selectedRegion" :items="regionItems" class="min-w-48" @update:model-value="emit('update:selectedRegion', $event as string)" /><UInput :model-value="query" class="min-w-56 flex-1" type="search" placeholder="アイテム名・キーで検索" @update:model-value="emit('update:query', $event)" /><USelect :model-value="sortKey" :items="sortItems" class="w-28" @update:model-value="emit('update:sortKey', $event as 'name' | 'amount')" /><UButton color="primary" variant="soft" @click="emit('update:sortDirection', sortDirection === 'asc' ? 'desc' : 'asc')">{{ sortDirection === 'asc' ? '昇順' : '降順' }}</UButton><UButton color="primary" variant="soft" @click="emit('update:view', view === 'table' ? 'grid' : 'table')">{{ view === 'table' ? 'カード表示' : 'テーブル表示' }}</UButton></div></template>
