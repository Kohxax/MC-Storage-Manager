<script setup lang="ts">
import { onMounted, ref } from 'vue';
const route = useRoute();
const status = ref<'loading' | 'error'>('loading');
const message = ref('ログイントークンを確認しています…');
onMounted(async () => { const token = typeof route.query.token === 'string' ? route.query.token : ''; if (!token) { status.value = 'error'; message.value = 'ログイントークンがありません。'; return; } try { await $fetch('/api/auth/redeem', { method: 'POST', body: { token } }); await navigateTo('/', { replace: true }); } catch { status.value = 'error'; message.value = 'URLが期限切れか、すでに使用されています。ゲーム内で新しいURLを発行してください。'; } });
</script>
<template><main class="grid min-h-screen place-items-center bg-surface px-4 text-on-surface"><UCard class="max-w-lg text-center"><div class="mx-auto grid size-14 place-items-center rounded-2xl bg-primary/15 text-xl text-primary">M</div><h1 class="mt-5 text-2xl font-bold">Minecraftアカウントでログイン</h1><p class="mt-3 text-on-surface-variant" :class="status === 'error' ? 'text-error' : ''">{{ message }}</p><UButton v-if="status === 'error'" class="mt-6" to="/">トップへ戻る</UButton></UCard></main></template>
