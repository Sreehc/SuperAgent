import { useState } from 'react'
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table'
import { CaretDown, CaretUp } from '@phosphor-icons/react'

interface DataTableProps<TData> {
  columns: ColumnDef<TData, unknown>[]
  data: TData[]
  onRowClick?: (row: TData) => void
  rowTestId?: (row: TData) => string | undefined
  emptyLabel?: string
}

/** Headless TanStack Table rendered with the project's `.data-table` styles. */
export function DataTable<TData>({
  columns,
  data,
  onRowClick,
  rowTestId,
  emptyLabel = '暂无数据',
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

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                const canSort = header.column.getCanSort()
                const sorted = header.column.getIsSorted()
                return (
                  <th
                    key={header.id}
                    onClick={canSort ? header.column.getToggleSortingHandler() : undefined}
                    style={{ cursor: canSort ? 'pointer' : undefined, userSelect: 'none' }}
                  >
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {sorted === 'asc' && <CaretUp size={11} aria-hidden="true" />}
                      {sorted === 'desc' && <CaretDown size={11} aria-hidden="true" />}
                    </span>
                  </th>
                )
              })}
            </tr>
          ))}
        </thead>
        <tbody>
          {table.getRowModel().rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>
                <div className="empty-line">{emptyLabel}</div>
              </td>
            </tr>
          ) : (
            table.getRowModel().rows.map((row) => (
              <tr
                key={row.id}
                data-testid={rowTestId?.(row.original)}
                onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                style={{ cursor: onRowClick ? 'pointer' : undefined }}
              >
                {row.getVisibleCells().map((cell) => (
                  <td key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
