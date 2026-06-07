<template>
  <div class="loading-state" :class="sizeClass" aria-busy="true">
    <div class="loading-state__rows" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
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
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.loading-state__rows {
  display: grid;
  gap: 8px;
}

.loading-state__rows span {
  display: block;
  height: 10px;
  border-radius: var(--radius-xs);
  background: linear-gradient(90deg, var(--color-surface-subtle), var(--color-border), var(--color-surface-subtle));
  background-size: 220% 100%;
  animation: loadingRows 1.2s ease-in-out infinite;
}

.loading-state__rows span:nth-child(1) {
  width: 72%;
}

.loading-state__rows span:nth-child(2) {
  width: 92%;
  animation-delay: 80ms;
}

.loading-state__rows span:nth-child(3) {
  width: 48%;
  animation-delay: 160ms;
}

.loading-state p {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.loading-state--small {
  padding: 12px;
}

.loading-state--large {
  padding: 28px;
}

@keyframes loadingRows {
  0% {
    background-position: 100% 0;
  }
  100% {
    background-position: -100% 0;
  }
}
</style>
