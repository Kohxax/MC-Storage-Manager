<script setup lang="ts">
import { computed, ref, watch } from 'vue';
const props = defineProps<{ itemKey: string }>();
const stage = ref(0);
watch(() => props.itemKey, () => { stage.value = 0; });
const path = computed(() => props.itemKey.split(':').at(-1)?.replace(/[^a-z0-9_./-]/g, '') ?? '');
const source = computed(() => stage.value === 0
  ? `https://assets.mcasset.cloud/26.2/assets/minecraft/textures/item/${path.value}.png`
  : `https://assets.mcasset.cloud/26.2/assets/minecraft/textures/block/${path.value}.png`);
const initials = computed(() => path.value.replaceAll('_', ' ').slice(0, 2).toUpperCase());
function fallback() { stage.value += 1; }
</script>

<template>
  <span class="item-icon" aria-hidden="true">
    <img v-if="stage < 2 && path" :src="source" alt="" class="size-8 object-contain [image-rendering:pixelated]" loading="lazy" @error="fallback">
    <span v-else>{{ initials }}</span>
  </span>
</template>
