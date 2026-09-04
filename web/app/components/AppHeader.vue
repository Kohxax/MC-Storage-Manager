<script setup lang="ts">
import type { Theme } from '~/composables/useTheme';
defineProps<{ player?: { displayName: string }; theme: Theme }>();
defineEmits<{ 'update:theme': [Theme]; logout: [] }>();
const themeItems = [{ label: 'システム', value: 'system' }, { label: 'ライト', value: 'light' }, { label: 'ダーク', value: 'dark' }];
</script>
<template><header class="sticky top-0 z-10 border-b border-outline/20 bg-surface/90 backdrop-blur"><div class="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6"><div class="flex items-center gap-3"><div class="grid size-10 place-items-center rounded-xl bg-primary font-bold text-on-primary">M</div><div><p class="font-semibold">MC Storage Manager</p><p class="text-xs text-on-surface-variant">ストレージ在庫管理</p></div></div><div class="flex items-center gap-2"><USelect :model-value="theme" :items="themeItems" aria-label="テーマ" class="w-28" @update:model-value="$emit('update:theme', $event as Theme)" /><UButton v-if="player" color="primary" variant="soft" @click="$emit('logout')">ログアウト</UButton></div></div></header></template>
