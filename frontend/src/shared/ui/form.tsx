import { cloneElement, isValidElement } from 'react'
import type { AriaAttributes, ReactElement, ReactNode } from 'react'
import { cn } from '@/lib/cn'

type FieldControlProps = {
  id?: string
  'aria-describedby'?: string
  'aria-invalid'?: AriaAttributes['aria-invalid']
}

interface FormFieldProps {
  label: string
  htmlFor: string
  hint?: string
  error?: string
  errorTestId?: string
  className?: string
  children: ReactElement<FieldControlProps>
}

interface SaveBarProps {
  dirty?: boolean
  saving?: boolean
  saved?: boolean
  error?: string
  message?: string
  children?: ReactNode
}

function describedByIds(htmlFor: string, hint?: string, error?: string) {
  return [hint ? `${htmlFor}-hint` : '', error ? `${htmlFor}-error` : ''].filter(Boolean).join(' ') || undefined
}

export function FormField({ label, htmlFor, hint, error, errorTestId, className, children }: FormFieldProps) {
  const ariaDescribedBy = describedByIds(htmlFor, hint, error)
  const childProps = children.props
  const control = isValidElement(children)
    ? cloneElement(children, {
        id: childProps.id ?? htmlFor,
        'aria-describedby': [childProps['aria-describedby'], ariaDescribedBy].filter(Boolean).join(' ') || undefined,
        'aria-invalid': error ? true : childProps['aria-invalid'],
      })
    : children

  return (
    <div className={cn('field form-field', error && 'form-field--error', className)}>
      <label className="form-field__label" htmlFor={htmlFor}>
        {label}
      </label>
      {control}
      {hint && (
        <small id={`${htmlFor}-hint`} className="field-label form-field__hint">
          {hint}
        </small>
      )}
      {error && (
        <small id={`${htmlFor}-error`} className="field-error form-field__error" data-testid={errorTestId}>
          {error}
        </small>
      )}
    </div>
  )
}

export function SaveBar({ dirty = false, saving = false, saved = false, error, message, children }: SaveBarProps) {
  const state = error ? 'error' : saving ? 'saving' : dirty ? 'dirty' : saved ? 'saved' : 'idle'
  const defaultMessage = {
    idle: '当前没有未保存修改。',
    dirty: '有未保存修改。',
    saving: '正在保存…',
    saved: '已保存。',
    error: error ?? '保存失败。',
  }[state]

  return (
    <div className={cn('save-bar', `save-bar--${state}`)}>
      <p className="save-bar__message" role={error ? 'alert' : 'status'} aria-live={error ? 'assertive' : 'polite'}>
        {error ?? message ?? defaultMessage}
      </p>
      {children && <div className="save-bar__actions">{children}</div>}
    </div>
  )
}
