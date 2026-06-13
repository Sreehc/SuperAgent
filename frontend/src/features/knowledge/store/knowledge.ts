import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  getDocumentGraph,
  getKnowledgeDocument,
  getKnowledgeBase,
  listChunkingProfiles,
  listDocumentVersions,
  listKnowledgeDomains,
  listKnowledgeBases,
  listKnowledgeDocumentChunks,
  listKnowledgeDocumentTasks,
  listKnowledgeDocuments,
  rebuildDocumentGraph,
  reprocessKnowledgeDocument,
  updateKnowledgeDocument,
  updateKnowledgeBase,
  uploadKnowledgeDocument,
  uploadKnowledgeDocumentsBatch,
} from '../api'
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
  KnowledgeDocumentListItem,
  KnowledgeBaseStatus,
} from '../types'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const knowledgeBases = ref<KnowledgeBaseListItem[]>([])
  const selectedKnowledgeBase = ref<KnowledgeBaseDetail | null>(null)
  const selectedDocument = ref<KnowledgeDocumentDetail | null>(null)
  const documents = ref<KnowledgeDocumentListItem[]>([])
  const documentChunks = ref<DocumentChunkItem[]>([])
  const documentTasks = ref<DocumentTaskItem[]>([])
  const knowledgeDomains = ref<KnowledgeDomainItem[]>([])
  const chunkingProfiles = ref<ChunkingProfileItem[]>([])
  const documentVersions = ref<DocumentVersionItem[]>([])
  const documentGraph = ref<DocumentGraphDetail | null>(null)
  const loadingKnowledgeBases = ref(false)
  const loadingDocuments = ref(false)
  const loadingDocumentDetail = ref(false)
  const loadingGovernanceOptions = ref(false)
  const loadingDocumentGraph = ref(false)
  const creatingKnowledgeBase = ref(false)
  const uploadingDocument = ref(false)
  const reprocessingDocument = ref(false)
  const rebuildingDocumentGraph = ref(false)
  const deletingDocument = ref(false)
  const deletingKnowledgeBase = ref(false)
  const savingKnowledgeBase = ref(false)
  const savingDocumentMetadata = ref(false)
  const errorMessage = ref('')
  const keyword = ref('')
  const statusFilter = ref('')
  const documentStatusFilter = ref('')
  const documentTypeFilter = ref('')
  const tagFilter = ref('')

  const isEmpty = computed(() => !loadingKnowledgeBases.value && knowledgeBases.value.length === 0)

  async function fetchKnowledgeBases() {
    loadingKnowledgeBases.value = true
    errorMessage.value = ''
    try {
      const response = await listKnowledgeBases({
        keyword: keyword.value || undefined,
        status: statusFilter.value || undefined,
      })
      knowledgeBases.value = response.data.items
    } catch {
      errorMessage.value = '知识库列表加载失败，请稍后重试。'
    } finally {
      loadingKnowledgeBases.value = false
    }
  }

  async function createBase(payload: CreateKnowledgeBaseRequest) {
    creatingKnowledgeBase.value = true
    errorMessage.value = ''
    try {
      const response = await createKnowledgeBase(payload)
      await fetchKnowledgeBases()
      await selectKnowledgeBase(response.data.id)
    } catch {
      errorMessage.value = '知识库创建失败，请检查输入后重试。'
      throw new Error(errorMessage.value)
    } finally {
      creatingKnowledgeBase.value = false
    }
  }

  async function selectKnowledgeBase(knowledgeBaseId: number) {
    loadingDocuments.value = true
    errorMessage.value = ''
    try {
      const [knowledgeBaseResponse, documentsResponse] = await Promise.all([
        getKnowledgeBase(knowledgeBaseId),
        listKnowledgeDocuments(knowledgeBaseId, {
          status: documentStatusFilter.value || undefined,
          fileType: documentTypeFilter.value || undefined,
          tag: tagFilter.value || undefined,
        }),
      ])
      selectedKnowledgeBase.value = knowledgeBaseResponse.data
      documents.value = documentsResponse.data.items
    } catch {
      errorMessage.value = '知识库详情加载失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      loadingDocuments.value = false
    }
  }

  async function refreshDocuments() {
    if (!selectedKnowledgeBase.value) {
      return
    }
    loadingDocuments.value = true
    errorMessage.value = ''
    try {
      const response = await listKnowledgeDocuments(selectedKnowledgeBase.value.id, {
        status: documentStatusFilter.value || undefined,
        fileType: documentTypeFilter.value || undefined,
        tag: tagFilter.value || undefined,
      })
      documents.value = response.data.items
    } catch {
      errorMessage.value = '文档列表加载失败，请稍后重试。'
    } finally {
      loadingDocuments.value = false
    }
  }

  async function selectDocument(documentId: number) {
    loadingDocumentDetail.value = true
    loadingDocumentGraph.value = true
    errorMessage.value = ''
    try {
      const [documentResponse, chunkResponse, taskResponse, versionResponse, graphResponse] = await Promise.allSettled([
        getKnowledgeDocument(documentId),
        listKnowledgeDocumentChunks(documentId, { pageSize: 20 }),
        listKnowledgeDocumentTasks(documentId),
        listDocumentVersions(documentId),
        getDocumentGraph(documentId),
      ])

      if (documentResponse.status !== 'fulfilled' || chunkResponse.status !== 'fulfilled' || taskResponse.status !== 'fulfilled') {
        throw new Error('document detail load failed')
      }

      selectedDocument.value = documentResponse.value.data
      documentChunks.value = chunkResponse.value.data.items
      documentTasks.value = taskResponse.value.data
      documentVersions.value = versionResponse.status === 'fulfilled' ? versionResponse.value.data : []
      documentGraph.value = graphResponse.status === 'fulfilled' ? graphResponse.value.data : null
    } catch {
      errorMessage.value = '文档详情加载失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      loadingDocumentDetail.value = false
      loadingDocumentGraph.value = false
    }
  }

  async function reprocessDocument(payload?: { reason?: string; chunkingProfileId?: number | null }) {
    if (!selectedDocument.value) {
      return
    }
    reprocessingDocument.value = true
    errorMessage.value = ''
    try {
      await reprocessKnowledgeDocument(selectedDocument.value.id, payload)
      await selectDocument(selectedDocument.value.id)
    } catch {
      errorMessage.value = '文档重处理触发失败，请稍后重试。'
    } finally {
      reprocessingDocument.value = false
    }
  }

  async function removeCurrentDocument() {
    if (!selectedDocument.value) {
      return false
    }
    deletingDocument.value = true
    errorMessage.value = ''
    try {
      const knowledgeBaseId = selectedDocument.value.knowledgeBaseId
      await deleteKnowledgeDocument(selectedDocument.value.id)
      selectedDocument.value = null
      documentChunks.value = []
      documentTasks.value = []
      documentVersions.value = []
      documentGraph.value = null
      if (selectedKnowledgeBase.value?.id === knowledgeBaseId) {
        await refreshDocuments()
      }
      return true
    } catch {
      errorMessage.value = '文档删除失败，请稍后重试。'
      return false
    } finally {
      deletingDocument.value = false
    }
  }

  async function publishKnowledgeBase() {
    if (!selectedKnowledgeBase.value) {
      return
    }
    await updateStatus('published')
  }

  async function archiveKnowledgeBase() {
    if (!selectedKnowledgeBase.value) {
      return
    }
    await updateStatus('archived')
  }

  async function saveKnowledgeBase(payload: { name?: string; description?: string }) {
    if (!selectedKnowledgeBase.value) {
      return
    }
    savingKnowledgeBase.value = true
    errorMessage.value = ''
    try {
      await updateKnowledgeBase(selectedKnowledgeBase.value.id, payload)
      await Promise.all([fetchKnowledgeBases(), selectKnowledgeBase(selectedKnowledgeBase.value.id)])
    } catch {
      errorMessage.value = '知识库保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingKnowledgeBase.value = false
    }
  }

  async function removeKnowledgeBase() {
    if (!selectedKnowledgeBase.value) {
      return false
    }
    deletingKnowledgeBase.value = true
    errorMessage.value = ''
    try {
      await deleteKnowledgeBase(selectedKnowledgeBase.value.id)
      selectedKnowledgeBase.value = null
      documents.value = []
      await fetchKnowledgeBases()
      return true
    } catch {
      errorMessage.value = '知识库删除失败，请稍后重试。'
      return false
    } finally {
      deletingKnowledgeBase.value = false
    }
  }

  async function uploadDocument(payload: {
    file: File
    title?: string
    category?: string
    tags?: string
    knowledgeDomainId?: number | null
    chunkingProfileId?: number | null
  }) {
    if (!selectedKnowledgeBase.value) {
      return
    }
    uploadingDocument.value = true
    errorMessage.value = ''
    try {
      await uploadKnowledgeDocument(selectedKnowledgeBase.value.id, payload)
      await Promise.all([fetchKnowledgeBases(), refreshDocuments(), selectKnowledgeBase(selectedKnowledgeBase.value.id)])
    } catch {
      errorMessage.value = '文档上传失败，请检查文件类型和大小。'
      throw new Error(errorMessage.value)
    } finally {
      uploadingDocument.value = false
    }
  }

  async function uploadDocuments(payload: {
    files: File[]
    category?: string
    tags?: string
    knowledgeDomainId?: number | null
    chunkingProfileId?: number | null
  }) {
    if (!selectedKnowledgeBase.value || payload.files.length === 0) {
      return
    }
    uploadingDocument.value = true
    errorMessage.value = ''
    try {
      if (payload.files.length === 1) {
        await uploadKnowledgeDocument(selectedKnowledgeBase.value.id, {
          file: payload.files[0],
          category: payload.category,
          tags: payload.tags,
          knowledgeDomainId: payload.knowledgeDomainId,
          chunkingProfileId: payload.chunkingProfileId,
        })
      } else {
        await uploadKnowledgeDocumentsBatch(selectedKnowledgeBase.value.id, payload)
      }
      await Promise.all([fetchKnowledgeBases(), refreshDocuments(), selectKnowledgeBase(selectedKnowledgeBase.value.id)])
    } catch {
      errorMessage.value = '文档上传失败，请检查文件类型和大小。'
      throw new Error(errorMessage.value)
    } finally {
      uploadingDocument.value = false
    }
  }

  async function saveDocumentMetadata(payload: {
    title?: string
    category?: string
    tags?: string
    knowledgeDomainId?: number | null
    chunkingProfileId?: number | null
  }) {
    if (!selectedDocument.value) {
      return
    }
    savingDocumentMetadata.value = true
    errorMessage.value = ''
    try {
      const response = await updateKnowledgeDocument(selectedDocument.value.id, payload)
      selectedDocument.value = response.data.data
      await refreshDocuments()
    } catch {
      errorMessage.value = '文档元数据保存失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      savingDocumentMetadata.value = false
    }
  }

  async function fetchGovernanceOptions(force = false) {
    if (!force && knowledgeDomains.value.length > 0 && chunkingProfiles.value.length > 0) {
      return
    }
    loadingGovernanceOptions.value = true
    errorMessage.value = ''
    try {
      const [domainResponse, profileResponse] = await Promise.all([
        listKnowledgeDomains(),
        listChunkingProfiles(),
      ])
      knowledgeDomains.value = domainResponse.data
      chunkingProfiles.value = profileResponse.data
    } catch {
      errorMessage.value = '治理配置加载失败，请稍后重试。'
    } finally {
      loadingGovernanceOptions.value = false
    }
  }

  async function rebuildCurrentDocumentGraph() {
    if (!selectedDocument.value) {
      return
    }
    rebuildingDocumentGraph.value = true
    loadingDocumentGraph.value = true
    errorMessage.value = ''
    try {
      const response = await rebuildDocumentGraph(selectedDocument.value.id)
      documentGraph.value = response.data
      const versions = await listDocumentVersions(selectedDocument.value.id)
      documentVersions.value = versions.data
      const detail = await getKnowledgeDocument(selectedDocument.value.id)
      selectedDocument.value = detail.data
    } catch {
      errorMessage.value = '图谱重建失败，请稍后重试。'
    } finally {
      rebuildingDocumentGraph.value = false
      loadingDocumentGraph.value = false
    }
  }

  async function updateStatus(status: KnowledgeBaseStatus) {
    if (!selectedKnowledgeBase.value) {
      return
    }
    errorMessage.value = ''
    try {
      await updateKnowledgeBase(selectedKnowledgeBase.value.id, { status })
      await Promise.all([fetchKnowledgeBases(), selectKnowledgeBase(selectedKnowledgeBase.value.id)])
    } catch {
      errorMessage.value = '知识库状态更新失败，请稍后重试。'
    }
  }

  return {
    knowledgeBases,
    selectedKnowledgeBase,
    selectedDocument,
    documents,
    documentChunks,
    documentTasks,
    knowledgeDomains,
    chunkingProfiles,
    documentVersions,
    documentGraph,
    loadingKnowledgeBases,
    loadingDocuments,
    loadingDocumentDetail,
    loadingGovernanceOptions,
    loadingDocumentGraph,
    creatingKnowledgeBase,
    uploadingDocument,
    reprocessingDocument,
    rebuildingDocumentGraph,
    deletingDocument,
    deletingKnowledgeBase,
    savingKnowledgeBase,
    savingDocumentMetadata,
    errorMessage,
    keyword,
    statusFilter,
    documentStatusFilter,
    documentTypeFilter,
    tagFilter,
    isEmpty,
    fetchKnowledgeBases,
    createBase,
    selectKnowledgeBase,
    selectDocument,
    refreshDocuments,
    publishKnowledgeBase,
    archiveKnowledgeBase,
    saveKnowledgeBase,
    removeKnowledgeBase,
    uploadDocument,
    uploadDocuments,
    saveDocumentMetadata,
    reprocessDocument,
    removeCurrentDocument,
    fetchGovernanceOptions,
    rebuildCurrentDocumentGraph,
  }
})
