// 打字机效果工具函数
export function typewriterEffect(
  element: HTMLElement,
  text: string,
  speed: number = 20,
  onComplete?: () => void
): () => void {
  let index = 0
  let intervalId: number | null = null

  const type = () => {
    if (index < text.length) {
      element.textContent = text.substring(0, index + 1)
      index++
    } else {
      if (intervalId !== null) {
        clearInterval(intervalId)
      }
      onComplete?.()
    }
  }

  intervalId = window.setInterval(type, speed)

  // 返回取消函数
  return () => {
    if (intervalId !== null) {
      clearInterval(intervalId)
    }
  }
}

// Markdown 流式渲染打字机效果
export function streamingTypewriter(
  element: HTMLElement,
  content: string,
  renderMarkdown: (text: string) => string,
  speed: number = 10,
  onComplete?: () => void
): () => void {
  let index = 0
  let intervalId: number | null = null

  const type = () => {
    if (index < content.length) {
      // 逐字符增加
      const currentText = content.substring(0, index + 1)
      // 渲染当前文本
      element.innerHTML = renderMarkdown(currentText)
      index++
    } else {
      if (intervalId !== null) {
        clearInterval(intervalId)
      }
      onComplete?.()
    }
  }

  intervalId = window.setInterval(type, speed)

  // 返回取消函数
  return () => {
    if (intervalId !== null) {
      clearInterval(intervalId)
    }
  }
}
