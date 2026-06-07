type ToastInstance = {
  success: (message: string, duration?: number) => number
  error: (message: string, duration?: number) => number
  info: (message: string, duration?: number) => number
}

let toastInstance: ToastInstance | null = null

export function setToastInstance(instance: ToastInstance) {
  toastInstance = instance
}

export const toast = {
  success(message: string, duration?: number) {
    toastInstance?.success(message, duration)
  },
  error(message: string, duration?: number) {
    toastInstance?.error(message, duration)
  },
  info(message: string, duration?: number) {
    toastInstance?.info(message, duration)
  },
}
