import { describe, expect, it } from 'vitest'
import { formatChatTime, roleLabel } from './presentation'

describe('chat presentation helpers', () => {
  it('maps message roles to labels', () => {
    expect(roleLabel('assistant')).toBe('AI')
    expect(roleLabel('system')).toBe('系统')
    expect(roleLabel('user')).toBe('用户')
  })

  it('uses a friendly label for messages without a timestamp', () => {
    expect(formatChatTime(null)).toBe('刚刚')
  })
})
