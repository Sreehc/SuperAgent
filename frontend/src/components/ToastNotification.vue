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
          <span class="toast__icon" aria-hidden="true">
            <PhCheckCircle v-if="toast.type === 'success'" :size="18" weight="duotone" />
            <PhWarningCircle v-else-if="toast.type === 'error'" :size="18" weight="duotone" />
            <PhInfo v-else :size="18" weight="duotone" />
          </span>
          <span class="toast__message">{{ toast.message }}</span>
        </button>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { PhCheckCircle, PhInfo, PhWarningCircle } from '@phosphor-icons/vue'
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

defineExpose({
  success: (message: string, duration?: number) => show('success', message, duration),
  error: (message: string, duration?: number) => show('error', message, duration),
  info: (message: string, duration?: number) => show('info', message, duration),
})
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 14px;
  right: 14px;
  z-index: 1000;
  display: grid;
  gap: 8px;
  width: min(420px, calc(100vw - 28px));
  pointer-events: none;
}

.toast {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  width: 100%;
  padding: 11px 12px;
  color: var(--text-main);
  text-align: left;
  border: 1px solid var(--line-soft);
  border-left-width: 3px;
  border-radius: var(--radius-2);
  background: var(--bg-lift);
  box-shadow: var(--shadow-menu);
  pointer-events: auto;
}

.toast--success {
  border-left-color: var(--success);
}

.toast--error {
  border-left-color: var(--danger);
}

.toast--info {
  border-left-color: var(--accent);
}

.toast__icon {
  display: inline-flex;
  color: var(--text-muted);
}

.toast--success .toast__icon {
  color: var(--success);
}

.toast--error .toast__icon {
  color: var(--danger);
}

.toast--info .toast__icon {
  color: var(--accent);
}

.toast__message {
  line-height: 1.45;
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
