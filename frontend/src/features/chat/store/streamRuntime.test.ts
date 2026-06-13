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
    expect(state.timeline[0]).toMatchObject({ type: 'tool_start', title: 'search' })
  })
})
