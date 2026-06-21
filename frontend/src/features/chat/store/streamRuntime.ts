import type {
  DisplayMessage,
  DisplayReference,
  RunTimelineItem,
  StreamAgentStepEvent,
  StreamCheckpointEvent,
  StreamDeltaEvent,
  StreamDoneEvent,
  StreamErrorEvent,
  StreamRecommendationEvent,
  StreamReferenceEvent,
  StreamResumeEvent,
  StreamStartEvent,
  StreamState,
  StreamToolResultEvent,
  StreamToolStartEvent,
  StreamTraceStageEvent,
} from '../types'
import { formatDurationMs } from '../../../shared/lib/format'

export interface StreamEventHandlers {
  ensureAssistantMessage: () => DisplayMessage
  attachReference: (reference: DisplayReference) => void
  findLatestAssistantMessage: () => DisplayMessage | undefined
}

export interface SseParserState {
  offset: number
  buffer: string
}

export function createEmptyStreamState(): StreamState {
  return {
    exchangeId: null,
    runId: null,
    stage: null,
    recommendations: [],
    error: '',
    completed: false,
    stopped: false,
    timeline: [],
  }
}

export function applyStreamEvent(
  state: StreamState,
  eventName: string,
  dataLine: string,
  handlers: StreamEventHandlers,
) {
  const parsed: unknown = JSON.parse(dataLine)
  switch (eventName) {
    case 'start': {
      const event = parsed as StreamStartEvent
      state.exchangeId = event.exchangeId
      break
    }
    case 'trace_stage': {
      const event = parsed as StreamTraceStageEvent
      state.stage = `${event.stage} · ${event.status}`
      appendExecutionSummary(state, handlers, {
        type: 'trace_stage',
        title: `Trace · ${event.stage}`,
        summary: joinSummaryParts([event.status, formatDurationMs(event.durationMs)]),
      })
      break
    }
    case 'delta': {
      const event = parsed as StreamDeltaEvent
      handlers.ensureAssistantMessage().content += event.text
      break
    }
    case 'reference': {
      const event = parsed as StreamReferenceEvent
      handlers.attachReference(event)
      break
    }
    case 'recommendation': {
      const event = parsed as StreamRecommendationEvent
      state.recommendations = event.questions
      break
    }
    case 'agent_step': {
      const event = parsed as StreamAgentStepEvent
      state.runId = event.runId
      appendExecutionSummary(state, handlers, {
        type: 'agent_step',
        title: `Agent · ${event.phase} #${event.stepNo}`,
        summary: joinSummaryParts([event.status, event.summary]),
      })
      state.stage = `${event.phase} · ${event.status}`
      break
    }
    case 'tool_start': {
      const event = parsed as StreamToolStartEvent
      state.runId = event.runId
      appendExecutionSummary(state, handlers, {
        type: 'tool_start',
        title: `工具 · ${event.toolId}`,
        summary: event.summary ? `开始：${event.summary}` : '开始调用工具',
      })
      break
    }
    case 'tool_result': {
      const event = parsed as StreamToolResultEvent
      state.runId = event.runId
      appendExecutionSummary(state, handlers, {
        type: 'tool_result',
        title: `工具 · ${event.toolId}`,
        summary: joinSummaryParts([event.status, event.summary]),
      })
      break
    }
    case 'checkpoint': {
      const event = parsed as StreamCheckpointEvent
      state.runId = event.runId
      appendExecutionSummary(state, handlers, {
        type: 'checkpoint',
        title: `Checkpoint #${event.checkpointNo}`,
        summary: `${event.phase} · ${event.stable ? 'stable' : 'pending'}`,
      })
      break
    }
    case 'resume': {
      const event = parsed as StreamResumeEvent
      state.runId = event.runId
      appendExecutionSummary(state, handlers, { type: 'resume', title: '恢复执行', summary: event.status })
      break
    }
    case 'done': {
      const event = parsed as StreamDoneEvent
      state.runId = event.runId ?? state.runId
      const assistantMessage = handlers.findLatestAssistantMessage()
      if (assistantMessage) {
        assistantMessage.status = event.stopped ? 'stopped' : 'success'
      }
      state.completed = true
      state.stopped = event.stopped
      break
    }
    case 'error': {
      const event = parsed as StreamErrorEvent
      state.error = event.message
      state.exchangeId = event.exchangeId ?? state.exchangeId
      state.runId = event.runId ?? state.runId
      const assistantMessage = handlers.findLatestAssistantMessage()
      if (assistantMessage) {
        assistantMessage.status = 'error'
      }
      break
    }
  }
}

function appendExecutionSummary(state: StreamState, handlers: StreamEventHandlers, item: RunTimelineItem) {
  const normalized: RunTimelineItem = {
    ...item,
    summary: truncateSummary(item.summary),
  }
  const last = state.timeline[state.timeline.length - 1]
  if (last && last.type === normalized.type && last.title === normalized.title && last.summary === normalized.summary) {
    return
  }
  state.timeline.push(normalized)
  handlers.ensureAssistantMessage()
}

function joinSummaryParts(parts: Array<string | null | undefined>) {
  return parts.filter((part): part is string => Boolean(part)).join(' · ')
}

function truncateSummary(value: string) {
  const normalized = value.trim()
  return normalized.length > 160 ? `${normalized.slice(0, 157)}...` : normalized
}

export function consumeSseText(
  rawText: string,
  parser: SseParserState,
  onEvent: (eventName: string, data: string) => void,
) {
  parser.buffer += rawText.slice(parser.offset)
  parser.offset = rawText.length

  const blocks = parser.buffer.split('\n\n')
  parser.buffer = blocks.pop() ?? ''
  for (const block of blocks) {
    let eventName = ''
    let dataValue = ''
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice('event:'.length).trim()
      } else if (line.startsWith('data:')) {
        dataValue = line.slice('data:'.length).trim()
      }
    }
    if (eventName && dataValue) {
      onEvent(eventName, dataValue)
    }
  }
}
