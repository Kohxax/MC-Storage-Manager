import { onMounted, ref } from 'vue';
export type Theme = 'system' | 'light' | 'dark';
export function useTheme() {
  const theme = ref<Theme>('system');
  function applyTheme(next: Theme) {
    theme.value = next;
    if (import.meta.server) return;
    if (next === 'system') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = next;
    localStorage.setItem('mcsm-theme', next);
  }
  onMounted(() => { const saved = localStorage.getItem('mcsm-theme'); if (saved === 'system' || saved === 'light' || saved === 'dark') applyTheme(saved); });
  return { theme, applyTheme };
}
