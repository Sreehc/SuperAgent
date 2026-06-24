import { describe, expect, it, vi } from 'vitest'
import type { DisplayMessage, DisplayReference } from '../types'
import { applyStreamEvent, consumeSseText, createEmptyStreamState } from './streamRuntime'

function createHarness() {
  const assistant: DisplayMessage = {
    id: 1,
    role: 'assistant',
    content: '',
    status: 'streaming',
    createdAt: '2026-06-13T00:00:00Z',
    references: [],
    feedback: null,
  }
  const references: DisplayReference[] = []
  return {
    assistant,
    references,
    handlers: {
      ensureAssistantMessage: () => assistant,
      attachReference: (reference: DisplayReference) => references.push(reference),
      findLatestAssistantMessage: () => assistant,
    },
  }
}

describe('stream runtime', () => {
  it('consumes incremental SSE text without replaying prior events', () => {
    const parser = { offset: 0, buffer: '' }
    const onEvent = vi.fn()

    consumeSseText('event: delta\ndata: {"text":"A"}\n\n', parser, onEvent)
    consumeSseText('event: delta\ndata: {"text":"A"}\n\nevent: done\ndata: {"stopped":false}\n\n', parser, onEvent)

    expect(onEvent).toHaveBeenCalledTimes(2)
    expect(onEvent).toHaveBeenLastCalledWith('done', '{"stopped":false}')
  })

  it('updates messages, references and timeline from stream events', () => {
    const state = createEmptyStreamState()
    const harness = createHarness()

    applyStreamEvent(state, 'delta', '{"text":"hello"}', harness.handlers)
    applyStreamEvent(state, 'reference', '{"ordinal":1,"documentId":2,"chunkId":3,"title":"Doc","quote":"Quote","score":0.8}', harness.handlers)
    applyStreamEvent(state, 'tool_start', '{"runId":9,"toolId":"search","stepNo":1,"summary":"Searching"}', harness.handlers)
    applyStreamEvent(state, 'done', '{"runId":9,"stopped":false}', harness.handlers)

    expect(harness.assistant.content).toBe('hello')
    expect(harness.assistant.status).toBe('success')
    expect(harness.references).toHaveLength(1)
    expect(state.runId).toBe(9)
    expect(state.timeline[0]).toMatchObject({ type: 'tool_start', title: '工具 · search' })
  })

  it('maps execution SSE events to compact timeline summaries', () => {
    const state = createEmptyStreamState()
    const harness = createHarness()
    const ensureAssistantMessage = vi.fn(harness.handlers.ensureAssistantMessage)

    applyStreamEvent(state, 'trace_stage', '{"stage":"retrieve","status":"running","durationMs":1234}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })
    applyStreamEvent(state, 'trace_stage', '{"stage":"retrieve","status":"running","durationMs":1234}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })
    applyStreamEvent(state, 'agent_step', '{"runId":9,"stepNo":1,"phase":"plan","status":"success","summary":"规划工具调用"}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })
    applyStreamEvent(state, 'tool_start', '{"runId":9,"toolId":"search","stepNo":2,"summary":"检索退款规则"}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })
    applyStreamEvent(state, 'tool_result', '{"runId":9,"toolId":"search","status":"success","summary":"找到 3 条证据"}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })
    applyStreamEvent(state, 'checkpoint', '{"runId":9,"checkpointNo":2,"phase":"answer","stable":true}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })
    applyStreamEvent(state, 'resume', '{"runId":9,"status":"running"}', {
      ...harness.handlers,
      ensureAssistantMessage,
    })

    expect(state.timeline.map((item) => item.type)).toEqual([
      'trace_stage',
      'agent_step',
      'tool_start',
      'tool_result',
      'checkpoint',
      'resume',
    ])
    expect(state.timeline[0]).toMatchObject({ title: 'Trace · retrieve', summary: 'running · 1.2s' })
    expect(state.timeline[3]).toMatchObject({ title: '工具 · search', summary: 'success · 找到 3 条证据' })
    expect(ensureAssistantMessage).toHaveBeenCalledTimes(6)
  })

  it('maps stream error events to the inspector state and failed assistant message', () => {
    const state = createEmptyStreamState()
    const harness = createHarness()

    applyStreamEvent(state, 'error', '{"code":"MODEL_TIMEOUT","message":"模型调用超时","exchangeId":5001,"runId":9}', harness.handlers)

    expect(state.error).toBe('模型调用超时')
    expect(state.exchangeId).toBe(5001)
    expect(state.runId).toBe(9)
    expect(harness.assistant.status).toBe('error')
  })
})
