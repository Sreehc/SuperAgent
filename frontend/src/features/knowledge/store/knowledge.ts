import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  createKnowledgeBase,
  getKnowledgeBase,
  listKnowledgeBases,
  listKnowledgeDocuments,
  updateKnowledgeBase,
  uploadKnowledgeDocument,
} from '../api'
import type {
  CreateKnowledgeBaseRequest,
  KnowledgeBaseDetail,
  KnowledgeBaseListItem,
  KnowledgeDocumentListItem,
  KnowledgeBaseStatus,
} from '../types'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const knowledgeBases = ref<KnowledgeBaseListItem[]>([])
  const selectedKnowledgeBase = ref<KnowledgeBaseDetail | null>(null)
  const documents = ref<KnowledgeDocumentListItem[]>([])
  const loadingKnowledgeBases = ref(false)
  const loadingDocuments = ref(false)
  const creatingKnowledgeBase = ref(false)
  const uploadingDocument = ref(false)
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

  async function uploadDocument(payload: { file: File; title?: string; category?: string; tags?: string }) {
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
    documents,
    loadingKnowledgeBases,
    loadingDocuments,
    creatingKnowledgeBase,
    uploadingDocument,
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
    refreshDocuments,
    publishKnowledgeBase,
    archiveKnowledgeBase,
    uploadDocument,
  }
})
