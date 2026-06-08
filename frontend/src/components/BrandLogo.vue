<template>
  <div class="product-mark" :class="sizeClass" aria-label="SuperAgent">
    <span class="product-mark__glyph" aria-hidden="true">
      <span></span>
      <span></span>
      <span></span>
    </span>
    <span v-if="showText" class="product-mark__copy">
      <strong>SuperAgent</strong>
      <small>Control Tower</small>
    </span>
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

const sizeClass = computed(() => [`product-mark--${props.size}`, { 'product-mark--animated': props.animated }])
</script>

<style scoped>
.product-mark {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--text-main);
}

.product-mark__glyph {
  position: relative;
  display: inline-grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: 3px;
  flex: 0 0 auto;
  padding: 7px;
  border: 1px solid color-mix(in srgb, var(--accent), transparent 35%);
  border-radius: var(--radius-2);
  background:
    linear-gradient(135deg, rgba(123, 210, 179, 0.28), transparent 48%),
    #101417;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
}

.product-mark__glyph span {
  display: block;
  border-radius: 2px;
  background: #7bd2b3;
}

.product-mark__glyph span:nth-child(1) {
  opacity: 0.92;
}

.product-mark__glyph span:nth-child(2) {
  opacity: 0.52;
}

.product-mark__glyph span:nth-child(3) {
  grid-column: 1 / -1;
  opacity: 0.74;
}

.product-mark__copy {
  display: grid;
  gap: 1px;
  min-width: 0;
  line-height: 1.05;
}

.product-mark__copy strong {
  overflow: hidden;
  color: currentColor;
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 780;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-mark__copy small {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 720;
  letter-spacing: 0;
}

.product-mark--small .product-mark__glyph {
  width: 30px;
  height: 30px;
  padding: 6px;
}

.product-mark--medium .product-mark__glyph {
  width: 38px;
  height: 38px;
}

.product-mark--large .product-mark__glyph {
  width: 52px;
  height: 52px;
  gap: 4px;
  padding: 9px;
}

.product-mark--large .product-mark__copy strong {
  font-size: 20px;
}

.product-mark--large .product-mark__copy small {
  font-size: 11px;
}

.product-mark--animated .product-mark__glyph {
  animation: markPulse 2.4s var(--ease-standard) infinite;
}

@keyframes markPulse {
  0%, 100% {
    box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08), 0 0 0 0 rgba(123, 210, 179, 0.18);
  }
  50% {
    box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08), 0 0 0 7px transparent;
  }
}
</style>
