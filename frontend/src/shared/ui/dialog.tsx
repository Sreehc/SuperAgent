import { useEffect, useState } from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from '@phosphor-icons/react'
import { cn } from '@/lib/cn'
import { Button } from './button'
import { useRestoreFocusOnClose } from './use-restore-focus'

export const Dialog = DialogPrimitive.Root
export const DialogTrigger = DialogPrimitive.Trigger
export const DialogClose = DialogPrimitive.Close

function isPromiseLike(value: unknown): value is Promise<void> {
  return typeof value === 'object' && value !== null && 'then' in value && typeof value.then === 'function'
}

export function DialogContent({
  className,
  children,
  title,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & { title?: string }) {
  return (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="dialog-overlay" />
      <DialogPrimitive.Content className={cn('dialog-content surface-box', className)} {...props}>
        {title && (
          <div className="dialog-content__header">
            <DialogPrimitive.Title className="workspace-title">{title}</DialogPrimitive.Title>
            <DialogPrimitive.Close className="icon-button" aria-label="关闭">
              <X size={15} aria-hidden="true" />
            </DialogPrimitive.Close>
          </div>
        )}
        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  )
}

interface ConfirmDialogProps {
  open: boolean
  title: string
  description?: string
  error?: string | null
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  onConfirm: () => void | Promise<void>
  onOpenChange: (open: boolean) => void
}

interface PromptDialogProps {
  open: boolean
  title: string
  label: string
  description?: string
  error?: string | null
  defaultValue?: string
  placeholder?: string
  confirmLabel?: string
  cancelLabel?: string
  required?: boolean
  onConfirm: (value: string) => void | Promise<void>
  onOpenChange: (open: boolean) => void
}

export function ConfirmDialog({
  open,
  title,
  description,
  error,
  confirmLabel = '确认',
  cancelLabel = '取消',
  tone = 'danger',
  onConfirm,
  onOpenChange,
}: ConfirmDialogProps) {
  const [working, setWorking] = useState(false)
  useRestoreFocusOnClose(open)

  async function handleConfirm() {
    const result = onConfirm()
    if (!isPromiseLike(result)) {
      onOpenChange(false)
      return
    }

    setWorking(true)
    try {
      await result
      setWorking(false)
      onOpenChange(false)
    } catch {
      setWorking(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={title} className="dialog-content--narrow">
        <div className="dialog-body">
          {description && <DialogPrimitive.Description className="dialog-description">{description}</DialogPrimitive.Description>}
          {error && (
            <p className="error-banner" role="alert">
              {error}
            </p>
          )}
          <div className="dialog-footer">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              {cancelLabel}
            </Button>
            <Button type="button" variant={tone === 'danger' ? 'danger' : 'primary'} loading={working} onClick={handleConfirm}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

export function PromptDialog({
  open,
  title,
  label,
  description,
  error,
  defaultValue = '',
  placeholder,
  confirmLabel = '确认',
  cancelLabel = '取消',
  required = false,
  onConfirm,
  onOpenChange,
}: PromptDialogProps) {
  const [value, setValue] = useState(defaultValue)
  const [working, setWorking] = useState(false)
  const trimmed = value.trim()
  useRestoreFocusOnClose(open)

  useEffect(() => {
    if (open) setValue(defaultValue)
  }, [defaultValue, open])

  async function handleConfirm() {
    if (required && !trimmed) return
    const result = onConfirm(trimmed)
    if (!isPromiseLike(result)) {
      onOpenChange(false)
      return
    }

    setWorking(true)
    try {
      await result
      setWorking(false)
      onOpenChange(false)
    } catch {
      setWorking(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent title={title} className="dialog-content--narrow">
        <div className="dialog-body">
          {description && <DialogPrimitive.Description className="dialog-description">{description}</DialogPrimitive.Description>}
          {error && (
            <p className="error-banner" role="alert">
              {error}
            </p>
          )}
          <label className="field prompt-dialog__field">
            <span>{label}</span>
            <input value={value} placeholder={placeholder} autoFocus onChange={(event) => setValue(event.target.value)} />
          </label>
          <div className="dialog-footer">
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              {cancelLabel}
            </Button>
            <Button type="button" variant="primary" loading={working} disabled={required && !trimmed} onClick={handleConfirm}>
              {confirmLabel}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
