import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const baseCss = readFileSync(`${process.cwd()}/src/styles/base.css`, 'utf8')
const appCss = readFileSync(`${process.cwd()}/src/styles.css`, 'utf8')

describe('global interaction styles', () => {
  it('keeps keyboard focus visible across interactive controls', () => {
    expect(baseCss).toMatch(/:focus-visible\s*{[\s\S]*outline:\s*2px solid var\(--accent\)[\s\S]*outline-offset:\s*2px/)
  })

  it('defines touch behavior and coarse pointer target sizing', () => {
    expect(baseCss).toMatch(/touch-action:\s*manipulation/)
    expect(appCss).toMatch(/@media\s*\(pointer:\s*coarse\)\s*{[\s\S]*\.btn[\s\S]*min-height:\s*44px/)
    expect(appCss).toMatch(/@media\s*\(pointer:\s*coarse\)\s*{[\s\S]*\.icon-button[\s\S]*width:\s*44px/)
    expect(appCss).toMatch(/@media\s*\(pointer:\s*coarse\)\s*{[\s\S]*\.command-palette__item[\s\S]*min-height:\s*44px/)
  })

  it('prevents drawer and dialog scroll chaining on touch devices', () => {
    expect(appCss).toMatch(/\.drawer-overlay[\s\S]*touch-action:\s*none/)
    expect(appCss).toMatch(/\.detail-drawer[\s\S]*overscroll-behavior:\s*contain/)
    expect(appCss).toMatch(/\.detail-drawer__body[\s\S]*overscroll-behavior:\s*contain/)
    expect(appCss).toMatch(/\.dialog-content[\s\S]*overscroll-behavior:\s*contain/)
  })
})
