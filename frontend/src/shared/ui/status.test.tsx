import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EmptyState, ErrorState, LoadingState, TableStateRow } from './status'

describe('LoadingState', () => {
  it('announces loading and renders skeleton rows', () => {
    render(<LoadingState label="正在加载知识库" lines={4} />)

    expect(screen.getByRole('status', { name: '正在加载知识库' })).toBeTruthy()
    expect(screen.getAllByTestId('skeleton-line')).toHaveLength(4)
  })
})

describe('EmptyState', () => {
  it('renders title, description, and action', () => {
    render(
      <EmptyState
        title="暂无文档"
        description="上传文档后会显示解析状态。"
        action={<button type="button">上传文档</button>}
      />,
    )

    expect(screen.getByRole('status', { name: '暂无文档' })).toBeTruthy()
    expect(screen.getByText('上传文档后会显示解析状态。')).toBeTruthy()
    expect(screen.getByRole('button', { name: '上传文档' })).toBeTruthy()
  })
})

describe('ErrorState', () => {
  it('renders alert content with an optional action', () => {
    render(
      <ErrorState
        title="加载失败"
        description="网络连接中断。"
        action={<button type="button">重试</button>}
      />,
    )

    expect(screen.getByRole('alert')).toBeTruthy()
    expect(screen.getByText('加载失败')).toBeTruthy()
    expect(screen.getByText('网络连接中断。')).toBeTruthy()
    expect(screen.getByRole('button', { name: '重试' })).toBeTruthy()
  })
})

describe('TableStateRow', () => {
  it('renders a table-level loading skeleton with the expected colspan', () => {
    render(
      <table>
        <tbody>
          <TableStateRow state="loading" colSpan={3} label="正在加载运行记录" skeletonRows={2} />
        </tbody>
      </table>,
    )

    expect(screen.getByRole('cell').getAttribute('colspan')).toBe('3')
    expect(screen.getByRole('status', { name: '正在加载运行记录' })).toBeTruthy()
    expect(screen.getAllByTestId('table-skeleton-row')).toHaveLength(2)
  })

  it('renders empty and error table states', () => {
    const { rerender } = render(
      <table>
        <tbody>
          <TableStateRow state="empty" colSpan={2} title="暂无成员" />
        </tbody>
      </table>,
    )

    expect(screen.getByRole('status', { name: '暂无成员' })).toBeTruthy()

    rerender(
      <table>
        <tbody>
          <TableStateRow state="error" colSpan={2} title="成员加载失败" description="请稍后重试。" />
        </tbody>
      </table>,
    )

    expect(screen.getByRole('alert')).toBeTruthy()
    expect(screen.getByText('请稍后重试。')).toBeTruthy()
  })
})
