<script setup lang="ts">
import type { Region } from '#shared/types/storage';
defineProps<{ region: Region; selected: boolean; manageable: boolean; updatedLabel: string }>();
defineEmits<{ select: []; rename: [Region]; remove: [Region] }>();
</script>
<template><UCard :class="selected ? 'ring-2 ring-primary' : ''"><button class="w-full text-left" type="button" @click="$emit('select')"><div class="flex items-start justify-between gap-3"><h2 class="text-lg font-semibold">{{ region.name }}</h2><UBadge :color="region.status === 'invalid' ? 'error' : 'primary'" variant="subtle">{{ region.status === 'invalid' ? '無効なワールド' : '同期済み' }}</UBadge></div><p class="mt-2 text-sm text-on-surface-variant">{{ region.worldName }} · {{ region.dimensionKey }}</p><p class="mt-4 text-xs text-on-surface-variant">{{ updatedLabel }}</p></button><div v-if="manageable" class="mt-4 flex gap-2 border-t border-outline/20 pt-3"><UButton size="sm" color="primary" variant="soft" @click="$emit('rename', region)">名前変更</UButton><UButton size="sm" color="error" variant="soft" @click="$emit('remove', region)">削除</UButton></div></UCard></template>
