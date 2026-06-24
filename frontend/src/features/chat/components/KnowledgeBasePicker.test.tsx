import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { KnowledgeBasePicker, type KnowledgeBaseOption } from './KnowledgeBasePicker'

const options: KnowledgeBaseOption[] = [
  { id: 1, name: 'Alpha 手册' },
  { id: 2, name: 'Beta 政策' },
  { id: 3, name: 'Gamma 流程' },
  { id: 4, name: 'Delta FAQ' },
  { id: 5, name: 'Epsilon 指南' },
  { id: 6, name: 'Zeta 规范' },
]

describe('KnowledgeBasePicker', () => {
  it('keeps a compact trigger by default and only shows the search panel when opened', () => {
    render(<KnowledgeBasePicker options={options} selectedIds={[]} onChange={vi.fn()} />)

    const trigger = screen.getByRole('button', { name: '选择知识库' })

    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByPlaceholderText('搜索知识库')).toBeNull()

    fireEvent.click(trigger)

    expect(trigger.getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByPlaceholderText('搜索知识库')).toBeTruthy()
  })

  it('supports search, single selection, clearing, and the unrestricted option', () => {
    const onChange = vi.fn()
    const { rerender } = render(<KnowledgeBasePicker options={options} selectedIds={[]} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '选择知识库' }))

    const search = screen.getByPlaceholderText('搜索知识库')
    fireEvent.change(search, { target: { value: 'Beta' } })
    expect(screen.getByRole('option', { name: 'Beta 政策' })).toBeTruthy()
    expect(screen.queryByRole('option', { name: 'Alpha 手册' })).toBeNull()

    fireEvent.change(search, { target: { value: '' } })
    fireEvent.click(screen.getByRole('option', { name: 'Beta 政策' }))
    expect(onChange).toHaveBeenLastCalledWith([2])

    rerender(<KnowledgeBasePicker options={options} selectedIds={[2]} onChange={onChange} />)
    expect(screen.queryByRole('button', { name: '清空知识库选择' })).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: '选择知识库' }))
    fireEvent.click(screen.getByRole('button', { name: '清空知识库选择' }))
    expect(onChange).toHaveBeenLastCalledWith([])
  })

  it('folds the selected state into the trigger instead of reserving a second summary row', () => {
    render(<KnowledgeBasePicker options={options} selectedIds={[1]} onChange={vi.fn()} />)

    const trigger = screen.getByRole('button', { name: '选择知识库' })
    expect(within(trigger).getByText('Alpha 手册')).toBeTruthy()
    expect(screen.queryByRole('status', { name: '已选知识库' })).toBeNull()
  })
})
