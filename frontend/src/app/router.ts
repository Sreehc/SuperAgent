import type { Pinia } from 'pinia'
import { createRouter as createVueRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import AppShell from './shells/AppShell.vue'
import { useAuthStore } from '../features/auth/store/auth'
import { LoginPage } from '../features/auth/pages'
import { ChatWorkspacePage } from '../features/chat/pages'
import { KnowledgeDetailPage, KnowledgeListPage } from '../features/knowledge/pages'
import { PermissionDeniedPage, SettingsPlaceholderPage, TracePlaceholderPage } from '../features/placeholders/pages'

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
            path: '/traces',
            name: 'traces',
            component: TracePlaceholderPage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: 'Trace',
            },
          },
          {
            path: '/settings',
            name: 'settings',
            component: SettingsPlaceholderPage,
            meta: {
              requiresAuth: true,
              roles: ['OWNER', 'ADMIN'],
              menuLabel: '设置',
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
      if (authStore.hasToken) {
        await authStore.ensureSession()
        if (authStore.isAuthenticated) {
          return authStore.pendingRedirect || '/chat'
        }
      }
      return true
    }

    if (!to.meta.requiresAuth) {
      return true
    }

    if (!authStore.hasToken) {
      authStore.rememberRedirect(to.fullPath)
      return { name: 'login' }
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
