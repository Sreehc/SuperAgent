import type { MessageRole } from '../types'

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
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
