import { useEffect, useId, useMemo, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { MagnifyingGlass } from '@phosphor-icons/react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { selectCurrentRole, useAuthStore } from '@/features/auth/store/auth'
import { NAV_ITEMS } from '@/app/nav'
import { EmptyState } from '@/shared/ui'

/** ⌘K command palette: fuzzy nav across role-visible pages. */
export function CommandPalette() {
  const navigate = useNavigate()
  const role = useAuthStore(selectCurrentRole)
  const listboxId = useId()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)

  useEffect(() => {
    function onKeydown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen((value) => !value)
      }
    }
    function onOpen() {
      setOpen(true)
    }
    document.addEventListener('keydown', onKeydown)
    document.addEventListener('open-command-palette', onOpen)
    return () => {
      document.removeEventListener('keydown', onKeydown)
      document.removeEventListener('open-command-palette', onOpen)
    }
  }, [])

  const items = useMemo(() => {
    const visible = NAV_ITEMS.filter((item) => role && item.roles.includes(role))
    const search = query.trim().toLowerCase()
    if (!search) return visible
    return visible.filter((item) => item.label.toLowerCase().includes(search) || item.to.includes(search))
  }, [role, query])

  useEffect(() => {
    setActiveIndex(items.length > 0 ? 0 : -1)
  }, [items.length, query])

  function updateOpen(value: boolean) {
    setOpen(value)
    if (!value) {
      setQuery('')
      setActiveIndex(0)
    }
  }

  function go(to: string) {
    setOpen(false)
    setQuery('')
    setActiveIndex(0)
    navigate(to)
  }

  function onInputKeyDown(event: ReactKeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActiveIndex((index) => (items.length === 0 ? -1 : Math.min(index + 1, items.length - 1)))
      return
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActiveIndex((index) => (items.length === 0 ? -1 : Math.max(index - 1, 0)))
      return
    }

    if (event.key === 'Home') {
      event.preventDefault()
      setActiveIndex(items.length > 0 ? 0 : -1)
      return
    }

    if (event.key === 'End') {
      event.preventDefault()
      setActiveIndex(items.length > 0 ? items.length - 1 : -1)
      return
    }

    if (event.key === 'Enter') {
      event.preventDefault()
      const item = items[activeIndex]
      if (item) go(item.to)
    }
  }

  const activeItem = activeIndex >= 0 ? items[activeIndex] : null

  return (
    <DialogPrimitive.Root open={open} onOpenChange={updateOpen}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="dialog-overlay" />
        <DialogPrimitive.Content className="command-box command-palette" aria-label="命令面板">
          <DialogPrimitive.Title className="sr-only">命令面板</DialogPrimitive.Title>
          <div className="command-palette__search">
            <MagnifyingGlass size={16} aria-hidden="true" />
            <input
              autoFocus
              role="combobox"
              aria-label="搜索页面或操作"
              aria-expanded="true"
              aria-controls={listboxId}
              aria-activedescendant={activeItem ? `command-${activeItem.to}` : undefined}
              aria-autocomplete="list"
              value={query}
              placeholder="搜索页面或操作…"
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={onInputKeyDown}
            />
          </div>
          <ul id={listboxId} className="command-palette__list" role="listbox" aria-label="可用命令">
            {items.map((item) => {
              const Icon = item.icon
              const selected = activeItem?.to === item.to
              return (
                <li key={item.to} id={`command-${item.to}`} role="option" aria-selected={selected}>
                  <button
                    type="button"
                    className={`command-palette__item${selected ? ' command-palette__item--active' : ''}`}
                    tabIndex={-1}
                    onMouseEnter={() => setActiveIndex(items.findIndex((candidate) => candidate.to === item.to))}
                    onClick={() => go(item.to)}
                  >
                    <Icon size={16} aria-hidden="true" />
                    <span>{item.label}</span>
                    <code>{item.to}</code>
                  </button>
                </li>
              )
            })}
            {items.length === 0 && (
              <li className="command-palette__empty">
                <EmptyState title="无匹配命令" description="换一个关键词，或稍后扩展到会话和知识库搜索。" size="compact" />
              </li>
            )}
          </ul>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
