<template>
  <div class="empty-state" :class="`empty-state--${variant}`">
    <div class="empty-state__mark" aria-hidden="true">{{ mark }}</div>
    <div class="empty-state__copy">
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </div>
    <slot name="action"></slot>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  variant?: 'chat' | 'knowledge' | 'search' | 'default'
  title: string
  description: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
})

const mark = computed(() => {
  const map: Record<NonNullable<Props['variant']>, string> = {
    chat: 'MSG',
    knowledge: 'KB',
    search: 'FIND',
    default: 'EMPTY',
  }
  return map[props.variant]
})
</script>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 36px 20px;
  color: var(--color-text-muted);
  text-align: center;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.empty-state__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 48px;
  height: 30px;
  padding: 0 9px;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-xs);
  color: var(--color-accent);
  background: var(--color-accent-soft);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 800;
}

.empty-state__copy {
  display: grid;
  gap: 6px;
}

.empty-state h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 17px;
}

.empty-state p {
  max-width: 52ch;
  margin: 0;
  line-height: 1.6;
}
</style>
