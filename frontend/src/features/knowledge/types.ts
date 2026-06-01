export type KnowledgeBaseStatus = 'draft' | 'published' | 'archived' | 'deleted'
export type KnowledgeBaseVisibility = 'tenant'

export interface PagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

export interface KnowledgeBaseSummary {
  id: number
  name: string
  status: KnowledgeBaseStatus
  visibility: KnowledgeBaseVisibility
}

export interface KnowledgeBaseListItem {
  id: number
  name: string
  status: KnowledgeBaseStatus
  documentCount: number
  updatedAt: string
}

export interface KnowledgeBaseDetail {
  id: number
  name: string
  description: string | null
  visibility: KnowledgeBaseVisibility
  status: KnowledgeBaseStatus
  documentCount: number
}

export interface KnowledgeDocumentListItem {
  id: number
  title: string
  fileType: string
  fileSize: number
  status: string
  chunkCount: number
  updatedAt: string
}

export interface KnowledgeDocumentDetail {
  id: number
  knowledgeBaseId: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  status: string
  chunkCount: number
  errorMessage: string | null
  metadata: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface DocumentChunkItem {
  id: number
  chunkNo: number
  sectionTitle: string | null
  content: string
  charCount: number
  tokenCount: number | null
  metadata: Record<string, unknown>
  createdAt: string
}

export interface DocumentTaskItem {
  id: number
  taskType: string
  status: string
  attemptCount: number
  inputSummary: string | null
  outputSummary: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
  visibility?: KnowledgeBaseVisibility
}

export interface UpdateKnowledgeBaseRequest {
  name?: string
  description?: string
  visibility?: KnowledgeBaseVisibility
  status?: KnowledgeBaseStatus
}

export interface UploadDocumentResponse {
  id: number
  knowledgeBaseId: number
  title: string
  status: string
  taskId: number
}
