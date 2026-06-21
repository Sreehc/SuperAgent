import { useEffect, useMemo, useRef, useState } from 'react'
import { renderMarkdown } from '../utils/renderMarkdown'

type CopyStatus = 'idle' | 'copied' | 'failed'

export function MarkdownText({ text }: { text: string }) {
  const html = useMemo(() => renderMarkdown(text), [text])
  const resetTimer = useRef<number | null>(null)
  const [copyStatus, setCopyStatus] = useState<CopyStatus>('idle')

  useEffect(() => {
    return () => {
      if (resetTimer.current !== null) {
        window.clearTimeout(resetTimer.current)
      }
    }
  }, [])

  async function handleCopy(event: React.MouseEvent<HTMLDivElement>) {
    const target = event.target
    if (!(target instanceof Element)) {
      return
    }

    const button = target.closest<HTMLButtonElement>('[data-code-copy]')
    if (!button) {
      return
    }

    const figure = button.closest('.markdown-codeblock')
    const code = figure?.querySelector('pre code')
    const codeText = code?.textContent ?? ''
    if (!codeText) {
      return
    }

    try {
      await copyText(codeText)
      updateButtonLabel(button, '已复制', '已复制代码块')
      setCopyStatus('copied')
    } catch {
      updateButtonLabel(button, '复制失败', '复制代码块失败')
      setCopyStatus('failed')
    }

    if (resetTimer.current !== null) {
      window.clearTimeout(resetTimer.current)
    }
    resetTimer.current = window.setTimeout(() => {
      updateButtonLabel(button, '复制代码', '复制代码块')
      setCopyStatus('idle')
    }, 1600)
  }

  return (
    <>
      <div className="markdown-body" onClick={handleCopy} dangerouslySetInnerHTML={{ __html: html }} />
      <span className="sr-only" aria-live="polite">
        {copyStatus === 'copied' ? '已复制' : copyStatus === 'failed' ? '复制失败' : ''}
      </span>
    </>
  )
}

async function copyText(value: string) {
  if (!navigator.clipboard?.writeText) {
    throw new Error('Clipboard API is unavailable')
  }
  await navigator.clipboard.writeText(value)
}

function updateButtonLabel(button: HTMLButtonElement, text: string, label: string) {
  button.textContent = text
  button.setAttribute('aria-label', label)
}
