import { useEffect, useId, type HTMLAttributes, type ReactNode } from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from '@phosphor-icons/react'
import { cn } from '@/lib/cn'
import { useRestoreFocusOnClose } from './use-restore-focus'

interface DetailInspectorProps extends HTMLAttributes<HTMLElement> {
  title: string
  description?: ReactNode
  meta?: ReactNode
  actions?: ReactNode
  children: ReactNode
}

interface DetailDrawerProps {
  open: boolean
  title: string
  description?: ReactNode
  side?: 'right' | 'bottom'
  footer?: ReactNode
  children: ReactNode
  className?: string
  onOpenChange: (open: boolean) => void
}

export function DetailInspector({
  id,
  title,
  description,
  meta,
  actions,
  children,
  className,
  'aria-labelledby': ariaLabelledBy,
  ...props
}: DetailInspectorProps) {
  const generatedId = useId()
  const headingId = ariaLabelledBy ?? `${id ?? generatedId}-title`

  return (
    <aside id={id} aria-labelledby={headingId} className={cn('detail-inspector inspector-box', className)} {...props}>
      <div className="detail-inspector__header">
        <div className="detail-inspector__title">
          <h2 id={headingId}>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        {meta && <div className="detail-inspector__meta">{meta}</div>}
        {actions && <div className="detail-inspector__actions">{actions}</div>}
      </div>
      <div className="detail-inspector__body">{children}</div>
    </aside>
  )
}

export function DetailDrawer({
  open,
  title,
  description,
  side = 'right',
  footer,
  children,
  className,
  onOpenChange,
}: DetailDrawerProps) {
  const descriptionId = useId()
  useRestoreFocusOnClose(open)

  useEffect(() => {
    if (!open) return undefined

    const { overflow, overscrollBehavior } = document.body.style
    document.body.style.overflow = 'hidden'
    document.body.style.overscrollBehavior = 'contain'

    return () => {
      document.body.style.overflow = overflow
      document.body.style.overscrollBehavior = overscrollBehavior
    }
  }, [open])

  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="drawer-overlay" />
        <DialogPrimitive.Content
          className={cn('detail-drawer', `detail-drawer--${side}`, className)}
          aria-describedby={description ? descriptionId : undefined}
        >
          <div className="detail-drawer__header">
            <div className="detail-drawer__title">
              <DialogPrimitive.Title className="detail-drawer__heading">{title}</DialogPrimitive.Title>
              {description && (
                <DialogPrimitive.Description id={descriptionId} className="detail-drawer__description">
                  {description}
                </DialogPrimitive.Description>
              )}
            </div>
            <DialogPrimitive.Close className="icon-button" aria-label={`关闭${title}`}>
              <X size={16} aria-hidden="true" />
            </DialogPrimitive.Close>
          </div>
          <div className="detail-drawer__body">{children}</div>
          {footer && <div className="detail-drawer__footer">{footer}</div>}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
