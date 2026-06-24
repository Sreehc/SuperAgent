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

  it('supports expanding the left rail on compact layouts up to tablet width', () => {
    expect(appCss).toMatch(/@media\s*\(max-width:\s*1180px\)\s*{[\s\S]*\.utility-bar__menu[\s\S]*display:\s*inline-flex/)
    expect(appCss).toMatch(/@media\s*\(max-width:\s*1180px\)\s*{[\s\S]*\.console-workspace[\s\S]*margin-left:\s*var\(--rail-width\)/)
    expect(appCss).toMatch(/\.global-rail--expanded[\s\S]*width:\s*min\(292px,\s*calc\(100vw - 24px\)\)/)
    expect(appCss).toMatch(/\.console-shell__overlay[\s\S]*display:\s*block/)
  })

  it('skins native selects and file pickers to match the app chrome', () => {
    expect(appCss).toMatch(/\.select-control__button\s*{[\s\S]*grid-template-columns:\s*minmax\(0,\s*1fr\)\s*auto/)
    expect(appCss).toMatch(/\.select-control__panel\s*{[\s\S]*position:\s*absolute/)
    expect(appCss).toMatch(/\.file-trigger__chrome\s*{[\s\S]*border:\s*1px dashed/)
    expect(appCss).toMatch(/\.file-trigger__button\s*{[\s\S]*min-height:\s*34px/)
  })

  it('uses minimal surface framing for authenticated workspace layouts', () => {
    expect(appCss).toMatch(/\.session-rail,\s*[\s\S]*\.conversation-surface,\s*[\s\S]*\.evidence-inspector\s*{[\s\S]*border:\s*0/)
    expect(appCss).toMatch(/\.workspace-strip,\s*[\s\S]*\.surface-box,\s*[\s\S]*\.inset-box,\s*[\s\S]*\.data-frame,\s*[\s\S]*\.inspector-box,\s*[\s\S]*\.command-box\s*{[\s\S]*border:\s*0/)
    expect(appCss).toMatch(/\.table-wrap\s*{[\s\S]*border:\s*0/)
    expect(appCss).toMatch(/\.state-card\s*{[\s\S]*border:\s*0/)
  })

  it('aligns admin console pages to the same outer spacing and softened container rhythm as chat', () => {
    expect(appCss).toMatch(/\.console-content\s*{[\s\S]*padding:\s*16px/)
    expect(appCss).toMatch(/\.workspace-page\s*{[\s\S]*gap:\s*16px/)
    expect(appCss).toMatch(/\.workspace-strip\s*{[\s\S]*padding:\s*16px 18px/)
    expect(appCss).toMatch(/\.surface-box\s*{[\s\S]*padding:\s*16px/)
    expect(appCss).toMatch(/\.filter-row\s*{[\s\S]*padding:\s*12px 14px/)
    expect(appCss).toMatch(/\.table-wrap\s*{[\s\S]*border-radius:\s*22px/)
    expect(appCss).toMatch(/\.trace-detail-layout\s*{[\s\S]*gap:\s*16px/)
  })

  it('keeps the login brand signature legible in dark theme', () => {
    expect(appCss).toMatch(/:root\[data-theme="dark"\]\s+\.login-brief__signature\s*{[\s\S]*background:/)
    expect(appCss).toMatch(/:root\[data-theme="dark"\]\s+\.login-brief__signature\s+\.section-label\s*{[\s\S]*color:\s*var\(--text-main\)/)
  })

  it('keeps the login headline secondary line on a single row', () => {
    expect(appCss).toMatch(/\.login-brief__title-secondary\s*{[\s\S]*white-space:\s*nowrap/)
    expect(appCss).toMatch(/\.login-brief__title-secondary\s*{[\s\S]*max-width:\s*none/)
  })
})
