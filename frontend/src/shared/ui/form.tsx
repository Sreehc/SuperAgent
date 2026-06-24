import { Children, cloneElement, forwardRef, isValidElement, useEffect, useId, useMemo, useRef, useState } from 'react'
import type {
  AriaAttributes,
  ChangeEvent,
  InputHTMLAttributes,
  KeyboardEvent,
  ReactElement,
  ReactNode,
  SelectHTMLAttributes,
} from 'react'
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

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  wrapperClassName?: string
  panelPlacement?: 'top' | 'bottom'
}

interface FileTriggerProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  buttonLabel?: string
  placeholder?: string
  summary?: string
  wrapperClassName?: string
}

function describedByIds(htmlFor: string, hint?: string, error?: string) {
  return [hint ? `${htmlFor}-hint` : '', error ? `${htmlFor}-error` : ''].filter(Boolean).join(' ') || undefined
}

interface SelectOptionItem {
  value: string
  label: ReactNode
  disabled?: boolean
}

function optionItems(children: ReactNode): SelectOptionItem[] {
  return Children.toArray(children)
    .flatMap((child) => {
      if (!isValidElement(child) || child.type !== 'option') return []
      const optionProps = child.props as React.OptionHTMLAttributes<HTMLOptionElement> & { value?: string | number }
      return [
        {
          value: String(optionProps.value ?? ''),
          label: optionProps.children,
          disabled: Boolean(optionProps.disabled),
        },
      ]
    })
    .filter((item) => item.value !== undefined)
}

function changeEvent(value: string) {
  return {
    target: { value },
    currentTarget: { value },
  } as ChangeEvent<HTMLSelectElement>
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

export function SelectField({
  className,
  wrapperClassName,
  panelPlacement = 'bottom',
  children,
  disabled,
  id,
  value,
  defaultValue,
  onChange,
  name,
  form,
  required,
  'aria-describedby': ariaDescribedBy,
  'aria-invalid': ariaInvalid,
  ...props
}: SelectFieldProps) {
  const instanceId = useId()
  const selectId = id ?? instanceId
  const panelId = `${selectId}-panel`
  const rootRef = useRef<HTMLSpanElement | null>(null)
  const [open, setOpen] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)
  const [internalValue, setInternalValue] = useState(() => `${defaultValue ?? ''}`)
  const options = useMemo(() => optionItems(children), [children])
  const selectedValue = `${value !== undefined ? value : internalValue}`
  const selectedOption = options.find((option) => option.value === selectedValue) ?? options.find((option) => option.value === '')
  const displayLabel = selectedOption?.label ?? '请选择'
  const hiddenSelectProps = {
    ...props,
    id,
    value,
    defaultValue,
    name,
    form,
    required,
    disabled,
    onChange,
  }

  useEffect(() => {
    if (value === undefined) return
    setInternalValue(`${value ?? ''}`)
  }, [value])

  useEffect(() => {
    if (!open) return
    const currentIndex = Math.max(
      0,
      options.findIndex((option) => option.value === selectedValue && !option.disabled),
    )
    setActiveIndex(currentIndex >= 0 ? currentIndex : 0)

    function handlePointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }

    window.addEventListener('pointerdown', handlePointerDown)
    return () => window.removeEventListener('pointerdown', handlePointerDown)
  }, [open, options, selectedValue])

  function emitValue(nextValue: string) {
    if (disabled) return
    setInternalValue(nextValue)
    onChange?.(changeEvent(nextValue))
    setOpen(false)
  }

  function handleButtonKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (disabled) return
    if (!open && (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowDown')) {
      event.preventDefault()
      setOpen(true)
      return
    }
    if (!open) return

    if (event.key === 'Escape') {
      event.preventDefault()
      setOpen(false)
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((current) => Math.min(current + 1, options.length - 1))
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((current) => Math.max(current - 1, 0))
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      const candidate = options[activeIndex]
      if (candidate && !candidate.disabled) {
        emitValue(candidate.value)
      }
    }
  }

  return (
    <span
      ref={rootRef}
      className={cn('select-control', wrapperClassName)}
      data-open={open ? 'true' : undefined}
      data-panel-placement={panelPlacement}
    >
      <select className="select-control__native" aria-hidden="true" tabIndex={-1} {...hiddenSelectProps}>
        {children}
      </select>
      <button
        id={selectId}
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={panelId}
        aria-describedby={ariaDescribedBy}
        aria-invalid={ariaInvalid}
        aria-disabled={disabled || undefined}
        className={cn('select-control__button', className)}
        disabled={disabled}
        data-state={open ? 'open' : 'closed'}
        onClick={() => !disabled && setOpen((current) => !current)}
        onKeyDown={handleButtonKeyDown}
      >
        <span className={cn('select-control__value', !selectedOption && 'select-control__value--placeholder')}>
          {displayLabel}
        </span>
        <span className="select-control__indicator" aria-hidden="true">
          <i />
        </span>
      </button>
      {open && (
        <div
          id={panelId}
          className="select-control__panel"
          role="listbox"
          aria-label="选择项"
          data-placement={panelPlacement}
        >
          {options.length > 0 ? (
            options.map((option, index) => {
              const selected = option.value === selectedValue
              return (
                <button
                  key={option.value || `option-${index}`}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  className={cn('select-control__option', selected && 'select-control__option--selected')}
                  disabled={option.disabled}
                  data-active={index === activeIndex ? 'true' : undefined}
                  onMouseEnter={() => setActiveIndex(index)}
                  onClick={() => emitValue(option.value)}
                >
                  <span className="select-control__option-label">{option.label}</span>
                  {selected && <span className="select-control__check">✓</span>}
                </button>
              )
            })
          ) : (
            <div className="select-control__empty">没有可选项</div>
          )}
        </div>
      )}
    </span>
  )
}

export const FileTrigger = forwardRef<HTMLInputElement, FileTriggerProps>(function FileTrigger(
  { className, wrapperClassName, buttonLabel = '选择文件', placeholder = '未选择文件', summary, ...props },
  ref,
) {
  return (
    <span className={cn('file-trigger', wrapperClassName)}>
      <span className="file-trigger__chrome" aria-hidden="true">
        <span className="file-trigger__button">{buttonLabel}</span>
        <span className={cn('file-trigger__summary', !summary && 'file-trigger__summary--placeholder')}>
          {summary || placeholder}
        </span>
      </span>
      <input ref={ref} type="file" className={cn('file-trigger__input', className)} {...props} />
    </span>
  )
})
