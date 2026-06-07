<template>
  <Teleport to="body">
    <div class="toast-container" aria-live="polite" aria-relevant="additions">
      <TransitionGroup name="toast">
        <button
          v-for="toast in toasts"
          :key="toast.id"
          class="toast"
          :class="`toast--${toast.type}`"
          type="button"
          @click="remove(toast.id)"
        >
          <span class="toast__type">{{ typeLabel(toast.type) }}</span>
          <span class="toast__message">{{ toast.message }}</span>
        </button>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from 'vue'

export interface Toast {
  id: number
  type: 'success' | 'error' | 'info'
  message: string
}

const toasts = ref<Toast[]>([])
let nextId = 1

function show(type: Toast['type'], message: string, duration = 3000) {
  const id = nextId++
  toasts.value.push({ id, type, message })
  window.setTimeout(() => remove(id), duration)
  return id
}

function remove(id: number) {
  toasts.value = toasts.value.filter((toast) => toast.id !== id)
}

function typeLabel(type: Toast['type']) {
  const map: Record<Toast['type'], string> = {
    success: 'OK',
    error: 'ERR',
    info: 'INFO',
  }
  return map[type]
}

defineExpose({
  success: (message: string, duration?: number) => show('success', message, duration),
  error: (message: string, duration?: number) => show('error', message, duration),
  info: (message: string, duration?: number) => show('info', message, duration),
})
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 1000;
  display: grid;
  gap: 10px;
  width: min(420px, calc(100vw - 32px));
  pointer-events: none;
}

.toast {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  width: 100%;
  padding: 12px;
  color: var(--color-text);
  text-align: left;
  border: 1px solid var(--color-border);
  border-left-width: 4px;
  border-radius: var(--radius-md);
  background: var(--color-surface-raised);
  box-shadow: var(--shadow-popover);
  pointer-events: auto;
}

.toast--success {
  border-left-color: var(--color-success);
}

.toast--error {
  border-left-color: var(--color-danger);
}

.toast--info {
  border-left-color: var(--color-accent);
}

.toast__type {
  color: var(--color-text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 800;
}

.toast__message {
  line-height: 1.5;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity var(--duration-base) var(--ease-standard), transform var(--duration-base) var(--ease-standard);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
