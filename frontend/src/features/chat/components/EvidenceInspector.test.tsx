import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { useChatStore } from '../store/chat'
import { createEmptyStreamState } from '../store/streamRuntime'
import { EvidenceInspector } from './EvidenceInspector'

function seedInspectorState() {
  useChatStore.setState({
    conversations: [],
    keyword: '',
    selectedReference: null,
    messages: [
      {
        id: 100,
        role: 'assistant',
        content: '退款政策摘要',
        status: 'success',
        createdAt: '2026-06-20T00:00:00Z',
        feedback: null,
        references: [
          {
            ordinal: 1,
            documentId: 22,
            chunkId: 7,
            title: 'Refund Guide',
            quote: '退款需要在 30 天内提交。',
            score: 0.82,
          },
        ],
      },
    ],
    streamState: {
      ...createEmptyStreamState(),
      exchangeId: 77,
      runId: 9,
      stage: 'answer · success',
      recommendations: ['继续追问审批条件'],
      timeline: [
        { type: 'trace_stage', title: 'Trace · retrieve', summary: 'success · 1.2s' },
        { type: 'tool_start', title: '工具 · search', summary: '开始：检索退款规则' },
        { type: 'tool_result', title: '工具 · search', summary: 'success · 找到 3 条证据' },
        { type: 'checkpoint', title: 'Checkpoint #2', summary: 'answer · stable' },
        { type: 'resume', title: '恢复执行', summary: 'running' },
      ],
    },
  })
}

function renderInspector() {
  return render(
    <MemoryRouter>
      <EvidenceInspector />
    </MemoryRouter>,
  )
}

function activateTab(name: RegExp) {
  const tab = screen.getByRole('tab', { name })
  fireEvent.mouseDown(tab, { button: 0, ctrlKey: false })
  fireEvent.click(tab)
}

describe('EvidenceInspector', () => {
  beforeEach(() => {
    seedInspectorState()
  })

  it('groups evidence, trace, tools, checkpoints and recommendations into tabs with links', () => {
    renderInspector()

    expect(screen.getByRole('tab', { name: /证据/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /Trace/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /工具/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /Checkpoint/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /追问/ })).toBeTruthy()

    const documentLink = screen.getByRole('link', { name: '查看文档' })
    expect(documentLink.getAttribute('href')).toBe('/documents/22')

    activateTab(/Trace/)
    expect(screen.getByRole('link', { name: '打开 Trace' }).getAttribute('href')).toBe('/traces/77')
    expect(screen.getByText('Trace · retrieve')).toBeTruthy()

    activateTab(/工具/)
    expect(screen.getAllByText('工具 · search')).toHaveLength(2)
    expect(screen.getByText('success · 找到 3 条证据')).toBeTruthy()

    activateTab(/Checkpoint/)
    expect(screen.getByText('Checkpoint #2')).toBeTruthy()
    expect(screen.getByText('恢复执行')).toBeTruthy()

    activateTab(/追问/)
    expect(screen.getByRole('button', { name: '继续追问审批条件' })).toBeTruthy()
  })
})
