<template>
  <button class="theme-toggle" type="button" :aria-label="label" :title="label" @click="toggleTheme">
    <span class="theme-toggle__icon" aria-hidden="true">{{ theme === 'light' ? 'D' : 'L' }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const theme = ref<'light' | 'dark'>('light')
const label = computed(() => (theme.value === 'light' ? '切换到深色主题' : '切换到浅色主题'))

function applyTheme(nextTheme: 'light' | 'dark') {
  theme.value = nextTheme
  document.documentElement.setAttribute('data-theme', nextTheme)
  localStorage.setItem('theme', nextTheme)
}

function toggleTheme() {
  applyTheme(theme.value === 'light' ? 'dark' : 'light')
}

onMounted(() => {
  const saved = localStorage.getItem('theme') as 'light' | 'dark' | null
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  applyTheme(saved || (prefersDark ? 'dark' : 'light'))
})
</script>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface);
  color: var(--color-text);
  transition: background-color var(--duration-fast) var(--ease-standard), border-color var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.theme-toggle:hover {
  background: var(--color-surface-subtle);
  border-color: var(--color-border-strong);
}

.theme-toggle:active {
  transform: translateY(1px);
}

.theme-toggle__icon {
  font-size: 15px;
  line-height: 1;
}
</style>
