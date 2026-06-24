import { useState } from 'react'
import type { KeyboardEvent, ReactNode } from 'react'
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { CaretDown, CaretUp } from '@phosphor-icons/react'
import { TableStateRow } from './status'

interface DataTableProps<TData> {
  columns: ColumnDef<TData, unknown>[]
  data: TData[]
  onRowClick?: (row: TData) => void
  rowTestId?: (row: TData) => string | undefined
  emptyLabel?: string
  loading?: boolean
  loadingLabel?: string
}

function sortAriaValue(sorted: false | 'asc' | 'desc') {
  if (sorted === 'asc') return 'ascending'
  if (sorted === 'desc') return 'descending'
  return 'none'
}

function sortButtonLabel(label: string, sorted: false | 'asc' | 'desc') {
  if (sorted === 'asc') return `按 ${label} 降序排序`
  if (sorted === 'desc') return `清除 ${label} 排序`
  return `排序 ${label}`
}

function headerLabel(header: unknown, fallback: string) {
  return typeof header === 'string' ? header : fallback
}

function primitiveCellValue(value: unknown) {
  return typeof value === 'string' || typeof value === 'number' || value instanceof String || value instanceof Number
}

function renderCellContent(content: ReactNode, rawValue: unknown) {
  const title = primitiveCellValue(rawValue) ? String(rawValue) : primitiveCellValue(content) ? String(content) : undefined
  return (
    <span className="data-table__cell-content" title={title}>
      {content}
    </span>
  )
}

/** Headless TanStack Table rendered with the project's `.data-table` styles. */
export function DataTable<TData>({
  columns,
  data,
  onRowClick,
  rowTestId,
  emptyLabel = '暂无数据',
  loading = false,
  loadingLabel = '加载中…',
}: DataTableProps<TData>) {
  const [sorting, setSorting] = useState<SortingState>([])

  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  })

  const visibleColumnCount = Math.max(table.getAllLeafColumns().length, 1)
  const rows = table.getRowModel().rows

  function handleRowKeyDown(event: KeyboardEvent<HTMLTableRowElement>, row: TData) {
    if (!onRowClick) return

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onRowClick(row)
    }
  }

  return (
    <div className="table-wrap">
      <table className="data-table" aria-busy={loading || undefined}>
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                const canSort = header.column.getCanSort()
                const sorted = header.column.getIsSorted()
                const label = headerLabel(header.column.columnDef.header, header.id)
                return (
                  <th
                    key={header.id}
                    aria-sort={canSort ? sortAriaValue(sorted) : undefined}
                    className={canSort ? 'data-table__sortable-header' : undefined}
                  >
                    {header.isPlaceholder ? null : canSort ? (
                      <button
                        type="button"
                        className="data-table__sort-button"
                        aria-label={sortButtonLabel(label, sorted)}
                        onClick={header.column.getToggleSortingHandler()}
                      >
                        <span>{flexRender(header.column.columnDef.header, header.getContext())}</span>
                        {sorted === 'asc' && <CaretUp className="data-table__sort-icon" size={11} aria-hidden="true" />}
                        {sorted === 'desc' && <CaretDown className="data-table__sort-icon" size={11} aria-hidden="true" />}
                      </button>
                    ) : (
                      <span className="data-table__header-content">
                        {flexRender(header.column.columnDef.header, header.getContext())}
                      </span>
                    )}
                  </th>
                )
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {loading ? (
            <TableStateRow state="loading" colSpan={visibleColumnCount} label={loadingLabel} />
          ) : rows.length === 0 ? (
            <TableStateRow state="empty" colSpan={visibleColumnCount} title={emptyLabel} />
          ) : (
            rows.map((row) => (
              <tr
                key={row.id}
                data-testid={rowTestId?.(row.original)}
                onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                onKeyDown={onRowClick ? (event) => handleRowKeyDown(event, row.original) : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                className={onRowClick ? 'data-table__row--clickable' : undefined}
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>
                    {renderCellContent(flexRender(cell.column.columnDef.cell, cell.getContext()), cell.renderValue())}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
