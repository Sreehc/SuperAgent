<template>
  <button class="theme-toggle icon-button" type="button" :aria-label="label" :title="label" @click="toggleTheme">
    <PhMoon v-if="theme === 'light'" :size="17" weight="regular" aria-hidden="true" />
    <PhSun v-else :size="17" weight="regular" aria-hidden="true" />
  </button>
</template>

<script setup lang="ts">
import { PhMoon, PhSun } from '@phosphor-icons/vue'
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
  flex: 0 0 auto;
}
</style>
