import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog, PromptDialog } from './dialog'

describe('ConfirmDialog', () => {
  it('calls confirm handler and supports cancel', async () => {
    const onConfirm = vi.fn()
    const onOpenChange = vi.fn()

    render(
      <ConfirmDialog
        open
        title="删除文档"
        description="删除后无法恢复。"
        confirmLabel="确认删除"
        cancelLabel="取消"
        onConfirm={onConfirm}
        onOpenChange={onOpenChange}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '取消' }))
    expect(onOpenChange).toHaveBeenCalledWith(false)

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('restores focus to the opener after a controlled confirm dialog closes', async () => {
    function Harness() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            删除文档
          </button>
          <ConfirmDialog
            open={open}
            title="删除文档"
            description="删除后无法恢复。"
            onConfirm={() => {}}
            onOpenChange={setOpen}
          />
        </>
      )
    }

    render(<Harness />)

    const opener = screen.getByRole('button', { name: '删除文档' })
    opener.focus()
    fireEvent.click(opener)

    expect(screen.getByRole('dialog', { name: '删除文档' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    await waitFor(() => expect(document.activeElement).toBe(opener))
  })

  it('restores focus when a controlled confirm dialog is mounted only while open', async () => {
    function Harness() {
      const [open, setOpen] = useState(false)
      return (
        <>
          <button type="button" onClick={() => setOpen(true)}>
            保存配置
          </button>
          {open && (
            <ConfirmDialog
              open
              title="保存 RAG 配置"
              description="保存后会影响检索策略。"
              onConfirm={() => {}}
              onOpenChange={setOpen}
            />
          )}
        </>
      )
    }

    render(<Harness />)

    const opener = screen.getByRole('button', { name: '保存配置' })
    opener.focus()
    fireEvent.click(opener)

    expect(screen.getByRole('dialog', { name: '保存 RAG 配置' })).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    await waitFor(() => expect(document.activeElement).toBe(opener))
  })
})

describe('PromptDialog', () => {
  it('submits trimmed input values', async () => {
    const onConfirm = vi.fn()

    render(
      <PromptDialog
        open
        title="重命名会话"
        label="会话名称"
        defaultValue="  原标题  "
        confirmLabel="保存"
        onConfirm={onConfirm}
        onOpenChange={() => {}}
      />,
    )

    const input = screen.getByLabelText('会话名称')
    fireEvent.change(input, { target: { value: '  新标题  ' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(onConfirm).toHaveBeenCalledWith('新标题')
  })

  it('disables confirm when required input is blank', () => {
    render(
      <PromptDialog
        open
        title="更正回答"
        label="更正建议"
        required
        confirmLabel="提交"
        onConfirm={() => {}}
        onOpenChange={() => {}}
      />,
    )

    expect(screen.getByRole('button', { name: '提交' }).getAttribute('disabled')).toBe('')
  })
})
