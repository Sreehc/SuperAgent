import { apiGet, apiPost, http } from '../../api/http'
import type {
  CreateKnowledgeBaseRequest,
  DocumentChunkItem,
  DocumentTaskItem,
  KnowledgeBaseDetail,
  KnowledgeBaseListItem,
  KnowledgeDocumentDetail,
  PagedResult,
  UpdateKnowledgeBaseRequest,
  UploadDocumentResponse,
  KnowledgeDocumentListItem,
  KnowledgeBaseSummary,
} from './types'

export function listKnowledgeBases(params?: Record<string, string | number | undefined>) {
  return apiGet<PagedResult<KnowledgeBaseListItem>>(`/knowledge-bases${buildQuery(params)}`)
}

export function createKnowledgeBase(payload: CreateKnowledgeBaseRequest) {
  return apiPost<KnowledgeBaseSummary, CreateKnowledgeBaseRequest>('/knowledge-bases', payload)
}

export function getKnowledgeBase(knowledgeBaseId: number) {
  return apiGet<KnowledgeBaseDetail>(`/knowledge-bases/${knowledgeBaseId}`)
}

export function updateKnowledgeBase(knowledgeBaseId: number, payload: UpdateKnowledgeBaseRequest) {
  return http.patch(`/knowledge-bases/${knowledgeBaseId}`, payload)
}

export function deleteKnowledgeBase(knowledgeBaseId: number) {
  return http.delete(`/knowledge-bases/${knowledgeBaseId}`)
}

export function listKnowledgeDocuments(knowledgeBaseId: number, params?: Record<string, string | number | undefined>) {
  return apiGet<PagedResult<KnowledgeDocumentListItem>>(`/knowledge-bases/${knowledgeBaseId}/documents${buildQuery(params)}`)
}

export async function uploadKnowledgeDocument(
  knowledgeBaseId: number,
  payload: {
    file: File
    title?: string
    category?: string
    tags?: string
  },
) {
  const formData = new FormData()
  formData.append('file', payload.file)
  if (payload.title?.trim()) {
    formData.append('title', payload.title.trim())
  }
  if (payload.category?.trim()) {
    formData.append('category', payload.category.trim())
  }
  if (payload.tags?.trim()) {
    formData.append('tags', payload.tags.trim())
  }

  const response = await http.post<{ success: boolean; code: string; message: string; data: UploadDocumentResponse; traceId: string }>(
    `/knowledge-bases/${knowledgeBaseId}/documents`,
    formData,
  )
  return response.data
}

export function getKnowledgeDocument(documentId: number) {
  return apiGet<KnowledgeDocumentDetail>(`/documents/${documentId}`)
}

export function listKnowledgeDocumentChunks(documentId: number, params?: Record<string, string | number | undefined>) {
  return apiGet<PagedResult<DocumentChunkItem>>(`/documents/${documentId}/chunks${buildQuery(params)}`)
}

export function listKnowledgeDocumentTasks(documentId: number) {
  return apiGet<DocumentTaskItem[]>(`/documents/${documentId}/tasks`)
}

export function reprocessKnowledgeDocument(documentId: number, reason?: string) {
  return apiPost<{ documentId: number; taskId: number; status: string }, { reason?: string }>(
    `/documents/${documentId}/reprocess`,
    reason ? { reason } : {},
  )
}

function buildQuery(params?: Record<string, string | number | undefined>) {
  if (!params) {
    return ''
  }

  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}`.trim() !== '') {
      query.set(key, `${value}`)
    }
  })
  const serialized = query.toString()
  return serialized ? `?${serialized}` : ''
}
