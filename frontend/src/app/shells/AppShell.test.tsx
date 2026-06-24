import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useAuthStore } from '@/features/auth/store/auth'
import { AppShell } from './AppShell'

vi.mock('@/components/ThemeToggle', () => ({
  ThemeToggle: ({ className }: { className?: string }) => (
    <button type="button" className={className} aria-label="切换主题">
      主题
    </button>
  ),
}))

beforeEach(() => {
  useAuthStore.setState({
    accessToken: 'token',
    user: { id: 1, username: 'admin', displayName: 'Admin' },
    tenants: [{ id: 2, name: 'Acme', code: 'acme', role: 'ADMIN', status: 'active' }],
    currentTenantId: 2,
    initialized: true,
    loadingProfile: false,
    pendingRedirect: null,
  })
})

function renderShell(route = '/tools') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route path="/*" element={<AppShell />}>
          <Route path="*" element={<div>当前页面</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('AppShell responsive navigation', () => {
  it('keeps the desktop rail and highlights the current route', () => {
    renderShell('/tools')

    const desktopNav = screen.getByTestId('desktop-nav')
    const activeLink = within(desktopNav).getByRole('link', { name: 'Tools' })

    expect(desktopNav.getAttribute('aria-label')).toBe('主导航')
    expect(activeLink.getAttribute('aria-current')).toBe('page')
    expect(activeLink.className).toContain('global-rail__item--active')
  })

  it('expands the rail instead of opening a separate navigation drawer', () => {
    renderShell('/tools')

    const desktopNav = screen.getByTestId('desktop-nav')

    expect(desktopNav.className).not.toContain('global-rail--expanded')
    expect(screen.queryByRole('dialog', { name: '主导航' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: '打开导航' }))

    expect(screen.queryByRole('dialog', { name: '主导航' })).toBeNull()
    expect(desktopNav.className).toContain('global-rail--expanded')
    expect(screen.getByRole('button', { name: '收起导航' })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '收起导航' }))

    expect(desktopNav.className).not.toContain('global-rail--expanded')
  })
})
