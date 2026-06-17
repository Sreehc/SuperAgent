import { useEffect, useState } from 'react'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../../shared/ui/tabs'
import { Button } from '../../../shared/ui/button'
import { toast } from '../../../utils/toast'
import { selectCurrentRole, useAuthStore } from '../../auth/store/auth'
import { useSettingsStore } from '../store/settings'

const TABS = [
  { id: 'model', label: '模型', risk: '影响对话模型、向量模型和所有回答生成。' },
  { id: 'rag', label: 'RAG', risk: '影响召回数量、证据预算和回答可靠性。' },
  { id: 'rerank', label: '重排序', risk: '影响检索结果排序和证据选择。' },
  { id: 'agent', label: '智能体', risk: '影响 Agent 步数、检查点和工具策略。' },
  { id: 'tools', label: '工具权限', risk: '影响联网、HTTP 和代码执行等高风险能力。' },
] as const

export function SettingsPage() {
  const role = useAuthStore(selectCurrentRole)
  const currentTenantId = useAuthStore((s) => s.currentTenantId)
  const isOwner = role === 'OWNER'
  const store = useSettingsStore()
  const { modelForm, ragForm, fieldErrors, savingTab } = store
  const [activeTab, setActiveTab] = useState<string>('model')

  useEffect(() => {
    store.loadAll().catch(() => {})
    // reload on tenant switch
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenantId])

  useEffect(() => {
    if (store.successMessage) toast.success('配置已保存')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [store.successMessage])

  useEffect(() => {
    if (store.errorMessage) toast.error(store.errorMessage)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [store.errorMessage])

  async function saveModel() {
    if (!isOwner || !window.confirm('模型配置直接影响对话和向量计算，确认保存吗？')) return
    await store.saveModel()
  }
  async function saveRag() {
    if (!window.confirm('检索策略配置会影响回答质量和证据预算，确认保存吗？')) return
    await store.saveRag()
  }
  async function saveRerank() {
    if (!isOwner || !window.confirm('重排序配置会影响检索结果的排序质量，确认保存吗？')) return
    await store.saveRerank()
  }
  async function saveAgent() {
    if (!window.confirm('智能体设置会影响执行上限、检查点和工具策略，确认保存吗？')) return
    await store.saveAgent()
  }
  async function saveTools() {
    if (!window.confirm('工具权限设置会影响联网搜索、HTTP 和代码执行的可用范围，确认保存吗？')) return
    await store.saveTools()
  }

  const activeMeta = TABS.find((tab) => tab.id === activeTab) ?? TABS[0]

  return (
    <ConsolePage
      title="设置"
      description="模型、检索、重排序、智能体与工具权限的租户级配置。"
      actions={
        <Button variant="ghost" data-testid="settings-refresh" onClick={() => store.loadAll().catch(() => {})}>
          刷新
        </Button>
      }
    >
      <div className="settings-layout">
        <Tabs value={activeTab} onValueChange={setActiveTab} className="settings-main">
          <TabsList>
            {TABS.map((tab) => (
              <TabsTrigger key={tab.id} value={tab.id}>
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>

          <TabsContent value="model" className="settings-section">
            <Field label="接口地址" error={fieldErrors.baseUrl} testid="settings-error-base-url">
              <input
                data-testid="settings-model-base-url"
                value={modelForm.baseUrl}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ baseUrl: e.target.value })}
              />
            </Field>
            <Field label="对话模型">
              <input
                data-testid="settings-model-chat-model"
                value={modelForm.chatModel}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ chatModel: e.target.value })}
              />
            </Field>
            <Field label="向量模型">
              <input
                data-testid="settings-model-embedding-model"
                value={modelForm.embeddingModel}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ embeddingModel: e.target.value })}
              />
            </Field>
            <Field label="API Key" hint={modelForm.apiKeySet ? 'API Key 已设置' : undefined}>
              <input
                data-testid="settings-model-api-key"
                type="password"
                placeholder="留空表示保持现状"
                value={modelForm.apiKey}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ apiKey: e.target.value })}
              />
            </Field>
            <div className="action-row">
              <Button variant="primary" data-testid="settings-save-model" loading={savingTab === 'model'} onClick={saveModel}>
                保存配置
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="rag" className="settings-section">
            <Field label="最大子问题数">
              <input
                type="number"
                min={1}
                data-testid="settings-rag-max-sub-questions"
                value={ragForm.maxSubQuestions}
                onChange={(e) => store.patchRag({ maxSubQuestions: Number(e.target.value) })}
              />
            </Field>
            <Field label="向量召回 Top-K" error={fieldErrors.vectorTopK} testid="settings-error-vector-top-k">
              <input
                type="number"
                min={1}
                data-testid="settings-rag-vector-top-k"
                value={ragForm.vectorTopK}
                onChange={(e) => store.patchRag({ vectorTopK: Number(e.target.value) })}
              />
            </Field>
            <Field label="关键词召回 Top-K">
              <input
                type="number"
                min={1}
                value={ragForm.keywordTopK}
                onChange={(e) => store.patchRag({ keywordTopK: Number(e.target.value) })}
              />
            </Field>
            <Field label="证据条数上限">
              <input
                type="number"
                min={1}
                value={ragForm.evidenceLimit}
                onChange={(e) => store.patchRag({ evidenceLimit: Number(e.target.value) })}
              />
            </Field>
            <div className="action-row">
              <Button variant="primary" data-testid="settings-save-rag" loading={savingTab === 'rag'} onClick={saveRag}>
                保存检索策略
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="rerank" className="settings-section">
            <Field label="模型提供方" error={fieldErrors.provider}>
              <input
                data-testid="settings-rerank-provider"
                value={store.rerankForm.provider}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ provider: e.target.value })}
              />
            </Field>
            <Field label="接口地址">
              <input
                data-testid="settings-rerank-base-url"
                value={store.rerankForm.baseUrl}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ baseUrl: e.target.value })}
              />
            </Field>
            <Field label="模型名称">
              <input
                data-testid="settings-rerank-model"
                value={store.rerankForm.model}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ model: e.target.value })}
              />
            </Field>
            <Field label="密钥" hint={store.rerankForm.apiKeySet ? 'API Key 已设置' : undefined}>
              <input
                data-testid="settings-rerank-api-key"
                type="password"
                placeholder="留空表示保持现状"
                value={store.rerankForm.apiKey}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ apiKey: e.target.value })}
              />
            </Field>
            <div className="action-row">
              <Button variant="primary" data-testid="settings-save-rerank" loading={savingTab === 'rerank'} onClick={saveRerank}>
                保存配置
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="agent" className="settings-section">
            <Field label="最大模型步数">
              <input
                type="number"
                min={1}
                value={store.agentForm.maxModelSteps}
                onChange={(e) => store.patchAgent({ maxModelSteps: Number(e.target.value) })}
              />
            </Field>
            <Field label="最大工具调用数">
              <input
                type="number"
                min={1}
                value={store.agentForm.maxToolCalls}
                onChange={(e) => store.patchAgent({ maxToolCalls: Number(e.target.value) })}
              />
            </Field>
            <Field label="允许的 HTTP 域名（每行一个）">
              <textarea
                rows={4}
                value={store.agentForm.allowedHttpDomainsText}
                onChange={(e) => store.patchAgent({ allowedHttpDomainsText: e.target.value })}
              />
            </Field>
            <div className="action-row">
              <Button variant="primary" data-testid="settings-save-agent" loading={savingTab === 'agent'} onClick={saveAgent}>
                保存智能体设置
              </Button>
            </div>
          </TabsContent>

          <TabsContent value="tools" className="settings-section">
            <Field label="搜索提供方">
              <input
                value={store.toolForm.searchProvider}
                onChange={(e) => store.patchTool({ searchProvider: e.target.value })}
              />
            </Field>
            <Field label="工具超时（ms）">
              <input
                type="number"
                min={1000}
                value={store.toolForm.toolTimeoutMs}
                onChange={(e) => store.patchTool({ toolTimeoutMs: Number(e.target.value) })}
              />
            </Field>
            <Field label="允许的 HTTP 域名（每行一个）">
              <textarea
                rows={4}
                value={store.toolForm.allowedHttpDomainsText}
                onChange={(e) => store.patchTool({ allowedHttpDomainsText: e.target.value })}
              />
            </Field>
            <div className="action-row">
              <Button variant="primary" data-testid="settings-save-tools" loading={savingTab === 'tools'} onClick={saveTools}>
                保存工具权限
              </Button>
            </div>
          </TabsContent>
        </Tabs>

        <aside className="settings-inspector inspector-box">
          <h2>{activeMeta.label}</h2>
          <p>{activeMeta.risk}</p>
          <dl className="reference-detail">
            <div>
              <dt>当前角色</dt>
              <dd>{role ?? '-'}</dd>
            </div>
            <div>
              <dt>保存状态</dt>
              <dd>{savingTab ?? 'idle'}</dd>
            </div>
            <div>
              <dt>校验错误</dt>
              <dd>{Object.keys(fieldErrors).length}</dd>
            </div>
          </dl>
          {store.successMessage && <p className="success-banner">{store.successMessage}</p>}
          {store.errorMessage && <p className="error-banner">{store.errorMessage}</p>}
        </aside>
      </div>
    </ConsolePage>
  )
}

function Field({
  label,
  error,
  testid,
  hint,
  children,
}: {
  label: string
  error?: string
  testid?: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small className="field-label">{hint}</small>}
      {error && (
        <small className="field-error" data-testid={testid}>
          {error}
        </small>
      )}
    </label>
  )
}

export default SettingsPage
