import type { Pinia } from 'pinia'
import { createRouter as createVueRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import AppShell from './shells/AppShell.vue'
import { useAuthStore } from '../features/auth/store/auth'

const LoginPage = () => import('../features/auth/pages/LoginPage.vue')
const AuditLogPage = () => import('../features/audit/pages/AuditLogPage.vue')
const ToolsConsolePage = () => import('../features/agent/pages/ToolsConsolePage.vue')
const ChatWorkspacePage = () => import('../features/chat/pages/ChatWorkspacePage.vue')
const EvaluationConsolePage = () => import('../features/evaluation/pages/EvaluationConsolePage.vue')
const FeedbackConsolePage = () => import('../features/feedback/pages/FeedbackConsolePage.vue')
const GovernanceConsolePage = () => import('../features/governance/pages/GovernanceConsolePage.vue')
const DocumentDetailPage = () => import('../features/knowledge/pages/DocumentDetailPage.vue')
const KnowledgeDetailPage = () => import('../features/knowledge/pages/KnowledgeDetailPage.vue')
const KnowledgeListPage = () => import('../features/knowledge/pages/KnowledgeListPage.vue')
const PermissionDeniedPage = () => import('../features/placeholders/pages/PermissionDeniedPage.vue')
const SettingsPage = () => import('../features/settings/pages/SettingsPage.vue')
const TraceDetailPage = () => import('../features/traces/pages/TraceDetailPage.vue')
const TraceListPage = () => import('../features/traces/pages/TraceListPage.vue')

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    roles?: Array<'OWNER' | 'ADMIN' | 'MEMBER'>
    menuLabel?: string
  }
}

export function createRouter(pinia: Pinia) {
  const router = createVueRouter({
    history: createWebHistory(),
    routes: [
      {
        path: '/login',
        name: 'login',
        component: LoginPage,
        meta: {
          guestOnly: true,
        },
      },
      {
        path: '/',
        component: AppShell,
        meta: {
          requiresAuth: true,
        },
        children: [
          {
            path: '',
            redirect: '/chat',
          },
          {
            path: '/chat',
            name: 'chat',
            component: ChatWorkspacePage,
            meta: {
              requiresAuth: true,
              menuLabel: '对话工作台',
            },
          },
          {
            path: '/chat/:sessionId',
            name: 'chat-session',
            component: ChatWorkspacePage,
            meta: {
              requiresAuth: true,
              menuLabel: '对话工作台',
            },
          },
          {
            path: '/knowledge',
            name: 'knowledge',
            component: KnowledgeListPage,
            meta: {
              requiresAuth: true,
              menuLabel: '知识库',
            },
          },
          {
            path: '/knowledge/:knowledgeBaseId',
            name: 'knowledge-detail',
            component: KnowledgeDetailPage,
            meta: {
              requiresAuth: true,
              menuLabel: '知识库',
            },
          },
          {
            path: '/documents/:documentId',
            name: 'document-detail',
            component: DocumentDetailPage,
            meta: {
              requiresAuth: true,
              menuLabel: '知识库',
            },
          },
          {
            path: '/traces',
            name: 'traces',
            component: TraceListPage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: 'Trace',
            },
          },
          {
            path: '/traces/:exchangeId',
            name: 'trace-detail',
            component: TraceDetailPage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: 'Trace',
            },
          },
          {
            path: '/settings',
            name: 'settings',
            component: SettingsPage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: '设置',
            },
          },
          {
            path: '/tools',
            name: 'tools',
            component: ToolsConsolePage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: 'Tools',
            },
          },
          {
            path: '/governance',
            name: 'governance',
            component: GovernanceConsolePage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: '治理',
            },
          },
          {
            path: '/feedback',
            name: 'feedback',
            component: FeedbackConsolePage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: '反馈',
            },
          },
          {
            path: '/evaluations',
            name: 'evaluations',
            component: EvaluationConsolePage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: '评测',
            },
          },
          {
            path: '/audit-logs',
            name: 'audit-logs',
            component: AuditLogPage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: '审计',
            },
          },
          {
            path: '/forbidden',
            name: 'forbidden',
            component: PermissionDeniedPage,
            meta: {
              requiresAuth: true,
            },
          },
        ],
      },
      {
        path: '/:pathMatch(.*)*',
        redirect: '/chat',
      },
    ],
  })

  router.beforeEach(async (to: RouteLocationNormalized) => {
    const authStore = useAuthStore(pinia)

    if (to.meta.guestOnly) {
      await authStore.ensureSession()
      if (authStore.isAuthenticated) {
        return authStore.pendingRedirect || '/chat'
      }
      return true
    }

    if (!to.meta.requiresAuth) {
      return true
    }

    const sessionReady = await authStore.ensureSession()
    if (!sessionReady) {
      authStore.rememberRedirect(to.fullPath)
      return { name: 'login' }
    }

    if (to.meta.roles?.length) {
      const currentRole = authStore.currentRole
      if (!currentRole || !to.meta.roles.includes(currentRole)) {
        return { name: 'forbidden' }
      }
    }

    return true
  })

  return router
}
