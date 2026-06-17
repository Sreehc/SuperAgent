import { create } from 'zustand'

export type ToastKind = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  kind: ToastKind
  message: string
  duration: number
}

interface ToastStore {
  items: ToastItem[]
  push: (kind: ToastKind, message: string, duration?: number) => number
  dismiss: (id: number) => void
}

let seq = 0

export const useToastStore = create<ToastStore>((set) => ({
  items: [],
  push(kind, message, duration = 3200) {
    const id = ++seq
    set((state) => ({ items: [...state.items, { id, kind, message, duration }] }))
    return id
  },
  dismiss(id) {
    set((state) => ({ items: state.items.filter((item) => item.id !== id) }))
  },
}))

/** Global, component-free toast API (parity with the previous toast helper). */
export const toast = {
  success: (message: string, duration?: number) => useToastStore.getState().push('success', message, duration),
  error: (message: string, duration?: number) => useToastStore.getState().push('error', message, duration),
  info: (message: string, duration?: number) => useToastStore.getState().push('info', message, duration),
}
