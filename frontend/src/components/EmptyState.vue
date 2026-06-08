<template>
  <div class="empty-state" :class="`empty-state--${variant}`">
    <div class="empty-state__mark" aria-hidden="true">
      <component :is="iconComponent" :size="28" weight="duotone" />
    </div>
    <div class="empty-state__copy">
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </div>
    <slot name="action"></slot>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PhBooks, PhChatCenteredText, PhDatabase, PhMagnifyingGlass } from '@phosphor-icons/vue'

interface Props {
  variant?: 'chat' | 'knowledge' | 'search' | 'default'
  title: string
  description: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
})

const iconComponent = computed(() => {
  const map = {
    chat: PhChatCenteredText,
    knowledge: PhBooks,
    search: PhMagnifyingGlass,
    default: PhDatabase,
  }
  return map[props.variant]
})
</script>

<style scoped>
.empty-state {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 34px 20px;
  color: var(--text-muted);
  text-align: center;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-2);
  background:
    linear-gradient(135deg, rgba(47, 111, 94, 0.08), transparent 48%),
    color-mix(in srgb, var(--bg-inset), var(--bg-surface) 34%);
}

.empty-state__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border: 1px solid color-mix(in srgb, var(--accent), transparent 58%);
  border-radius: var(--radius-2);
  color: var(--accent);
  background: var(--accent-soft);
}

.empty-state__copy {
  display: grid;
  gap: 6px;
}

.empty-state h3 {
  margin: 0;
  color: var(--text-main);
  font-size: 16px;
}

.empty-state p {
  max-width: 52ch;
  margin: 0;
  line-height: 1.6;
}
</style>
