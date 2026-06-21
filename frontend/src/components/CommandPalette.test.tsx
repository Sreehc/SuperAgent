import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/features/auth/store/auth'
import { CommandPalette } from './CommandPalette'

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

function LocationProbe() {
  const location = useLocation()
  return <output aria-label="当前路径">{location.pathname}</output>
}

function renderPalette(route = '/chat') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <CommandPalette />
      <Routes>
        <Route path="*" element={<LocationProbe />} />
      </Routes>
    </MemoryRouter>,
  )
}

function openWithKeyboard() {
  fireEvent.keyDown(document, { key: 'k', ctrlKey: true })
}

describe('CommandPalette', () => {
  it('opens with Ctrl+K and exposes the command list', () => {
    renderPalette()

    expect(screen.queryByRole('dialog', { name: '命令面板' })).toBeNull()

    openWithKeyboard()

    const dialog = screen.getByRole('dialog', { name: '命令面板' })
    expect(within(dialog).getByRole('combobox', { name: '搜索页面或操作' })).toBeTruthy()
    expect(within(dialog).getByRole('listbox', { name: '可用命令' })).toBeTruthy()
  })

  it('supports keyboard selection and Enter navigation', () => {
    renderPalette()
    openWithKeyboard()

    const input = screen.getByRole('combobox', { name: '搜索页面或操作' })
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(screen.getByLabelText('当前路径').textContent).toBe('/knowledge')
    expect(screen.queryByRole('dialog', { name: '命令面板' })).toBeNull()
  })

  it('renders a clear empty result state', () => {
    renderPalette()
    openWithKeyboard()

    fireEvent.change(screen.getByRole('combobox', { name: '搜索页面或操作' }), { target: { value: 'no-matches' } })

    const dialog = screen.getByRole('dialog', { name: '命令面板' })
    expect(within(dialog).getByRole('status', { name: '无匹配命令' })).toBeTruthy()
  })
})
