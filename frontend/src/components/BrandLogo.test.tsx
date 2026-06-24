import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { BrandLogo } from './BrandLogo'

describe('BrandLogo', () => {
  it('renders the refined hub mark with a center core and four orbit tiles', () => {
    const { container } = render(<BrandLogo size="medium" />)

    expect(screen.getByTestId('brand-logo')).toBeTruthy()
    expect(container.querySelector('[data-testid="brand-logo-core"]')).toBeTruthy()
    expect(container.querySelectorAll('[data-testid="brand-logo-tile"]').length).toBe(4)
  })
})
