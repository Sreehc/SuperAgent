<template>
  <div class="loading-state" :class="sizeClass" aria-busy="true">
    <div class="loading-state__rows" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
      <span v-if="size !== 'small'"></span>
    </div>
    <p v-if="text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: 'small' | 'medium' | 'large'
  text?: string
}

const props = withDefaults(defineProps<Props>(), {
  size: 'medium',
  text: '',
})

const sizeClass = computed(() => `loading-state--${props.size}`)
</script>

<style scoped>
.loading-state {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: color-mix(in srgb, var(--bg-surface), var(--bg-inset) 28%);
}

.loading-state__rows {
  display: grid;
  gap: 8px;
}

.loading-state__rows span {
  position: relative;
  display: block;
  height: 10px;
  overflow: hidden;
  border-radius: var(--radius-1);
  background: var(--bg-subtle);
}

.loading-state__rows span::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.36), transparent);
  content: "";
  animation: loadingRows 1.2s ease-in-out infinite;
}

.loading-state__rows span:nth-child(1) {
  width: 64%;
}

.loading-state__rows span:nth-child(2) {
  width: 92%;
  animation-delay: 80ms;
}

.loading-state__rows span:nth-child(3) {
  width: 48%;
  animation-delay: 160ms;
}

.loading-state__rows span:nth-child(4) {
  width: 76%;
  animation-delay: 220ms;
}

.loading-state p {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.loading-state--small {
  padding: 12px;
}

.loading-state--large {
  padding: 24px;
}

@keyframes loadingRows {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(100%);
  }
}
</style>
