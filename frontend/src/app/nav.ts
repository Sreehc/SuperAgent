import type { ComponentType } from 'react'
import {
  Books,
  ChartLineUp,
  ChatCenteredText,
  ClipboardText,
  GearSix,
  Graph,
  type IconProps,
  PuzzlePiece,
  ShieldCheck,
  ThumbsUp,
  UsersThree,
} from '@phosphor-icons/react'
import type { TenantRole } from '@/features/auth/types'

export interface NavItem {
  to: string
  label: string
  icon: ComponentType<IconProps>
  roles: TenantRole[]
}

/** Primary navigation model, shared by the shell rail and the command palette. */
export const NAV_ITEMS: NavItem[] = [
  { to: '/chat', label: '对话', icon: ChatCenteredText, roles: ['OWNER', 'ADMIN', 'MEMBER'] },
  { to: '/knowledge', label: '知识库', icon: Books, roles: ['OWNER', 'ADMIN', 'MEMBER'] },
  { to: '/traces', label: 'Trace', icon: Graph, roles: ['OWNER', 'ADMIN'] },
  { to: '/tools', label: 'Tools', icon: PuzzlePiece, roles: ['OWNER', 'ADMIN'] },
  { to: '/governance', label: '治理', icon: ShieldCheck, roles: ['OWNER', 'ADMIN'] },
  { to: '/feedback', label: '反馈', icon: ThumbsUp, roles: ['OWNER', 'ADMIN'] },
  { to: '/evals', label: '评测', icon: ChartLineUp, roles: ['OWNER', 'ADMIN'] },
  { to: '/members', label: '成员', icon: UsersThree, roles: ['OWNER', 'ADMIN'] },
  { to: '/audit-logs', label: '审计', icon: ClipboardText, roles: ['OWNER', 'ADMIN'] },
  { to: '/settings', label: '设置', icon: GearSix, roles: ['OWNER', 'ADMIN'] },
]
