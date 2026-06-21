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

function selectOptions(select: HTMLElement, values: string[]) {
  within(select).getAllByRole('option').forEach((option) => {
    ;(option as HTMLOptionElement).selected = values.includes((option as HTMLOptionElement).value)
  })
  fireEvent.change(select)
}

describe('KnowledgeBasePicker', () => {
  it('supports multi-select, search, clearing, and the unrestricted option', () => {
    const onChange = vi.fn()

    render(<KnowledgeBasePicker options={options} selectedIds={[]} onChange={onChange} />)

    expect(screen.getByText('未限定知识库')).toBeTruthy()

    const search = screen.getByPlaceholderText('搜索知识库')
    fireEvent.change(search, { target: { value: 'Beta' } })

    const select = screen.getByTestId('chat-knowledge-base')
    expect(within(select).getByRole('option', { name: 'Beta 政策' })).toBeTruthy()
    expect(within(select).queryByRole('option', { name: 'Alpha 手册' })).toBeNull()

    fireEvent.change(search, { target: { value: '' } })
    selectOptions(select, ['2', '3'])
    expect(onChange).toHaveBeenLastCalledWith([2, 3])

    selectOptions(select, [''])
    expect(onChange).toHaveBeenLastCalledWith([])
  })

  it('summarizes large selections and exposes a clear action', () => {
    const onChange = vi.fn()

    render(<KnowledgeBasePicker options={options} selectedIds={[1, 2, 3, 4, 5, 6]} onChange={onChange} />)

    const selectedSummary = screen.getByRole('status', { name: '已选知识库' })

    expect(screen.getByText('已选 6 个知识库')).toBeTruthy()
    expect(within(selectedSummary).getByText('Alpha 手册')).toBeTruthy()
    expect(within(selectedSummary).getByText('Epsilon 指南')).toBeTruthy()
    expect(within(selectedSummary).getByText('另 1 个')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '清空知识库选择' }))

    expect(onChange).toHaveBeenCalledWith([])
  })
})
