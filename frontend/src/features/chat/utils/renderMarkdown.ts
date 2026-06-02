import DOMPurify from 'dompurify'
import { marked } from 'marked'

marked.setOptions({
  gfm: true,
  breaks: true,
})

export function renderMarkdown(content: string) {
  const raw = marked.parse(content || '') as string
  return DOMPurify.sanitize(raw, {
    USE_PROFILES: { html: true },
  })
}
