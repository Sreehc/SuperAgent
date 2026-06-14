import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/common'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true,
})

export function renderMarkdown(content: string) {
  const raw = marked.parse(content || '') as string
  const sanitized = DOMPurify.sanitize(raw, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['data-code-copy', 'data-code-language', 'data-highlighted'],
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
    applyHighlight(code as HTMLElement, language)

    const figure = document.createElement('figure')
    figure.className = 'markdown-codeblock'
    figure.dataset.codeLanguage = language || 'text'

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

function applyHighlight(code: HTMLElement, language: string) {
  try {
    const text = code.textContent ?? ''
    if (!text.trim()) {
      return
    }
    let highlighted = ''
    if (language && hljs.getLanguage(language)) {
      highlighted = hljs.highlight(text, { language, ignoreIllegals: true }).value
    } else {
      highlighted = hljs.highlightAuto(text).value
    }
    code.innerHTML = highlighted
    code.classList.add('hljs')
    code.dataset.highlighted = 'true'
  } catch {
    // fall back to plain text rendering
  }
}

function readCodeLanguage(className: string) {
  const matched = className.match(/(?:^|\s)language-([a-z0-9_+-]+)/i)
  return matched?.[1] ?? ''
}
