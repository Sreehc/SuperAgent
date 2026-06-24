import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import {
  deleteToolSecret,
  listAgentRuns,
  listPlugins,
  listToolCalls,
  listToolCapabilities,
  updatePlugin,
  updateToolSecret,
} from '../api'
import { AgentRunDetailPanel } from '../components/AgentRunDetailPanel'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Badge } from '../../../shared/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../../shared/ui/tabs'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/dialog'
import { DetailDrawer } from '../../../shared/ui/drawer'
import { TableStateRow } from '../../../shared/ui/status'
import { SelectField } from '@/shared/ui'
import { formatDateTime, formatDurationMs, formatNumber } from '@/shared/lib/format'
import type { AgentRunSummary, PluginItem, ToolCallDetail, ToolCapabilityItem } from '../types'

const TOOL_TABS = [
  { value: 'capabilities', label: '能力' },
  { value: 'plugins', label: '插件' },
  { value: 'secrets', label: '密钥' },
  { value: 'tool-calls', label: '工具调用' },
  { value: 'agent-runs', label: 'Agent Runs' },
] as const

type ToolTab = (typeof TOOL_TABS)[number]['value']
interface ToolSecretRow {
  toolId: string
  pluginName: string
  secretKey: string
  configured: boolean
}

interface ToolCallFilters {
  runId: string
  toolId: string
}

interface AppliedToolCallFilters extends Record<string, string | number | undefined> {
  runId?: number
  toolId?: string
}

function isToolTab(value: string | null): value is ToolTab {
  return TOOL_TABS.some((tab) => tab.value === value)
}

function riskTone(risk: string): 'danger' | 'warning' | 'neutral' {
  if (risk === 'high') return 'danger'
  if (risk === 'medium') return 'warning'
  return 'neutral'
}

function executableTone(tool: ToolCapabilityItem): 'success' | 'warning' | 'neutral' {
  if (tool.executable) return 'success'
  if (tool.enabled) return 'warning'
  return 'neutral'
}

function executableLabel(tool: ToolCapabilityItem) {
  if (tool.executable) return '可执行'
  if (tool.enabled) return '不可执行'
  return '已禁用'
}

function statusTone(status: string): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'success' || status === 'completed') return 'success'
  if (status === 'failed' || status === 'error') return 'danger'
  if (status === 'running' || status === 'pending') return 'warning'
  return 'neutral'
}

function secretRowKey(toolId: string, secretKey: string) {
  return `${toolId}::${secretKey}`
}

function optionalText(value: string | number | null | undefined) {
  if (value === undefined || value === null || value === '') return '—'
  return `${value}`
}

function parsePositiveNumber(value: string) {
  const normalized = value.trim()
  if (!normalized) return undefined
  const parsed = Number(normalized)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

export function ToolsConsolePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()
  const [pendingPlugin, setPendingPlugin] = useState<PluginItem | null>(null)
  const [pendingSecretDelete, setPendingSecretDelete] = useState<ToolSecretRow | null>(null)
  const [selectedToolCall, setSelectedToolCall] = useState<ToolCallDetail | null>(null)
  const [selectedAgentRunId, setSelectedAgentRunId] = useState<number | null>(null)
  const [pluginError, setPluginError] = useState<string | null>(null)
  const [secretError, setSecretError] = useState<string | null>(null)
  const [secretInputs, setSecretInputs] = useState<Record<string, string>>({})
  const [toolCallFilters, setToolCallFilters] = useState<ToolCallFilters>({ runId: '', toolId: '' })
  const [appliedToolCallFilters, setAppliedToolCallFilters] = useState<AppliedToolCallFilters>({})
  const [agentRunStatus, setAgentRunStatus] = useState('')
  const [appliedAgentRunStatus, setAppliedAgentRunStatus] = useState('')
  const [agentRunPage, setAgentRunPage] = useState(1)
  const agentRunPageSize = 10
  const { data, isLoading } = useQuery({
    queryKey: ['tool-capabilities'],
    queryFn: () => listToolCapabilities(),
  })
  const tabParam = searchParams.get('tab')
  const activeTab: ToolTab = isToolTab(tabParam) ? tabParam : 'capabilities'
  const pluginsQuery = useQuery({
    queryKey: ['tool-plugins'],
    queryFn: () => listPlugins(),
    enabled: activeTab === 'plugins' || activeTab === 'secrets',
  })
  const toolCallsQuery = useQuery({
    queryKey: ['tool-calls', appliedToolCallFilters],
    queryFn: () => listToolCalls(appliedToolCallFilters),
    enabled: activeTab === 'tool-calls',
  })
  const agentRunsQuery = useQuery({
    queryKey: ['agent-runs', agentRunPage, agentRunPageSize, appliedAgentRunStatus],
    queryFn: () =>
      listAgentRuns({
        page: agentRunPage,
        pageSize: agentRunPageSize,
        status: appliedAgentRunStatus || undefined,
      }),
    enabled: activeTab === 'agent-runs',
  })
  const togglePluginMutation = useMutation({
    mutationFn: (plugin: PluginItem) => updatePlugin(plugin.pluginId, !plugin.enabled),
    onSuccess: async () => {
      setPluginError(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['tool-plugins'] }),
        queryClient.invalidateQueries({ queryKey: ['tool-capabilities'] }),
      ])
    },
    onError: () => {
      setPluginError('插件状态更新失败，请检查权限或稍后重试。')
    },
  })
  const saveSecretMutation = useMutation({
    mutationFn: ({ toolId, secretKey, value }: { toolId: string; secretKey: string; value: string }) =>
      updateToolSecret(toolId, secretKey, value),
    onSuccess: async (_data, variables) => {
      setSecretError(null)
      setSecretInputs((current) => ({ ...current, [secretRowKey(variables.toolId, variables.secretKey)]: '' }))
      await queryClient.invalidateQueries({ queryKey: ['tool-capabilities'] })
    },
    onError: () => {
      setSecretError('密钥保存失败，请检查权限或稍后重试。')
    },
  })
  const deleteSecretMutation = useMutation({
    mutationFn: (secret: ToolSecretRow) => deleteToolSecret(secret.toolId, secret.secretKey),
    onSuccess: async () => {
      setSecretError(null)
      await queryClient.invalidateQueries({ queryKey: ['tool-capabilities'] })
    },
    onError: () => {
      setSecretError('密钥删除失败，请检查权限或稍后重试。')
    },
  })

  const tools = data?.data.tools ?? []
  const plugins = pluginsQuery.data?.data ?? []
  const toolCalls = toolCallsQuery.data?.data ?? []
  const agentRuns = agentRunsQuery.data?.data.items ?? []
  const agentRunTotal = agentRunsQuery.data?.data.total ?? 0
  const agentRunTotalPages = Math.max(1, Math.ceil(agentRunTotal / agentRunPageSize))
  const secretRows = plugins.flatMap((plugin) =>
    plugin.enabledTools.flatMap((toolId) => {
      const capability = tools.find((tool) => tool.toolId === toolId)
      return plugin.secretKeys.map((secretKey) => ({
        toolId,
        pluginName: plugin.displayName,
        secretKey,
        configured: Boolean(capability?.configuredSecrets.includes(secretKey)),
      }))
    }),
  )

  function handleTabChange(nextTab: string) {
    const next = new URLSearchParams(searchParams)
    if (nextTab === 'capabilities') {
      next.delete('tab')
    } else {
      next.set('tab', nextTab)
    }
    setSearchParams(next, { replace: true })
  }

  async function confirmPluginToggle() {
    if (!pendingPlugin) return
    await togglePluginMutation.mutateAsync(pendingPlugin)
  }

  function closePluginConfirm(open: boolean) {
    if (open) return
    setPendingPlugin(null)
    setPluginError(null)
  }

  function closeSecretDeleteConfirm(open: boolean) {
    if (open) return
    setPendingSecretDelete(null)
    setSecretError(null)
  }

  async function saveSecret(row: ToolSecretRow) {
    const key = secretRowKey(row.toolId, row.secretKey)
    const value = secretInputs[key]?.trim() ?? ''
    if (!value) return
    try {
      await saveSecretMutation.mutateAsync({ toolId: row.toolId, secretKey: row.secretKey, value })
    } catch {
      // Error state is rendered via the mutation's onError handler.
    }
  }

  async function confirmSecretDelete() {
    if (!pendingSecretDelete) return
    await deleteSecretMutation.mutateAsync(pendingSecretDelete)
  }

  function applyToolCallFilters() {
    setAppliedToolCallFilters({
      runId: parsePositiveNumber(toolCallFilters.runId),
      toolId: toolCallFilters.toolId.trim() || undefined,
    })
  }

  function applyAgentRunFilters() {
    setAgentRunPage(1)
    setAppliedAgentRunStatus(agentRunStatus.trim())
  }

  return (
    <ConsolePage title="Tools" description="agent 可调用工具的能力清单、风险等级与可用状态。">
      <section className="surface-box tools-console-tabs">
        <Tabs value={activeTab} onValueChange={handleTabChange} className="tools-tabs">
          <TabsList className="tools-tabs__list" aria-label="Tools 分组">
            {TOOL_TABS.map((tab) => (
              <TabsTrigger key={tab.value} value={tab.value} onClick={() => handleTabChange(tab.value)}>
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>

          <TabsContent value="capabilities" className="tools-tab-panel">
            <ToolCapabilityTable tools={tools} isLoading={isLoading} />
          </TabsContent>

          <TabsContent value="plugins" className="tools-tab-panel">
            <PluginTable
              plugins={plugins}
              isLoading={pluginsQuery.isLoading}
              isError={pluginsQuery.isError}
              onToggle={(plugin) => {
                setPluginError(null)
                setPendingPlugin(plugin)
              }}
            />
          </TabsContent>

          <TabsContent value="secrets" className="tools-tab-panel">
            <ToolSecretsTable
              rows={secretRows}
              inputs={secretInputs}
              error={secretError}
              isLoading={pluginsQuery.isLoading || isLoading}
              isError={pluginsQuery.isError}
              isSaving={saveSecretMutation.isPending}
              isDeleting={deleteSecretMutation.isPending}
              onInputChange={(row, value) => {
                setSecretError(null)
                setSecretInputs((current) => ({ ...current, [secretRowKey(row.toolId, row.secretKey)]: value }))
              }}
              onSave={saveSecret}
              onDelete={(row) => {
                setSecretError(null)
                setPendingSecretDelete(row)
              }}
            />
          </TabsContent>

          <TabsContent value="tool-calls" className="tools-tab-panel">
            <ToolCallsTable
              calls={toolCalls}
              filters={toolCallFilters}
              isLoading={toolCallsQuery.isLoading}
              isError={toolCallsQuery.isError}
              onFilterChange={setToolCallFilters}
              onApplyFilters={applyToolCallFilters}
              onOpenDetail={setSelectedToolCall}
            />
          </TabsContent>

          <TabsContent value="agent-runs" className="tools-tab-panel">
            <AgentRunsTable
              runs={agentRuns}
              status={agentRunStatus}
              page={agentRunPage}
              total={agentRunTotal}
              totalPages={agentRunTotalPages}
              isLoading={agentRunsQuery.isLoading}
              isError={agentRunsQuery.isError}
              onStatusChange={setAgentRunStatus}
              onApplyFilters={applyAgentRunFilters}
              onPageChange={setAgentRunPage}
              onOpenDetail={setSelectedAgentRunId}
            />
          </TabsContent>
        </Tabs>
      </section>
      <ConfirmDialog
        open={pendingPlugin != null}
        title={`${pendingPlugin?.enabled ? '禁用' : '启用'}插件 ${pendingPlugin?.displayName ?? ''}`}
        description={
          pendingPlugin
            ? `${pendingPlugin.enabled ? '禁用' : '启用'}后会刷新工具能力清单，影响该插件提供的 ${pendingPlugin.enabledTools.length} 个工具。`
            : undefined
        }
        error={pluginError}
        confirmLabel={pendingPlugin?.enabled ? '确认禁用' : '确认启用'}
        cancelLabel="取消"
        tone={pendingPlugin?.enabled ? 'danger' : 'primary'}
        onConfirm={confirmPluginToggle}
        onOpenChange={closePluginConfirm}
      />
      <ConfirmDialog
        open={pendingSecretDelete != null}
        title={`删除密钥 ${pendingSecretDelete?.secretKey ?? ''}`}
        description={
          pendingSecretDelete
            ? `确认删除 ${pendingSecretDelete.toolId} 的 ${pendingSecretDelete.secretKey}？删除后相关工具可能变为不可执行。`
            : undefined
        }
        error={secretError}
        confirmLabel="确认删除"
        cancelLabel="取消"
        tone="danger"
        onConfirm={confirmSecretDelete}
        onOpenChange={closeSecretDeleteConfirm}
      />
      <DetailDrawer
        open={selectedToolCall != null}
        title={`工具调用 #${selectedToolCall?.id ?? ''}`}
        description={selectedToolCall ? `${selectedToolCall.toolId} · Run #${selectedToolCall.agentRunId}` : undefined}
        onOpenChange={(open) => {
          if (!open) setSelectedToolCall(null)
        }}
      >
        {selectedToolCall ? <ToolCallDetailView call={selectedToolCall} /> : null}
      </DetailDrawer>
      <DetailDrawer
        open={selectedAgentRunId != null}
        title={`Agent Run #${selectedAgentRunId ?? ''}`}
        description="运行步骤、Checkpoint 与工具调用明细。"
        onOpenChange={(open) => {
          if (!open) setSelectedAgentRunId(null)
        }}
      >
        <AgentRunDetailPanel runId={selectedAgentRunId} />
      </DetailDrawer>
    </ConsolePage>
  )
}

function ToolCapabilityTable({
  tools,
  isLoading,
}: {
  tools: ToolCapabilityItem[]
  isLoading: boolean
}) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>工具</th>
            <th>类型</th>
            <th>风险</th>
            <th>状态</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          {tools.map((tool) => (
            <tr key={tool.toolId} data-testid={`tool-capability-${tool.toolId}`}>
              <td>
                <strong>{tool.name}</strong>
                <div className="mono" style={{ color: 'var(--text-muted)' }}>
                  {tool.toolId}
                </div>
              </td>
              <td className="mono">{tool.kind}</td>
              <td>
                <Badge tone={riskTone(tool.riskLevel)}>{tool.riskLevel}</Badge>
              </td>
              <td>
                <div className="chip-row">
                  <Badge tone={tool.enabled ? 'success' : 'neutral'}>{tool.enabled ? '已启用' : '已禁用'}</Badge>
                  <Badge tone={executableTone(tool)}>{executableLabel(tool)}</Badge>
                  <Badge tone={tool.requiresConfirmation ? 'warning' : 'neutral'}>
                    {tool.requiresConfirmation ? '需要确认' : '无需确认'}
                  </Badge>
                </div>
              </td>
              <td>
                <div>{tool.description || '—'}</div>
                {tool.reason && (
                  <div className="metadata" style={{ marginTop: 4 }}>
                    不可用原因：{tool.reason}
                  </div>
                )}
              </td>
            </tr>
          ))}
          {tools.length === 0 && (
            <tr>
              <td colSpan={5}>
                <div className="empty-line">{isLoading ? '加载中…' : '暂无可用工具'}</div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}

function PluginTable({
  plugins,
  isLoading,
  isError,
  onToggle,
}: {
  plugins: PluginItem[]
  isLoading: boolean
  isError: boolean
  onToggle: (plugin: PluginItem) => void
}) {
  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>插件</th>
            <th>状态</th>
            <th>工具</th>
            <th>密钥</th>
            <th>近期错误</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {isError ? (
            <tr>
              <td colSpan={7}>
                <div className="empty-line">插件列表加载失败，请刷新后重试。</div>
              </td>
            </tr>
          ) : plugins.length === 0 ? (
            <tr>
              <td colSpan={7}>
                <div className="empty-line">{isLoading ? '加载中…' : '暂无插件'}</div>
              </td>
            </tr>
          ) : (
            plugins.map((plugin) => (
              <tr key={plugin.pluginId} data-testid={`tool-plugin-${plugin.pluginId}`}>
                <td>
                  <strong>{plugin.displayName}</strong>
                  <div className="mono" style={{ color: 'var(--text-muted)' }}>
                    {plugin.pluginKey} · v{plugin.version}
                  </div>
                </td>
                <td>
                  <div className="chip-row">
                    <Badge tone={plugin.enabled ? 'success' : 'neutral'}>{plugin.enabled ? '已启用' : '已禁用'}</Badge>
                    <Badge tone={plugin.status === 'installed' ? 'success' : 'neutral'}>{plugin.status}</Badge>
                  </div>
                </td>
                <td>{plugin.enabledTools.length > 0 ? plugin.enabledTools.join(', ') : '—'}</td>
                <td>{plugin.secretKeys.length > 0 ? plugin.secretKeys.join(', ') : '—'}</td>
                <td className="numeric">近期错误 {formatNumber(plugin.recentErrorCount)}</td>
                <td className="mono">{formatDateTime(plugin.updatedAt)}</td>
                <td>
                  <Button
                    size="sm"
                    variant={plugin.enabled ? 'danger' : 'primary'}
                    onClick={() => onToggle(plugin)}
                  >
                    {plugin.enabled ? `禁用 ${plugin.displayName}` : `启用 ${plugin.displayName}`}
                  </Button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}

function ToolSecretsTable({
  rows,
  inputs,
  error,
  isLoading,
  isError,
  isSaving,
  isDeleting,
  onInputChange,
  onSave,
  onDelete,
}: {
  rows: ToolSecretRow[]
  inputs: Record<string, string>
  error: string | null
  isLoading: boolean
  isError: boolean
  isSaving: boolean
  isDeleting: boolean
  onInputChange: (row: ToolSecretRow, value: string) => void
  onSave: (row: ToolSecretRow) => void
  onDelete: (row: ToolSecretRow) => void
}) {
  return (
    <div className="table-wrap">
      {error && (
        <p className="error-banner" role="alert">
          {error}
        </p>
      )}
      <table className="data-table">
        <thead>
          <tr>
            <th>插件</th>
            <th>工具</th>
            <th>密钥</th>
            <th>状态</th>
            <th>写入</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {isError ? (
            <tr>
              <td colSpan={6}>
                <div className="empty-line">密钥配置加载失败，请刷新后重试。</div>
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td colSpan={6}>
                <div className="empty-line">{isLoading ? '加载中…' : '暂无需要配置的工具密钥'}</div>
              </td>
            </tr>
          ) : (
            rows.map((row) => {
              const key = secretRowKey(row.toolId, row.secretKey)
              const value = inputs[key] ?? ''
              return (
                <tr key={key} data-testid={`tool-secret-${row.toolId}-${row.secretKey}`}>
                  <td>{row.pluginName}</td>
                  <td className="mono">{row.toolId}</td>
                  <td className="mono">{row.secretKey}</td>
                  <td>
                    <Badge tone={row.configured ? 'success' : 'neutral'}>{row.configured ? '已设置' : '未设置'}</Badge>
                  </td>
                  <td>
                    <label className="field" style={{ minWidth: 220 }}>
                      <span className="sr-only">{`${row.toolId} ${row.secretKey} 密钥值`}</span>
                      <input
                        type="password"
                        placeholder="输入新密钥"
                        value={value}
                        aria-label={`${row.toolId} ${row.secretKey} 密钥值`}
                        onChange={(event) => onInputChange(row, event.target.value)}
                      />
                    </label>
                  </td>
                  <td>
                    <div className="action-row">
                      <Button
                        size="sm"
                        variant="primary"
                        loading={isSaving}
                        disabled={!value.trim()}
                        onClick={() => onSave(row)}
                      >
                        保存 {row.toolId} {row.secretKey}
                      </Button>
                      {row.configured && (
                        <Button
                          size="sm"
                          variant="danger"
                          loading={isDeleting}
                          onClick={() => onDelete(row)}
                        >
                          删除 {row.toolId} {row.secretKey}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })
          )}
        </tbody>
      </table>
    </div>
  )
}

function ToolCallsTable({
  calls,
  filters,
  isLoading,
  isError,
  onFilterChange,
  onApplyFilters,
  onOpenDetail,
}: {
  calls: ToolCallDetail[]
  filters: ToolCallFilters
  isLoading: boolean
  isError: boolean
  onFilterChange: (filters: ToolCallFilters) => void
  onApplyFilters: () => void
  onOpenDetail: (call: ToolCallDetail) => void
}) {
  return (
    <div className="tools-observation-view">
      <div className="filter-row" role="search" aria-label="工具调用筛选">
        <label className="field">
          <span>Agent Run ID</span>
          <input
            inputMode="numeric"
            value={filters.runId}
            onChange={(event) => onFilterChange({ ...filters, runId: event.target.value })}
          />
        </label>
        <label className="field">
          <span>工具 ID</span>
          <input
            value={filters.toolId}
            placeholder="github_issue"
            onChange={(event) => onFilterChange({ ...filters, toolId: event.target.value })}
          />
        </label>
        <Button type="button" variant="secondary" onClick={onApplyFilters}>
          筛选工具调用
        </Button>
      </div>

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>调用</th>
              <th>状态</th>
              <th>摘要</th>
              <th>耗时</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <TableStateRow state="loading" colSpan={6} label="正在加载工具调用" />
            ) : isError ? (
              <TableStateRow state="error" colSpan={6} title="工具调用加载失败" description="请刷新后重试。" />
            ) : calls.length === 0 ? (
              <TableStateRow state="empty" colSpan={6} title="暂无工具调用" description="调整筛选条件后可重新查询。" />
            ) : (
              calls.map((call) => (
                <tr key={call.id} data-testid={`tool-call-${call.id}`}>
                  <td>
                    <strong>{call.toolId}</strong>
                    <div className="mono" style={{ color: 'var(--text-muted)' }}>
                      Run #{call.agentRunId}
                      {call.pluginVersion ? ` · v${call.pluginVersion}` : ''}
                    </div>
                  </td>
                  <td>
                    <Badge tone={statusTone(call.status)}>{call.status}</Badge>
                  </td>
                  <td>
                    <div>{optionalText(call.responseSummary ?? call.requestSummary)}</div>
                    {call.errorMessage ? (
                      <div className="metadata danger-text" style={{ marginTop: 4 }}>
                        {call.errorMessage}
                      </div>
                    ) : null}
                  </td>
                  <td className="mono">{formatDurationMs(call.latencyMs)}</td>
                  <td className="mono">{formatDateTime(call.createdAt)}</td>
                  <td>
                    <Button size="sm" variant="secondary" onClick={() => onOpenDetail(call)}>
                      查看工具调用 {call.id}
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function AgentRunsTable({
  runs,
  status,
  page,
  total,
  totalPages,
  isLoading,
  isError,
  onStatusChange,
  onApplyFilters,
  onPageChange,
  onOpenDetail,
}: {
  runs: AgentRunSummary[]
  status: string
  page: number
  total: number
  totalPages: number
  isLoading: boolean
  isError: boolean
  onStatusChange: (status: string) => void
  onApplyFilters: () => void
  onPageChange: (page: number) => void
  onOpenDetail: (runId: number) => void
}) {
  return (
    <div className="tools-observation-view">
      <div className="filter-row" role="search" aria-label="Agent Runs 筛选">
        <label className="field">
          <span>运行状态</span>
          <SelectField value={status} onChange={(event) => onStatusChange(event.target.value)}>
            <option value="">全部</option>
            <option value="running">running</option>
            <option value="completed">completed</option>
            <option value="failed">failed</option>
            <option value="pending">pending</option>
          </SelectField>
        </label>
        <Button type="button" variant="secondary" onClick={onApplyFilters}>
          筛选 Agent Runs
        </Button>
        <span className="metric-chip">共 {formatNumber(total)} 条</span>
      </div>

      <div className="table-wrap">
        <table className="data-table">
          <thead>
            <tr>
              <th>Run</th>
              <th>状态</th>
              <th>关联</th>
              <th>执行摘要</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <TableStateRow state="loading" colSpan={6} label="正在加载 Agent Runs" />
            ) : isError ? (
              <TableStateRow state="error" colSpan={6} title="Agent Runs 加载失败" description="请刷新后重试。" />
            ) : runs.length === 0 ? (
              <TableStateRow state="empty" colSpan={6} title="暂无 Agent Run" description="调整筛选条件后可重新查询。" />
            ) : (
              runs.map((run) => (
                <tr key={run.runId} data-testid={`agent-run-${run.runId}`}>
                  <td>
                    <strong>Run #{run.runId}</strong>
                    <div className="mono" style={{ color: 'var(--text-muted)' }}>
                      {run.memoryStrategy}
                    </div>
                  </td>
                  <td>
                    <Badge tone={statusTone(run.status)}>{run.status}</Badge>
                  </td>
                  <td>
                    <div className="meta-row">
                      <span className="metric-chip">会话 #{run.sessionId}</span>
                      {run.exchangeId ? <span className="metric-chip">Exchange #{run.exchangeId}</span> : null}
                    </div>
                  </td>
                  <td>
                    <div className="chip-row">
                      <span className="metric-chip">模型步数 {formatNumber(run.modelStepCount)}</span>
                      <span className="metric-chip">工具调用 {formatNumber(run.toolCallCount)}</span>
                      <span className="metric-chip">Checkpoint {formatNumber(run.latestCheckpointNo)}</span>
                    </div>
                    <div className="metadata" style={{ marginTop: 4 }}>
                      {optionalText(run.errorMessage ?? run.routeReason)}
                    </div>
                  </td>
                  <td className="mono">
                    <div>{formatDateTime(run.startedAt)}</div>
                    <div>{formatDateTime(run.finishedAt)}</div>
                  </td>
                  <td>
                    <Button size="sm" variant="secondary" onClick={() => onOpenDetail(run.runId)}>
                      查看 Agent Run {run.runId}
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="action-row" aria-label="Agent Runs 分页">
        <Button type="button" size="sm" variant="secondary" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
          上一页 Agent Runs
        </Button>
        <span className="metric-chip">
          第 {formatNumber(page)} / {formatNumber(totalPages)} 页
        </span>
        <Button type="button" size="sm" variant="secondary" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>
          下一页 Agent Runs
        </Button>
      </div>
    </div>
  )
}

function ToolCallDetailView({ call }: { call: ToolCallDetail }) {
  return (
    <div className="trace-card-list">
      <article className="trace-observation">
        <div className="trace-observation__header">
          <div>
            <div className="meta-row">
              <Badge tone={statusTone(call.status)}>{call.status}</Badge>
              <span className="mono">Run #{call.agentRunId}</span>
              {call.pluginId ? <span className="mono">Plugin #{call.pluginId}</span> : null}
            </div>
            <strong>{call.toolId}</strong>
          </div>
          <div className="meta-row">
            <span className="metric-chip">{formatDurationMs(call.latencyMs)}</span>
            <span className="metric-chip">{formatDateTime(call.createdAt)}</span>
          </div>
        </div>
        <dl className="reference-detail trace-detail-list">
          <div>
            <dt>Request</dt>
            <dd>{optionalText(call.requestSummary)}</dd>
          </div>
          <div>
            <dt>Response</dt>
            <dd>{optionalText(call.responseSummary)}</dd>
          </div>
          {call.errorMessage ? (
            <div>
              <dt>错误</dt>
              <dd className="danger-text">{call.errorMessage}</dd>
            </div>
          ) : null}
          <div>
            <dt>Metadata</dt>
            <dd>
              <pre className="metadata-pre">{JSON.stringify(call.metadata ?? {}, null, 2)}</pre>
            </dd>
          </div>
        </dl>
      </article>
    </div>
  )
}

export default ToolsConsolePage
