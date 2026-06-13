import DOMPurify from 'dompurify'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true,
})

export function renderMarkdown(content: string) {
  const raw = marked.parse(content || '') as string
  const sanitized = DOMPurify.sanitize(raw, {
    USE_PROFILES: { html: true },
  })
  return enhanceCodeBlocks(sanitized)
}

function enhanceCodeBlocks(html: string) {
  if (typeof document === 'undefined') {
    return html
  }

  const template = document.createElement('template')
  template.innerHTML = html
  template.content.querySelectorAll('pre > code').forEach((code, index) => {
    const pre = code.parentElement
    if (!pre) {
      return
    }

    const language = readCodeLanguage(code.className)
    const figure = document.createElement('figure')
    figure.className = 'markdown-codeblock'

    const header = document.createElement('figcaption')
    header.className = 'markdown-codeblock__header'

    const label = document.createElement('span')
    label.textContent = language || 'code'

    const copyButton = document.createElement('button')
    copyButton.type = 'button'
    copyButton.className = 'markdown-codeblock__copy'
    copyButton.dataset.codeCopy = String(index)
    copyButton.textContent = '复制代码'

    header.append(label, copyButton)
    pre.replaceWith(figure)
    figure.append(header, pre)
  })

  return template.innerHTML
}

function readCodeLanguage(className: string) {
  const matched = className.match(/(?:^|\s)language-([a-z0-9_+-]+)/i)
  return matched?.[1] ?? ''
}
