import { useState } from 'react'
import { Moon, Sun } from '@phosphor-icons/react'

const THEME_KEY = 'superagent.theme'
type Theme = 'light' | 'dark'

function resolveInitial(): Theme {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'dark' || saved === 'light') return saved
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** Apply the persisted/system theme to <html>. Call once before render. */
export function initTheme() {
  document.documentElement.dataset.theme = resolveInitial()
}

export function ThemeToggle({ className }: { className?: string }) {
  const [theme, setTheme] = useState<Theme>(() => (document.documentElement.dataset.theme as Theme) || 'light')

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = next
    localStorage.setItem(THEME_KEY, next)
    setTheme(next)
  }

  return (
    <button type="button" className={className ?? 'icon-button'} onClick={toggle} aria-label="切换主题" title="切换主题">
      {theme === 'dark' ? <Sun size={18} aria-hidden="true" /> : <Moon size={18} aria-hidden="true" />}
    </button>
  )
}
