<script setup lang="ts">
import { ref } from 'vue';
import type { ApiErrorResponse } from '#shared/types/api';

const route = useRoute();
const token = typeof route.query.token === 'string' ? route.query.token : '';
const status = ref<'ready' | 'loading' | 'error'>(token ? 'ready' : 'error');
const message = ref(token ? 'Minecraftアカウントでログインします。' : 'ログイントークンがありません。');

interface RedeemFetchError {
  status?: number;
  statusCode?: number;
  data?: ApiErrorResponse;
  response?: {
    status?: number;
    _data?: ApiErrorResponse;
    headers?: { get(name: string): string | null };
  };
}

function redeemErrorMessage(error: unknown): string {
  const fetchError = error as RedeemFetchError;
  const response = fetchError.response;
  const statusCode = fetchError.statusCode ?? fetchError.status ?? response?.status;
  const data = fetchError.data ?? response?._data;
  const responseRequestId = data?.requestId ?? response?.headers?.get('x-request-id');
  const requestId = responseRequestId ? ` 問い合わせID: ${responseRequestId}` : '';
  if (statusCode === 400) return `ログインURLが正しくありません。ゲーム内で新しいURLを発行してください。${requestId}`;
  if (statusCode === 401) return `URLが期限切れか、すでに使用されています。ゲーム内で新しいURLを発行してください。${requestId}`;
  if (statusCode === 403) return `Webログイン権限がありません。ゲーム内の権限を確認してください。${requestId}`;
  if (statusCode === 429) return `試行回数が多すぎます。1分ほど待ってから新しいURLで再試行してください。${requestId}`;
  return `ログイン処理に失敗しました。しばらく待ってから再試行してください。${requestId}`;
}

async function redeem() {
  if (!token || status.value === 'loading') return;
  status.value = 'loading';
  message.value = 'ログインしています…';
  try {
    await $fetch('/api/auth/redeem', { method: 'POST', body: { token } });
  } catch (error) {
    status.value = 'error';
    message.value = redeemErrorMessage(error);
    return;
  }
  await navigateTo('/', { replace: true });
}
</script>
<template><main class="grid min-h-screen place-items-center bg-surface px-4 text-on-surface"><UCard class="max-w-lg text-center"><div class="mx-auto grid size-14 place-items-center rounded-2xl bg-primary/15 text-xl text-primary">M</div><h1 class="mt-5 text-2xl font-bold">Minecraftアカウントでログイン</h1><p class="mt-3 text-on-surface-variant" :class="status === 'error' ? 'text-error' : ''">{{ message }}</p><UButton v-if="status === 'ready'" class="mt-6" icon="i-lucide-log-in" @click="redeem">ログインする</UButton><UButton v-else-if="status === 'loading'" class="mt-6" loading disabled>ログイン中</UButton><UButton v-else class="mt-6" to="/">トップへ戻る</UButton></UCard></main></template>
