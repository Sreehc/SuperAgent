import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DisplayMessage } from '../types'
import { MessageActions } from './MessageActions'

const assistantMessage: DisplayMessage = {
  id: 42,
  role: 'assistant',
  content: '这是回答正文',
  status: 'success',
  createdAt: '2026-01-01T00:00:00Z',
  references: [],
  feedback: null,
}

describe('MessageActions', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('copies assistant message content', async () => {
    render(
      <MessageActions
        message={assistantMessage}
        onFeedback={vi.fn()}
        onCorrection={vi.fn()}
        onClearFeedback={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '复制回答' }))

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('这是回答正文')
    expect(await screen.findByText('已复制')).toBeTruthy()
  })

  it('submits feedback and corrections without using native prompts', () => {
    const onFeedback = vi.fn()
    const onCorrection = vi.fn()

    render(
      <MessageActions
        message={assistantMessage}
        onFeedback={onFeedback}
        onCorrection={onCorrection}
        onClearFeedback={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '标记有帮助' }))
    expect(onFeedback).toHaveBeenCalledWith(assistantMessage, 'up')

    fireEvent.click(screen.getByRole('button', { name: '提交更正' }))
    const input = screen.getByLabelText('更正建议')
    fireEvent.change(input, { target: { value: '  应补充审批条件  ' } })
    fireEvent.click(screen.getByRole('button', { name: '提交' }))

    expect(onCorrection).toHaveBeenCalledWith(assistantMessage, '应补充审批条件')
  })
})
