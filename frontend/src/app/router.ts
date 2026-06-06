import type { Pinia } from 'pinia'
import { createRouter as createVueRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import AppShell from './shells/AppShell.vue'
import { useAuthStore } from '../features/auth/store/auth'
import { LoginPage } from '../features/auth/pages'
import { ToolsConsolePage } from '../features/agent/pages'
import { ChatWorkspacePage } from '../features/chat/pages'
import { GovernanceConsolePage } from '../features/governance/pages'
import { DocumentDetailPage, KnowledgeDetailPage, KnowledgeListPage } from '../features/knowledge/pages'
import { PermissionDeniedPage } from '../features/placeholders/pages'
import { SettingsPage } from '../features/settings/pages'
import { TraceDetailPage, TraceListPage } from '../features/traces/pages'

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
