import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './renderMarkdown'

describe('renderMarkdown', () => {
  it('sanitizes dangerous html and scripts', () => {
    const html = renderMarkdown('hello <script>alert(1)</script><img src=x onerror=alert(2) /><a href="javascript:alert(3)">x</a>')

    expect(html).toContain('<p>hello ')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('onerror=')
    expect(html).not.toContain('javascript:alert')
  })
})
