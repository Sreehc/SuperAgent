import { apiGet } from '../../api/http'
import type { ConversationFeedback, FeedbackPagedResult, FeedbackRating } from './types'

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

export function listAdminFeedbacks(params?: {
  page?: number
  pageSize?: number
  rating?: FeedbackRating | ''
}) {
  return apiGet<FeedbackPagedResult<ConversationFeedback>>(`/admin/feedbacks${buildQuery(params)}`)
}
