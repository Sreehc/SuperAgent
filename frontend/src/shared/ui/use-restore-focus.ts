import { useEffect, useRef } from 'react'

function getActiveElement() {
  return document.activeElement instanceof HTMLElement ? document.activeElement : null
}

function restoreFocus(element: HTMLElement | null) {
  if (!element || !document.contains(element)) return

  window.requestAnimationFrame(() => {
    if (document.contains(element)) {
      element.focus({ preventScroll: true })
    }
  })
}

export function useRestoreFocusOnClose(open: boolean) {
  const previousFocusRef = useRef<HTMLElement | null>(open ? getActiveElement() : null)
  const wasOpenRef = useRef(open)

  if (open && !wasOpenRef.current && previousFocusRef.current == null) {
    previousFocusRef.current = getActiveElement()
  }

  useEffect(() => {
    if (open) {
      wasOpenRef.current = true
      return
    }

    const previousFocus = previousFocusRef.current
    previousFocusRef.current = null
    wasOpenRef.current = false
    restoreFocus(previousFocus)
  }, [open])

  useEffect(() => {
    return () => {
      if (wasOpenRef.current) {
        restoreFocus(previousFocusRef.current)
      }
    }
  }, [])
}
