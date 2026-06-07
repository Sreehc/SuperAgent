<template>
  <div class="brand-logo" :class="sizeClass" aria-label="SuperAgent">
    <span class="brand-logo__mark">SA</span>
    <span v-if="showText" class="brand-logo__text">SuperAgent</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  size?: 'small' | 'medium' | 'large'
  animated?: boolean
  showText?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  size: 'medium',
  animated: false,
  showText: false,
})

const sizeClass = computed(() => [`brand-logo--${props.size}`, { 'brand-logo--animated': props.animated }])
</script>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
}

.brand-logo__mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-text);
  color: var(--color-bg);
  font-family: var(--font-mono);
  font-weight: 800;
  letter-spacing: -0.04em;
}

.brand-logo__text {
  font-weight: 760;
  letter-spacing: -0.02em;
}

.brand-logo--small .brand-logo__mark {
  width: 28px;
  height: 28px;
  font-size: 11px;
}

.brand-logo--medium .brand-logo__mark {
  width: 36px;
  height: 36px;
  font-size: 13px;
}

.brand-logo--large .brand-logo__mark {
  width: 48px;
  height: 48px;
  font-size: 16px;
}

.brand-logo--large .brand-logo__text {
  font-size: 20px;
}

.brand-logo--animated .brand-logo__mark {
  animation: markPulse 2.4s var(--ease-standard) infinite;
}

@keyframes markPulse {
  0%, 100% {
    box-shadow: 0 0 0 0 var(--color-accent-soft);
  }
  50% {
    box-shadow: 0 0 0 6px transparent;
  }
}
</style>
