import { apiGet, apiPost, http } from '../../api/http'
import type {
  ChunkingProfileItem,
  CreateKnowledgeBaseRequest,
  DocumentChunkItem,
  DocumentGraphDetail,
  DocumentTaskItem,
  DocumentVersionItem,
  KnowledgeBaseDetail,
  KnowledgeBaseListItem,
  KnowledgeDomainItem,
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
    knowledgeDomainId?: number | null
    chunkingProfileId?: number | null
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
  if (payload.knowledgeDomainId != null) {
    formData.append('knowledgeDomainId', `${payload.knowledgeDomainId}`)
  }
  if (payload.chunkingProfileId != null) {
    formData.append('chunkingProfileId', `${payload.chunkingProfileId}`)
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

export function deleteKnowledgeDocument(documentId: number) {
  return http.delete(`/documents/${documentId}`)
}

export function listKnowledgeDocumentChunks(documentId: number, params?: Record<string, string | number | undefined>) {
  return apiGet<PagedResult<DocumentChunkItem>>(`/documents/${documentId}/chunks${buildQuery(params)}`)
}

export function listKnowledgeDocumentTasks(documentId: number) {
  return apiGet<DocumentTaskItem[]>(`/documents/${documentId}/tasks`)
}

export function reprocessKnowledgeDocument(
  documentId: number,
  payload?: { reason?: string; chunkingProfileId?: number | null },
) {
  const request: { reason?: string; chunkingProfileId?: number } = {}
  if (payload?.reason?.trim()) {
    request.reason = payload.reason.trim()
  }
  if (payload?.chunkingProfileId != null) {
    request.chunkingProfileId = payload.chunkingProfileId
  }

  return apiPost<{ documentId: number; taskId: number; status: string }, { reason?: string; chunkingProfileId?: number }>(
    `/documents/${documentId}/reprocess`,
    request,
  )
}

export function listKnowledgeDomains() {
  return apiGet<KnowledgeDomainItem[]>('/admin/knowledge-domains')
}

export function createKnowledgeDomain(payload: { code: string; name: string; description?: string }) {
  return apiPost<KnowledgeDomainItem, { code: string; name: string; description?: string }>('/admin/knowledge-domains', payload)
}

export function updateKnowledgeDomain(domainId: number, payload: { name?: string; description?: string; status?: string }) {
  return http.patch(`/admin/knowledge-domains/${domainId}`, payload)
}

export function listChunkingProfiles() {
  return apiGet<ChunkingProfileItem[]>('/admin/chunking-profiles')
}

export function createChunkingProfile(payload: {
  code: string
  name: string
  strategy: string
  isDefault?: boolean
  config?: Record<string, unknown>
}) {
  return apiPost<
    ChunkingProfileItem,
    { code: string; name: string; strategy: string; isDefault?: boolean; config?: Record<string, unknown> }
  >('/admin/chunking-profiles', payload)
}

export function updateChunkingProfile(
  profileId: number,
  payload: { name?: string; strategy?: string; isDefault?: boolean; status?: string; config?: Record<string, unknown> },
) {
  return http.patch(`/admin/chunking-profiles/${profileId}`, payload)
}

export function listDocumentVersions(documentId: number) {
  return apiGet<DocumentVersionItem[]>(`/documents/${documentId}/versions`)
}

export function getDocumentGraph(documentId: number) {
  return apiGet<DocumentGraphDetail>(`/documents/${documentId}/graph`)
}

export function rebuildDocumentGraph(documentId: number) {
  return apiPost<DocumentGraphDetail, Record<string, never>>(`/documents/${documentId}/graph/rebuild`, {})
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
