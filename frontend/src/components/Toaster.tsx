import { useEffect } from 'react'
import { CheckCircle, Info, WarningCircle, X } from '@phosphor-icons/react'
import { useToastStore } from '@/utils/toast'

const ICONS = {
  success: CheckCircle,
  error: WarningCircle,
  info: Info,
} as const

/** Renders the global toast queue. Mounted once at the app root. */
export function Toaster() {
  const items = useToastStore((state) => state.items)
  const dismiss = useToastStore((state) => state.dismiss)

  return (
    <div className="toaster" role="status" aria-live="polite">
      {items.map((item) => (
        <ToastRow key={item.id} id={item.id} kind={item.kind} message={item.message} duration={item.duration} onDismiss={dismiss} />
      ))}
    </div>
  )
}

function ToastRow({
  id,
  kind,
  message,
  duration,
  onDismiss,
}: {
  id: number
  kind: 'success' | 'error' | 'info'
  message: string
  duration: number
  onDismiss: (id: number) => void
}) {
  const Icon = ICONS[kind]
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(id), duration)
    return () => window.clearTimeout(timer)
  }, [id, duration, onDismiss])

  return (
    <div className={`toast toast--${kind}`}>
      <Icon size={16} weight="fill" aria-hidden="true" />
      <span>{message}</span>
      <button type="button" className="toast__close" aria-label="关闭通知" onClick={() => onDismiss(id)}>
        <X size={13} aria-hidden="true" />
      </button>
    </div>
  )
}
