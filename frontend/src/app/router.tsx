import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom'
import { AppShell } from './shells/AppShell'
import { GuestOnly, RequireAuth, RequireRole } from './guards'
import { CommandPalette, Toaster } from '@/components'
import { LoginPage } from '@/features/auth/pages/LoginPage'
import { ChatWorkspacePage } from '@/features/chat/pages/ChatWorkspacePage'
import { KnowledgeListPage } from '@/features/knowledge/pages/KnowledgeListPage'
import { KnowledgeDetailPage } from '@/features/knowledge/pages/KnowledgeDetailPage'
import { DocumentDetailPage } from '@/features/knowledge/pages/DocumentDetailPage'
import { TraceListPage } from '@/features/traces/pages/TraceListPage'
import { TraceDetailPage } from '@/features/traces/pages/TraceDetailPage'
import { SettingsPage } from '@/features/settings/pages/SettingsPage'
import { ToolsConsolePage } from '@/features/agent/pages/ToolsConsolePage'
import { GovernanceConsolePage } from '@/features/governance/pages/GovernanceConsolePage'
import { FeedbackConsolePage } from '@/features/feedback/pages/FeedbackConsolePage'
import { EvaluationConsolePage } from '@/features/evaluation/pages/EvaluationConsolePage'
import { EvalSuiteDetailPage } from '@/features/evaluation/pages/EvalSuiteDetailPage'
import { EvalRunDetailPage } from '@/features/evaluation/pages/EvalRunDetailPage'
import { MembersConsolePage } from '@/features/members/pages/MembersConsolePage'
import { AuditLogPage } from '@/features/audit/pages/AuditLogPage'
import { PermissionDeniedPage } from '@/features/placeholders/pages/PermissionDeniedPage'

function RootLayout() {
  return (
    <>
      <Outlet />
      <Toaster />
      <CommandPalette />
    </>
  )
}

const ADMIN: Array<'OWNER' | 'ADMIN' | 'MEMBER'> = ['OWNER', 'ADMIN']

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      {
        element: <GuestOnly />,
        children: [{ path: '/login', element: <LoginPage /> }],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { index: true, element: <Navigate to="/chat" replace /> },
              { path: 'chat', element: <ChatWorkspacePage /> },
              { path: 'chat/:sessionId', element: <ChatWorkspacePage /> },
              { path: 'knowledge', element: <KnowledgeListPage /> },
              { path: 'knowledge/:knowledgeBaseId', element: <KnowledgeDetailPage /> },
              { path: 'documents/:documentId', element: <DocumentDetailPage /> },
              {
                element: <RequireRole roles={ADMIN} />,
                children: [
                  { path: 'traces', element: <TraceListPage /> },
                  { path: 'traces/:exchangeId', element: <TraceDetailPage /> },
                  { path: 'settings', element: <SettingsPage /> },
                  { path: 'tools', element: <ToolsConsolePage /> },
                  { path: 'governance', element: <GovernanceConsolePage /> },
                  { path: 'feedback', element: <FeedbackConsolePage /> },
                  { path: 'evals', element: <EvaluationConsolePage /> },
                  { path: 'evals/runs/:runId', element: <EvalRunDetailPage /> },
                  { path: 'evals/:suiteId', element: <EvalSuiteDetailPage /> },
                  { path: 'members', element: <MembersConsolePage /> },
                  { path: 'audit-logs', element: <AuditLogPage /> },
                ],
              },
              { path: 'evaluations', element: <Navigate to="/evals" replace /> },
              { path: 'forbidden', element: <PermissionDeniedPage /> },
            ],
          },
        ],
      },
      { path: '*', element: <Navigate to="/chat" replace /> },
    ],
  },
])
