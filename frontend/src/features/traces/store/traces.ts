import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAdminTrace, listAdminTraces } from '../api'
import type { TraceDetail, TraceListItem } from '../types'

export const useTraceStore = defineStore('traces', () => {
  const traces = ref<TraceListItem[]>([])
  const selectedTrace = ref<TraceDetail | null>(null)
  const loadingList = ref(false)
  const loadingDetail = ref(false)
  const errorMessage = ref('')
  const statusFilter = ref('')
  const modeFilter = ref('')
  const userIdFilter = ref('')
  const page = ref(1)
  const pageSize = ref(20)
  const total = ref(0)

  async function fetchTraces() {
    loadingList.value = true
    errorMessage.value = ''
    try {
      const response = await listAdminTraces({
        page: page.value,
        pageSize: pageSize.value,
        status: statusFilter.value || undefined,
        executionMode: modeFilter.value || undefined,
        userId: userIdFilter.value || undefined,
      })
      traces.value = response.data.items
      total.value = response.data.total
      page.value = response.data.page
      pageSize.value = response.data.pageSize
    } catch {
      errorMessage.value = 'Trace 列表加载失败，请稍后重试。'
    } finally {
      loadingList.value = false
    }
  }

  async function goToPage(nextPage: number) {
    page.value = Math.max(1, nextPage)
    await fetchTraces()
  }

  async function selectTrace(exchangeId: number) {
    loadingDetail.value = true
    errorMessage.value = ''
    try {
      const response = await getAdminTrace(exchangeId)
      selectedTrace.value = response.data
    } catch {
      errorMessage.value = 'Trace 详情加载失败，请稍后重试。'
      throw new Error(errorMessage.value)
    } finally {
      loadingDetail.value = false
    }
  }

  return {
    traces,
    selectedTrace,
    loadingList,
    loadingDetail,
    errorMessage,
    statusFilter,
    modeFilter,
    userIdFilter,
    page,
    pageSize,
    total,
    fetchTraces,
    goToPage,
    selectTrace,
  }
})
