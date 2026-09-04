import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
export type Theme = 'system' | 'light' | 'dark';
export function useTheme() {
  const theme = ref<Theme>('system');
  const systemPrefersDark = ref(false);
  let mediaQuery: MediaQueryList | undefined;

  const isDark = computed(() => theme.value === 'dark' || (theme.value === 'system' && systemPrefersDark.value));

  function applyTheme(next: Theme) {
    theme.value = next;
    if (import.meta.server) return;
    if (next === 'system') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = next;
    localStorage.setItem('mcsm-theme', next);
  }

  function updateSystemPreference(event?: MediaQueryListEvent) {
    systemPrefersDark.value = event?.matches ?? mediaQuery?.matches ?? false;
  }

  onMounted(() => {
    mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    updateSystemPreference();
    mediaQuery.addEventListener('change', updateSystemPreference);
    const saved = localStorage.getItem('mcsm-theme');
    if (saved === 'system' || saved === 'light' || saved === 'dark') applyTheme(saved);
  });
  onBeforeUnmount(() => mediaQuery?.removeEventListener('change', updateSystemPreference));

  return { theme, isDark, applyTheme };
}
