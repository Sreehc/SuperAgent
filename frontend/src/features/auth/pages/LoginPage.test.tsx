import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/auth'
import { LoginPage } from './LoginPage'

beforeEach(() => {
  useAuthStore.setState({
    accessToken: null,
    user: null,
    tenants: [],
    currentTenantId: null,
    initialized: true,
    loadingProfile: false,
    pendingRedirect: null,
  })
})

describe('LoginPage brand layout', () => {
  it('groups the logo into a compact brand signature row above the main headline', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    const brandHeader = screen.getByTestId('login-brand-header')
    const brandSignature = screen.getByTestId('login-brand-signature')
    const headline = within(brandHeader).getByRole('heading', { level: 1, name: 'Agent 运行控制台' })

    expect(within(brandSignature).getByTestId('brand-logo')).toBeTruthy()
    expect(within(brandSignature).getByText('SuperAgent')).toBeTruthy()
    expect(headline).toBeTruthy()
    expect(brandHeader.firstElementChild).toBe(brandSignature)
  })

  it('splits the hero headline into a dominant Agent line and a calmer control-console line', () => {
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    const headline = screen.getByRole('heading', { level: 1, name: 'Agent 运行控制台' })
    const firstLine = within(headline).getByTestId('login-title-primary')
    const secondLine = within(headline).getByTestId('login-title-secondary')

    expect(firstLine.textContent).toBe('Agent')
    expect(secondLine.textContent).toBe('运行控制台')
  })
})
