import type { MessageRole } from '../types'
import { formatShortDateTime } from '@/shared/lib/format'

export function roleLabel(role: MessageRole) {
  if (role === 'assistant') {
    return 'AI'
  }
  if (role === 'system') {
    return '系统'
  }
  return '用户'
}

export function formatChatTime(value: string | null) {
  if (!value) {
    return '刚刚'
  }
  return formatShortDateTime(value)
}
