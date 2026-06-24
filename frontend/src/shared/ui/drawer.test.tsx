import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { DetailDrawer, DetailInspector } from './drawer'

describe('DetailInspector', () => {
  it('renders a labelled complementary aside with optional actions', () => {
    render(
      <DetailInspector title="审计详情" actions={<button type="button">导出</button>} data-testid="audit-inspector">
        <p>资源变更记录</p>
      </DetailInspector>,
    )

    const inspector = screen.getByRole('complementary', { name: '审计详情' })

    expect(inspector.tagName).toBe('ASIDE')
    expect(screen.getByRole('heading', { level: 2, name: '审计详情' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '导出' })).toBeTruthy()
    expect(screen.getByText('资源变更记录')).toBeTruthy()
  })
})

describe('DetailDrawer', () => {
  it('renders dialog content and closes through the close button', () => {
    const onOpenChange = vi.fn()

    render(
      <DetailDrawer open title="证据详情" description="引用与执行链路" onOpenChange={onOpenChange}>
        <button type="button">查看 Trace</button>
      </DetailDrawer>,
    )

    const dialog = screen.getByRole('dialog', { name: '证据详情' })

    expect(dialog.getAttribute('aria-describedby')).toBeTruthy()
    expect(screen.getByText('引用与执行链路')).toBeTruthy()
    expect(screen.getByRole('button', { name: '查看 Trace' })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '关闭证据详情' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)
  })

  it('locks document scrolling while open and restores it after close', () => {
    const originalOverflow = document.body.style.overflow
    const originalOverscroll = document.body.style.overscrollBehavior
    document.body.style.overflow = 'auto'
    document.body.style.overscrollBehavior = 'auto'

    const { rerender, unmount } = render(
      <DetailDrawer open title="移动详情" onOpenChange={() => {}}>
        <p>移动端抽屉内容</p>
      </DetailDrawer>,
    )

    expect(document.body.style.overflow).toBe('hidden')
    expect(document.body.style.overscrollBehavior).toBe('contain')

    rerender(
      <DetailDrawer open={false} title="移动详情" onOpenChange={() => {}}>
        <p>移动端抽屉内容</p>
      </DetailDrawer>,
    )

    expect(document.body.style.overflow).toBe('auto')
    expect(document.body.style.overscrollBehavior).toBe('auto')

    unmount()
    document.body.style.overflow = originalOverflow
    document.body.style.overscrollBehavior = originalOverscroll
  })

  it('restores focus to the opener after a controlled drawer closes', async () => {
    function Harness() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            打开详情
          </button>
          <DetailDrawer open={open} title="审计详情" onOpenChange={setOpen}>
            <button type="button">查看资源</button>
          </DetailDrawer>
        </>
      )
    }

    render(<Harness />)

    const opener = screen.getByRole('button', { name: '打开详情' })
    opener.focus()
    fireEvent.click(opener)

    expect(screen.getByRole('dialog', { name: '审计详情' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '关闭审计详情' }))

    await waitFor(() => expect(document.activeElement).toBe(opener))
  })
})
