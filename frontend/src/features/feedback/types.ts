export type FeedbackRating = 'up' | 'down' | 'correction'

export interface ConversationFeedback {
  id: number
  sessionId: number
  exchangeId: number | null
  messageId: number
  actorUserId: number
  rating: FeedbackRating
  comment: string | null
  correction: string | null
  metadata: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface FeedbackPagedResult<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}
