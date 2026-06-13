import type {
  DisplayMessage,
  DisplayReference,
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
      state.timeline.push({ type: 'agent_step', title: `${event.phase} #${event.stepNo}`, summary: event.summary })
      state.stage = `${event.phase} · ${event.status}`
      break
    }
    case 'tool_start': {
      const event = parsed as StreamToolStartEvent
      state.runId = event.runId
      state.timeline.push({ type: 'tool_start', title: event.toolId, summary: event.summary })
      break
    }
    case 'tool_result': {
      const event = parsed as StreamToolResultEvent
      state.runId = event.runId
      state.timeline.push({ type: 'tool_result', title: `${event.toolId} · ${event.status}`, summary: event.summary })
      break
    }
    case 'checkpoint': {
      const event = parsed as StreamCheckpointEvent
      state.runId = event.runId
      state.timeline.push({
        type: 'checkpoint',
        title: `Checkpoint #${event.checkpointNo}`,
        summary: `${event.phase} · ${event.stable ? 'stable' : 'pending'}`,
      })
      break
    }
    case 'resume': {
      const event = parsed as StreamResumeEvent
      state.runId = event.runId
      state.timeline.push({ type: 'resume', title: '恢复执行', summary: event.status })
      break
    }
    case 'done': {
      const event = parsed as StreamDoneEvent
      state.runId = event.runId ?? state.runId
      const assistantMessage = handlers.findLatestAssistantMessage()
      if (assistantMessage) {
        assistantMessage.status = event.stopped ? 'stopped' : 'success'
      }
      state.stopped = event.stopped
      break
    }
    case 'error': {
      const event = parsed as StreamErrorEvent
      state.error = event.message
      state.runId = event.runId ?? state.runId
      const assistantMessage = handlers.findLatestAssistantMessage()
      if (assistantMessage) {
        assistantMessage.status = 'error'
      }
      break
    }
  }
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
