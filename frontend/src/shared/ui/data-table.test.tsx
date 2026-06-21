import { fireEvent, render, screen } from '@testing-library/react'
import { createColumnHelper } from '@tanstack/react-table'
import { describe, expect, it, vi } from 'vitest'
import { DataTable } from './data-table'

interface TestRow {
  id: number
  name: string
  description: string
  count: number
}

const column = createColumnHelper<TestRow>()

const columns = [
  column.accessor('name', { header: 'Name' }),
  column.accessor('description', { header: 'Description' }),
  column.accessor('count', { header: 'Count', cell: (info) => <span>{info.getValue()}</span> }),
]

const rows: TestRow[] = [
  { id: 1, name: 'Beta', description: 'Second row', count: 2 },
  { id: 2, name: 'Alpha', description: 'First row', count: 1 },
]

function bodyColumnText(container: HTMLElement, columnIndex: number) {
  return Array.from(container.querySelectorAll('tbody tr')).map((row) => row.children[columnIndex]?.textContent)
}

describe('DataTable', () => {
  it('renders sortable headers as buttons and updates aria-sort', () => {
    const { container } = render(<DataTable columns={columns as never} data={rows} />)

    const sortButton = screen.getByRole('button', { name: '排序 Name' })
    const header = sortButton.closest('th')

    expect(header?.getAttribute('aria-sort')).toBe('none')

    fireEvent.click(sortButton)

    expect(header?.getAttribute('aria-sort')).toBe('ascending')
    expect(bodyColumnText(container, 0)).toEqual(['Alpha', 'Beta'])
  })

  it('supports keyboard activation for clickable rows', () => {
    const onRowClick = vi.fn()
    render(
      <DataTable
        columns={columns as never}
        data={rows}
        rowTestId={(row) => `row-${row.id}`}
        onRowClick={onRowClick}
      />,
    )

    const row = screen.getByTestId('row-1')
    expect(row.getAttribute('tabindex')).toBe('0')

    fireEvent.keyDown(row, { key: 'Enter' })
    fireEvent.keyDown(row, { key: ' ' })

    expect(onRowClick).toHaveBeenCalledTimes(2)
    expect(onRowClick).toHaveBeenNthCalledWith(1, rows[0])
  })

  it('renders loading state separately from empty state', () => {
    render(<DataTable columns={columns as never} data={[]} loading emptyLabel="No rows" />)

    expect(screen.getByRole('table').getAttribute('aria-busy')).toBe('true')
    expect(screen.getByText('加载中…')).not.toBeNull()
    expect(screen.queryByText('No rows')).toBeNull()
  })

  it('wraps primitive cell content for long text handling', () => {
    const longText = 'This is a long piece of cell content that should be truncated without changing table layout.'
    render(<DataTable columns={columns as never} data={[{ ...rows[0], description: longText }]} />)

    const cellContent = screen.getByTitle(longText)
    expect(cellContent.className).toContain('data-table__cell-content')
    expect(cellContent.getAttribute('title')).toBe(longText)
  })
})
