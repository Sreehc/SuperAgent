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

  it('wraps fenced code blocks with safe copy controls', () => {
    const html = renderMarkdown('```ts\nconst answer = 42\n```')

    expect(html).toContain('markdown-codeblock')
    expect(html).toContain('ts')
    expect(html).toContain('复制代码')
    // After highlight.js tokenization, content is wrapped in <span> tags;
    // assert key tokens are still present.
    expect(html).toContain('answer')
    expect(html).toContain('42')
    expect(html).toContain('hljs')
  })
})
