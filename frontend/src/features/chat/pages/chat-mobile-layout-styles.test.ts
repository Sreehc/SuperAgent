import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const appCss = readFileSync(`${process.cwd()}/src/styles.css`, 'utf8')

describe('chat mobile layout styles', () => {
  it('prioritizes the message thread and moves side panels into drawers on mobile', () => {
    expect(appCss).toMatch(
      /@media\s*\(max-width:\s*760px\)\s*{[\s\S]*\.chat-workspace\s*{[\s\S]*grid-template-rows:\s*auto minmax\(0,\s*1fr\)[\s\S]*overflow:\s*hidden/,
    )
    expect(appCss).toMatch(
      /\.chat-workspace__mobile-actions\s*{[\s\S]*display:\s*grid[\s\S]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/,
    )
    expect(appCss).toMatch(
      /\.chat-workspace\s*>\s*\.session-rail,\s*\.chat-workspace\s*>\s*\.evidence-inspector\s*{[\s\S]*display:\s*none/,
    )
    expect(appCss).toMatch(/\.conversation-surface\s*{[\s\S]*order:\s*2[\s\S]*min-height:\s*0/)
  })
})
