import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MagnifyingGlass } from '@phosphor-icons/react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { selectCurrentRole, useAuthStore } from '@/features/auth/store/auth'
import { NAV_ITEMS } from '@/app/nav'

/** ⌘K command palette: fuzzy nav across role-visible pages. */
export function CommandPalette() {
  const navigate = useNavigate()
  const role = useAuthStore(selectCurrentRole)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')

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

  function go(to: string) {
    setOpen(false)
    setQuery('')
    navigate(to)
  }

  return (
    <DialogPrimitive.Root open={open} onOpenChange={setOpen}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="dialog-overlay" />
        <DialogPrimitive.Content className="command-box command-palette" aria-label="命令面板">
          <DialogPrimitive.Title className="sr-only">命令面板</DialogPrimitive.Title>
          <div className="command-palette__search">
            <MagnifyingGlass size={16} aria-hidden="true" />
            <input
              autoFocus
              value={query}
              placeholder="搜索页面或操作…"
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && items[0]) go(items[0].to)
              }}
            />
          </div>
          <ul className="command-palette__list">
            {items.map((item) => {
              const Icon = item.icon
              return (
                <li key={item.to}>
                  <button type="button" className="command-palette__item" onClick={() => go(item.to)}>
                    <Icon size={16} aria-hidden="true" />
                    <span>{item.label}</span>
                    <code>{item.to}</code>
                  </button>
                </li>
              )
            })}
            {items.length === 0 && <li className="empty-line">无匹配项</li>}
          </ul>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
