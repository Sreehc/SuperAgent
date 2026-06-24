import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { RunTimelineItem } from '../types'
import { ExecutionSummary } from './ExecutionSummary'

const items: RunTimelineItem[] = [
  { type: 'trace_stage', title: 'Trace · prepare', summary: 'running · 100ms' },
  { type: 'agent_step', title: 'Agent · plan #1', summary: '规划执行步骤' },
  { type: 'tool_start', title: '工具 · search', summary: '开始：检索政策' },
  { type: 'tool_result', title: '工具 · search', summary: 'success · 找到 3 条证据' },
  { type: 'checkpoint', title: 'Checkpoint #1', summary: 'answer · stable' },
]

describe('ExecutionSummary', () => {
  it('renders a compact inline summary and collapses older events', () => {
    render(<ExecutionSummary items={items} limit={3} />)

    expect(screen.getByLabelText('执行摘要')).toBeTruthy()
    expect(screen.getByText('还有 2 个执行事件')).toBeTruthy()
    expect(screen.queryByText('Trace · prepare')).toBeNull()
    expect(screen.getAllByText('工具 · search')).toHaveLength(2)
    expect(screen.getByText('Checkpoint #1')).toBeTruthy()
  })
})
