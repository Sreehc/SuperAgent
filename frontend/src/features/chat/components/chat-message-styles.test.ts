import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const appCss = readFileSync(`${process.cwd()}/src/styles.css`, 'utf8')

describe('chat message styles', () => {
  it('keeps long message and markdown content inside the thread width', () => {
    expect(appCss).toMatch(/\.message-block__content\s*{[\s\S]*min-width:\s*0/)
    expect(appCss).toMatch(/\.message-block__content\s*{[\s\S]*overflow-wrap:\s*anywhere/)
    expect(appCss).toMatch(/\.markdown-body\s*{[\s\S]*min-width:\s*0/)
    expect(appCss).toMatch(/\.markdown-codeblock\s*{[\s\S]*max-width:\s*100%/)
    expect(appCss).toMatch(/\.markdown-body table\s*{[\s\S]*display:\s*block[\s\S]*overflow-x:\s*auto/)
  })

  it('defines a compact action row for assistant messages', () => {
    expect(appCss).toMatch(/\.message-actions\s*{[\s\S]*display:\s*flex/)
    expect(appCss).toMatch(/\.message-actions__button\s*{[\s\S]*min-height:\s*28px/)
  })

  it('keeps execution summaries compact inside assistant messages', () => {
    expect(appCss).toMatch(/\.execution-summary\s*{[\s\S]*display:\s*grid[\s\S]*max-width:\s*100%/)
    expect(appCss).toMatch(/\.execution-summary__item\s*{[\s\S]*grid-template-columns:\s*auto minmax\(0,\s*1fr\)/)
    expect(appCss).toMatch(/\.execution-summary__body\s*{[\s\S]*overflow-wrap:\s*anywhere/)
  })
})
