import { describe, expect, it } from 'vitest'
import { buildStreamMessageRequest } from './api'

describe('buildStreamMessageRequest', () => {
  it('keeps legacy knowledgeBaseId for unrestricted and single-base requests', () => {
    expect(
      buildStreamMessageRequest({
        message: 'hello',
        knowledgeBaseIds: [],
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'AUTO',
      }),
    ).toEqual({
      ok: true,
      payload: {
        message: 'hello',
        knowledgeBaseId: null,
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'AUTO',
      },
    })

    expect(
      buildStreamMessageRequest({
        message: 'hello',
        knowledgeBaseIds: [7],
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'RAG_QA',
      }),
    ).toEqual({
      ok: true,
      payload: {
        message: 'hello',
        knowledgeBaseId: 7,
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'RAG_QA',
      },
    })
  })

  it('blocks multiple knowledge bases by default while the backend contract is unconfirmed', () => {
    expect(
      buildStreamMessageRequest({
        message: 'hello',
        knowledgeBaseIds: [1, 2],
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'AUTO',
      }),
    ).toEqual({
      ok: false,
      error: '当前后端暂不支持多知识库会话，请保留 1 个知识库或选择不限定后再发送。',
    })
  })

  it('emits target knowledgeBaseIds when the integration flag is enabled', () => {
    expect(
      buildStreamMessageRequest({
        message: 'hello',
        knowledgeBaseIds: [2, 3, 2],
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'REACT_AGENT',
        multiKnowledgeBaseEnabled: true,
      }),
    ).toEqual({
      ok: true,
      payload: {
        message: 'hello',
        knowledgeBaseIds: [2, 3],
        memoryStrategy: 'SLIDING_WINDOW',
        executionMode: 'REACT_AGENT',
      },
    })
  })
})
