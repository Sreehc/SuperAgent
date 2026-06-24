import type { ReactNode } from 'react'
import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { AppShell } from './shells/AppShell'
import { GuestOnly, RequireAuth, RequireRole } from './guards'
import { CommandPalette, LoadingSpinner, Toaster } from '@/components'

const LoginPage = lazy(() => import('@/features/auth/pages/LoginPage').then((module) => ({ default: module.LoginPage })))
const ChatWorkspacePage = lazy(() =>
  import('@/features/chat/pages/ChatWorkspacePage').then((module) => ({ default: module.ChatWorkspacePage })),
)
const KnowledgeListPage = lazy(() =>
  import('@/features/knowledge/pages/KnowledgeListPage').then((module) => ({ default: module.KnowledgeListPage })),
)
const KnowledgeDetailPage = lazy(() =>
  import('@/features/knowledge/pages/KnowledgeDetailPage').then((module) => ({ default: module.KnowledgeDetailPage })),
)
const DocumentDetailPage = lazy(() =>
  import('@/features/knowledge/pages/DocumentDetailPage').then((module) => ({ default: module.DocumentDetailPage })),
)
const TraceListPage = lazy(() =>
  import('@/features/traces/pages/TraceListPage').then((module) => ({ default: module.TraceListPage })),
)
const TraceDetailPage = lazy(() =>
  import('@/features/traces/pages/TraceDetailPage').then((module) => ({ default: module.TraceDetailPage })),
)
const SettingsPage = lazy(() => import('@/features/settings/pages/SettingsPage').then((module) => ({ default: module.SettingsPage })))
const ToolsConsolePage = lazy(() =>
  import('@/features/agent/pages/ToolsConsolePage').then((module) => ({ default: module.ToolsConsolePage })),
)
const GovernanceConsolePage = lazy(() =>
  import('@/features/governance/pages/GovernanceConsolePage').then((module) => ({ default: module.GovernanceConsolePage })),
)
const FeedbackConsolePage = lazy(() =>
  import('@/features/feedback/pages/FeedbackConsolePage').then((module) => ({ default: module.FeedbackConsolePage })),
)
const EvaluationConsolePage = lazy(() =>
  import('@/features/evaluation/pages/EvaluationConsolePage').then((module) => ({ default: module.EvaluationConsolePage })),
)
const EvalSuiteDetailPage = lazy(() =>
  import('@/features/evaluation/pages/EvalSuiteDetailPage').then((module) => ({ default: module.EvalSuiteDetailPage })),
)
const EvalRunDetailPage = lazy(() =>
  import('@/features/evaluation/pages/EvalRunDetailPage').then((module) => ({ default: module.EvalRunDetailPage })),
)
const MembersConsolePage = lazy(() =>
  import('@/features/members/pages/MembersConsolePage').then((module) => ({ default: module.MembersConsolePage })),
)
const AuditLogPage = lazy(() => import('@/features/audit/pages/AuditLogPage').then((module) => ({ default: module.AuditLogPage })))
const PermissionDeniedPage = lazy(() =>
  import('@/features/placeholders/pages/PermissionDeniedPage').then((module) => ({ default: module.PermissionDeniedPage })),
)

function RootLayout() {
  return (
    <>
      <Outlet />
      <Toaster />
      <CommandPalette />
    </>
  )
}

function RouteLoading() {
  return (
    <div className="route-loading">
      <LoadingSpinner />
    </div>
  )
}

function withSuspense(element: ReactNode) {
  return <Suspense fallback={<RouteLoading />}>{element}</Suspense>
}

const ADMIN: Array<'OWNER' | 'ADMIN' | 'MEMBER'> = ['OWNER', 'ADMIN']

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      {
        element: <GuestOnly />,
        children: [{ path: '/login', element: withSuspense(<LoginPage />) }],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Navigate to="/chat" replace /> },
              { path: 'chat', element: withSuspense(<ChatWorkspacePage />) },
              { path: 'chat/:sessionId', element: withSuspense(<ChatWorkspacePage />) },
              { path: 'knowledge', element: withSuspense(<KnowledgeListPage />) },
              { path: 'knowledge/:knowledgeBaseId', element: withSuspense(<KnowledgeDetailPage />) },
              { path: 'documents/:documentId', element: withSuspense(<DocumentDetailPage />) },
              {
                element: <RequireRole roles={ADMIN} />,
                children: [
                  { path: 'traces', element: withSuspense(<TraceListPage />) },
                  { path: 'traces/:exchangeId', element: withSuspense(<TraceDetailPage />) },
                  { path: 'settings', element: withSuspense(<SettingsPage />) },
                  { path: 'tools', element: withSuspense(<ToolsConsolePage />) },
                  { path: 'governance', element: withSuspense(<GovernanceConsolePage />) },
                  { path: 'feedback', element: withSuspense(<FeedbackConsolePage />) },
                  { path: 'evals', element: withSuspense(<EvaluationConsolePage />) },
                  { path: 'evals/runs/:runId', element: withSuspense(<EvalRunDetailPage />) },
                  { path: 'evals/:suiteId', element: withSuspense(<EvalSuiteDetailPage />) },
                  { path: 'members', element: withSuspense(<MembersConsolePage />) },
                  { path: 'audit-logs', element: withSuspense(<AuditLogPage />) },
                ],
              },
              { path: 'evaluations', element: <Navigate to="/evals" replace /> },
              { path: 'forbidden', element: withSuspense(<PermissionDeniedPage />) },
            ],
          },
        ],
      },
      { path: '*', element: <Navigate to="/chat" replace /> },
    ],
  },
])
