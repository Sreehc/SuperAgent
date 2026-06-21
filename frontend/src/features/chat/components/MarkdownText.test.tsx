import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MarkdownText } from './MarkdownText'

describe('MarkdownText', () => {
  beforeEach(() => {
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    })
  })

  it('copies fenced code block text from rendered markdown', async () => {
    render(<MarkdownText text={'```ts\nconst answer = 42\n```'} />)

    const copyButton = screen.getByRole('button', { name: '复制代码块' })
    fireEvent.click(copyButton)

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('const answer = 42\n')
    expect(await screen.findByText('已复制')).toBeTruthy()
  })
})
