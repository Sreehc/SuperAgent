<template>
  <div class="skeleton-card" :class="variantClass" aria-hidden="true">
    <div v-if="variant !== 'compact'" class="skeleton-card__header">
      <span class="skeleton-dot"></span>
      <div class="skeleton-stack">
        <span class="skeleton-line skeleton-line--short"></span>
        <span class="skeleton-line skeleton-line--tiny"></span>
      </div>
    </div>
    <div class="skeleton-stack">
      <span class="skeleton-line"></span>
      <span class="skeleton-line"></span>
      <span class="skeleton-line skeleton-line--medium"></span>
    </div>
    <div v-if="variant === 'chat'" class="skeleton-card__chips">
      <span></span>
      <span></span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  variant?: 'default' | 'chat' | 'list' | 'compact'
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
})

const variantClass = computed(() => `skeleton-card--${props.variant}`)
</script>

<style scoped>
.skeleton-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.skeleton-card--compact {
  padding: 10px;
}

.skeleton-card__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.skeleton-stack {
  display: grid;
  gap: 8px;
}

.skeleton-dot,
.skeleton-line,
.skeleton-card__chips span {
  position: relative;
  overflow: hidden;
  background: var(--color-surface-subtle);
}

.skeleton-dot::after,
.skeleton-line::after,
.skeleton-card__chips span::after {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.36), transparent);
  content: "";
  animation: skeletonShimmer 1.4s ease-in-out infinite;
}

.skeleton-dot {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
}

.skeleton-line {
  width: 100%;
  height: 10px;
  border-radius: var(--radius-xs);
}

.skeleton-line--medium {
  width: 68%;
}

.skeleton-line--short {
  width: 44%;
}

.skeleton-line--tiny {
  width: 26%;
  height: 8px;
}

.skeleton-card__chips {
  display: flex;
  gap: 8px;
}

.skeleton-card__chips span {
  width: 76px;
  height: 24px;
  border-radius: var(--radius-sm);
}

.skeleton-card--list .skeleton-stack {
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.7fr);
}

@keyframes skeletonShimmer {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(100%);
  }
}
</style>
