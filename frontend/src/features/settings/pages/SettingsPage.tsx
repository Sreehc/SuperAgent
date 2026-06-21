import { useEffect, useState } from 'react'
import { useBlocker, useSearchParams } from 'react-router-dom'
import { ConsolePage } from '../../../shared/ui/console-page'
import { Button } from '../../../shared/ui/button'
import { ConfirmDialog } from '../../../shared/ui/dialog'
import { FormField as Field, SaveBar } from '../../../shared/ui/form'
import { toast } from '../../../utils/toast'
import { selectCurrentRole, useAuthStore } from '../../auth/store/auth'
import { useSettingsStore, type SettingsTab } from '../store/settings'

const TABS = [
  { id: 'model', label: '模型', risk: '影响对话模型、向量模型和所有回答生成。' },
  { id: 'rag', label: 'RAG', risk: '影响召回数量、证据预算和回答可靠性。' },
  { id: 'rerank', label: '重排序', risk: '影响检索结果排序和证据选择。' },
  { id: 'agent', label: '智能体', risk: '影响 Agent 步数、检查点和工具策略。' },
  { id: 'tools', label: '工具权限', risk: '影响联网、HTTP 和代码执行等高风险能力。' },
] as const

const SAVE_CONFIRM: Record<SettingsTab, { title: string; description: string; risks: string[] }> = {
  model: {
    title: '保存模型配置',
    description: '模型配置直接影响对话和向量计算，保存后会影响后续回答生成。',
    risks: ['对话模型', '向量模型', '模型接口地址', '模型 API Key'],
  },
  rag: {
    title: '保存 RAG 配置',
    description: '检索策略配置会影响回答质量、召回数量和证据预算。',
    risks: ['召回数量', '证据预算', '重排序开关'],
  },
  rerank: {
    title: '保存重排序配置',
    description: '重排序配置会影响检索结果的排序质量和证据选择。',
    risks: ['重排序模型', '重排序接口地址', '重排序 API Key'],
  },
  agent: {
    title: '保存智能体设置',
    description: '智能体设置会影响执行上限、检查点、工具策略和外部访问边界。',
    risks: ['Agent 工具能力', '代码执行', 'HTTP 域名', '最大工具调用数'],
  },
  tools: {
    title: '保存工具权限',
    description: '工具权限设置会影响联网搜索、HTTP 和代码执行的可用范围。',
    risks: ['联网搜索', 'HTTP 工具', '代码执行', 'HTTP 域名'],
  },
}

function isSettingsTab(value: string | null): value is SettingsTab {
  return TABS.some((tab) => tab.id === value)
}

export function SettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const role = useAuthStore(selectCurrentRole)
  const currentTenantId = useAuthStore((s) => s.currentTenantId)
  const isOwner = role === 'OWNER'
  const store = useSettingsStore()
  const { modelForm, ragForm, fieldErrors, savingTab, dirtyTabs, lastSavedTab, successMessage, errorMessage } = store
  const [pendingSave, setPendingSave] = useState<SettingsTab | null>(null)
  const hasDirtySettings = Object.values(dirtyTabs).some(Boolean)
  const tabParam = searchParams.get('tab')
  const activeTab: SettingsTab = isSettingsTab(tabParam) ? tabParam : 'model'
  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (!hasDirtySettings) return false
    if (currentLocation.pathname === nextLocation.pathname) return false
    return true
  })

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

  useEffect(() => {
    function handleBeforeUnload(event: BeforeUnloadEvent) {
      if (!hasDirtySettings) return
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasDirtySettings])

  function setActiveTab(tab: SettingsTab) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (tab === 'model') {
        next.delete('tab')
      } else {
        next.set('tab', tab)
      }
      return next
    })
  }

  function requestSave(tab: SettingsTab) {
    if ((tab === 'model' || tab === 'rerank') && !isOwner) return
    setPendingSave(tab)
  }

  async function confirmPendingSave() {
    if (!pendingSave) return
    if (pendingSave === 'model') await store.saveModel()
    if (pendingSave === 'rag') await store.saveRag()
    if (pendingSave === 'rerank') await store.saveRerank()
    if (pendingSave === 'agent') await store.saveAgent()
    if (pendingSave === 'tools') await store.saveTools()
  }

  const activeMeta = TABS.find((tab) => tab.id === activeTab) ?? TABS[0]
  const pendingSaveMeta = pendingSave ? SAVE_CONFIRM[pendingSave] : null
  const auditSuccessMessage = successMessage ? `${successMessage}已记录审计。` : ''
  const activeSaveState = savingTab === activeTab ? 'saving' : dirtyTabs[activeTab] ? 'dirty' : lastSavedTab === activeTab ? 'saved' : 'idle'

  function renderSaveBar(
    tab: SettingsTab,
    label: string,
    buttonLabel: string,
    testId: string,
    onSave: () => void,
    disabled = false,
  ) {
    const dirty = dirtyTabs[tab]
    const saving = savingTab === tab
    const saved = lastSavedTab === tab && !dirty && Boolean(successMessage)
    const error = activeTab === tab && !savingTab && errorMessage ? errorMessage : undefined
    const message = saving
      ? `正在保存${label}…`
      : dirty
        ? `${label}有未保存修改。`
        : saved
          ? auditSuccessMessage
          : `${label}没有未保存修改。`

    return (
      <SaveBar dirty={dirty} saving={saving} saved={saved} error={error} message={message}>
        <Button
          variant="primary"
          data-testid={testId}
          loading={saving}
          disabled={disabled || Boolean(savingTab) || !dirty}
          onClick={onSave}
        >
          {buttonLabel}
        </Button>
      </SaveBar>
    )
  }

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
        <main className="settings-main">
          <nav className="tabs-list" aria-label="设置分组" role="tablist">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                role="tab"
                aria-selected={activeTab === tab.id}
                aria-controls={`settings-panel-${tab.id}`}
                className={`tab-button${activeTab === tab.id ? ' tab-button--active' : ''}`}
                onClick={() => setActiveTab(tab.id)}
              >
                {tab.label}
              </button>
            ))}
          </nav>

          {activeTab === 'model' && (
          <section className="settings-section" id="settings-panel-model" role="tabpanel" aria-label="模型">
            <Field label="接口地址" htmlFor="settings-model-base-url" error={fieldErrors.baseUrl} errorTestId="settings-error-base-url">
              <input
                data-testid="settings-model-base-url"
                value={modelForm.baseUrl}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ baseUrl: e.target.value })}
              />
            </Field>
            <Field label="对话模型" htmlFor="settings-model-chat-model" error={fieldErrors.chatModel}>
              <input
                data-testid="settings-model-chat-model"
                value={modelForm.chatModel}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ chatModel: e.target.value })}
              />
            </Field>
            <Field label="向量模型" htmlFor="settings-model-embedding-model" error={fieldErrors.embeddingModel}>
              <input
                data-testid="settings-model-embedding-model"
                value={modelForm.embeddingModel}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ embeddingModel: e.target.value })}
              />
            </Field>
            <Field label="API Key" htmlFor="settings-model-api-key" hint={modelForm.apiKeySet ? 'API Key 已设置' : undefined} error={fieldErrors.apiKey}>
              <input
                data-testid="settings-model-api-key"
                type="password"
                placeholder="留空表示保持现状"
                value={modelForm.apiKey}
                disabled={!isOwner}
                onChange={(e) => store.patchModel({ apiKey: e.target.value })}
              />
            </Field>
            {renderSaveBar('model', '模型配置', '保存配置', 'settings-save-model', () => requestSave('model'), !isOwner)}
          </section>
          )}

          {activeTab === 'rag' && (
          <section className="settings-section" id="settings-panel-rag" role="tabpanel" aria-label="RAG">
            <Field label="最大子问题数" htmlFor="settings-rag-max-sub-questions" error={fieldErrors.maxSubQuestions}>
              <input
                type="number"
                min={1}
                data-testid="settings-rag-max-sub-questions"
                value={ragForm.maxSubQuestions}
                onChange={(e) => store.patchRag({ maxSubQuestions: Number(e.target.value) })}
              />
            </Field>
            <Field label="向量召回 Top-K" htmlFor="settings-rag-vector-top-k" error={fieldErrors.vectorTopK} errorTestId="settings-error-vector-top-k">
              <input
                type="number"
                min={1}
                data-testid="settings-rag-vector-top-k"
                value={ragForm.vectorTopK}
                onChange={(e) => store.patchRag({ vectorTopK: Number(e.target.value) })}
              />
            </Field>
            <Field label="关键词召回 Top-K" htmlFor="settings-rag-keyword-top-k" error={fieldErrors.keywordTopK}>
              <input
                type="number"
                min={1}
                value={ragForm.keywordTopK}
                onChange={(e) => store.patchRag({ keywordTopK: Number(e.target.value) })}
              />
            </Field>
            <Field label="证据条数上限" htmlFor="settings-rag-evidence-limit" error={fieldErrors.evidenceLimit}>
              <input
                type="number"
                min={1}
                value={ragForm.evidenceLimit}
                onChange={(e) => store.patchRag({ evidenceLimit: Number(e.target.value) })}
              />
            </Field>
            {renderSaveBar('rag', 'RAG 配置', '保存检索策略', 'settings-save-rag', () => requestSave('rag'))}
          </section>
          )}

          {activeTab === 'rerank' && (
          <section className="settings-section" id="settings-panel-rerank" role="tabpanel" aria-label="重排序">
            <Field label="模型提供方" htmlFor="settings-rerank-provider" error={fieldErrors.provider}>
              <input
                data-testid="settings-rerank-provider"
                value={store.rerankForm.provider}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ provider: e.target.value })}
              />
            </Field>
            <Field label="接口地址" htmlFor="settings-rerank-base-url" error={fieldErrors.baseUrl}>
              <input
                data-testid="settings-rerank-base-url"
                value={store.rerankForm.baseUrl}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ baseUrl: e.target.value })}
              />
            </Field>
            <Field label="模型名称" htmlFor="settings-rerank-model" error={fieldErrors.model}>
              <input
                data-testid="settings-rerank-model"
                value={store.rerankForm.model}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ model: e.target.value })}
              />
            </Field>
            <Field label="密钥" htmlFor="settings-rerank-api-key" hint={store.rerankForm.apiKeySet ? 'API Key 已设置' : undefined} error={fieldErrors.apiKey}>
              <input
                data-testid="settings-rerank-api-key"
                type="password"
                placeholder="留空表示保持现状"
                value={store.rerankForm.apiKey}
                disabled={!isOwner}
                onChange={(e) => store.patchRerank({ apiKey: e.target.value })}
              />
            </Field>
            {renderSaveBar('rerank', '重排序配置', '保存配置', 'settings-save-rerank', () => requestSave('rerank'), !isOwner)}
          </section>
          )}

          {activeTab === 'agent' && (
          <section className="settings-section" id="settings-panel-agent" role="tabpanel" aria-label="智能体">
            <Field label="最大模型步数" htmlFor="settings-agent-max-model-steps" error={fieldErrors.maxModelSteps}>
              <input
                type="number"
                min={1}
                value={store.agentForm.maxModelSteps}
                onChange={(e) => store.patchAgent({ maxModelSteps: Number(e.target.value) })}
              />
            </Field>
            <Field label="最大工具调用数" htmlFor="settings-agent-max-tool-calls" error={fieldErrors.maxToolCalls}>
              <input
                type="number"
                min={1}
                value={store.agentForm.maxToolCalls}
                onChange={(e) => store.patchAgent({ maxToolCalls: Number(e.target.value) })}
              />
            </Field>
            <Field label="允许的 HTTP 域名（每行一个）" htmlFor="settings-agent-allowed-http-domains" error={fieldErrors.allowedHttpDomains}>
              <textarea
                rows={4}
                value={store.agentForm.allowedHttpDomainsText}
                onChange={(e) => store.patchAgent({ allowedHttpDomainsText: e.target.value })}
              />
            </Field>
            {renderSaveBar('agent', '智能体配置', '保存智能体设置', 'settings-save-agent', () => requestSave('agent'))}
          </section>
          )}

          {activeTab === 'tools' && (
          <section className="settings-section" id="settings-panel-tools" role="tabpanel" aria-label="工具权限">
            <Field label="搜索提供方" htmlFor="settings-tools-search-provider" error={fieldErrors.searchProvider}>
              <input
                value={store.toolForm.searchProvider}
                onChange={(e) => store.patchTool({ searchProvider: e.target.value })}
              />
            </Field>
            <Field label="工具超时（ms）" htmlFor="settings-tools-timeout" error={fieldErrors.toolTimeoutMs}>
              <input
                type="number"
                min={1000}
                value={store.toolForm.toolTimeoutMs}
                onChange={(e) => store.patchTool({ toolTimeoutMs: Number(e.target.value) })}
              />
            </Field>
            <Field label="允许的 HTTP 域名（每行一个）" htmlFor="settings-tools-allowed-http-domains" error={fieldErrors.allowedHttpDomains}>
              <textarea
                rows={4}
                value={store.toolForm.allowedHttpDomainsText}
                onChange={(e) => store.patchTool({ allowedHttpDomainsText: e.target.value })}
              />
            </Field>
            {renderSaveBar('tools', '工具权限配置', '保存工具权限', 'settings-save-tools', () => requestSave('tools'))}
          </section>
          )}
        </main>

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
              <dd>{activeSaveState}</dd>
            </div>
            <div>
              <dt>校验错误</dt>
              <dd>{Object.keys(fieldErrors).length}</dd>
            </div>
          </dl>
          {store.successMessage && <p className="success-banner">{auditSuccessMessage}</p>}
          {store.errorMessage && <p className="error-banner">{store.errorMessage}</p>}
        </aside>
      </div>
      {pendingSaveMeta && (
        <ConfirmDialog
          open={pendingSave != null}
          title={pendingSaveMeta.title}
          description={`${pendingSaveMeta.description} 高风险项：${pendingSaveMeta.risks.join('、')}。当前版本使用二次确认和审计记录，不展示审批流入口。`}
          confirmLabel="确认保存并记录审计"
          cancelLabel="取消"
          tone="danger"
          onConfirm={confirmPendingSave}
          onOpenChange={(open) => !open && setPendingSave(null)}
        />
      )}
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title="未保存修改"
        description="当前设置页有未保存修改。离开后这些改动不会保存。"
        confirmLabel="确认离开"
        cancelLabel="继续编辑"
        tone="danger"
        onConfirm={() => blocker.proceed?.()}
        onOpenChange={(open) => {
          if (!open && blocker.state === 'blocked') {
            blocker.reset?.()
          }
        }}
      />
    </ConsolePage>
  )
}

export default SettingsPage
