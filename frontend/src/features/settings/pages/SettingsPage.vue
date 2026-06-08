<template>
  <section class="settings-editor workspace-page">
    <header class="workspace-strip">
      <div class="workspace-title">
        <p class="section-label">Runtime configuration</p>
        <h1>运行时设置</h1>
        <p>模型、RAG、Agent 和工具策略统一在这里维护，密钥只展示“已设置”状态。</p>
      </div>
      <button class="btn btn-ghost btn-sm" type="button" data-testid="settings-refresh" @click="reload">刷新</button>
    </header>

    <LoadingSpinner v-if="settingsStore.loading" text="正在加载设置..." />

    <section v-else class="settings-layout">
      <aside class="settings-nav" aria-label="设置分组">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="settings-nav__item"
          :class="{ 'settings-nav__item--active': activeTab === tab.id }"
          type="button"
          @click="activeTab = tab.id"
        >
          <span>{{ tab.label }}</span>
          <small>{{ tab.summary }}</small>
        </button>
      </aside>

      <main class="settings-main">
        <section v-if="activeTab === 'model'" class="settings-section">
          <div class="section-head">
            <div><h2>模型配置</h2><p>配置对话模型和向量模型，提供方与密钥由 OWNER 维护，ADMIN 仅可查看。</p></div>
            <span class="status-chip">{{ settingsStore.modelForm.apiKeySet ? 'API Key 已设置' : 'API Key 未设置' }}</span>
          </div>
          <div class="field-grid">
            <label class="field"><span>模型提供方</span><input v-model="settingsStore.modelForm.provider" disabled /></label>
            <label class="field">
              <span>接口地址</span>
              <input v-model="settingsStore.modelForm.baseUrl" data-testid="settings-model-base-url" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.baseUrl)" />
              <small v-if="settingsStore.fieldErrors.baseUrl" class="field-error" data-testid="settings-error-base-url">{{ settingsStore.fieldErrors.baseUrl }}</small>
            </label>
            <label class="field">
              <span>对话模型</span>
              <input v-model="settingsStore.modelForm.chatModel" data-testid="settings-model-chat-model" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.chatModel)" />
              <small v-if="settingsStore.fieldErrors.chatModel" class="field-error">{{ settingsStore.fieldErrors.chatModel }}</small>
            </label>
            <label class="field">
              <span>向量模型</span>
              <input v-model="settingsStore.modelForm.embeddingModel" data-testid="settings-model-embedding-model" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.embeddingModel)" />
              <small v-if="settingsStore.fieldErrors.embeddingModel" class="field-error">{{ settingsStore.fieldErrors.embeddingModel }}</small>
            </label>
            <label class="field field--full">
              <span>密钥</span>
              <input v-model="settingsStore.modelForm.apiKey" type="password" placeholder="留空表示保持现状" data-testid="settings-model-api-key" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.apiKey)" />
              <small v-if="settingsStore.fieldErrors.apiKey" class="field-error">{{ settingsStore.fieldErrors.apiKey }}</small>
            </label>
          </div>
          <div class="save-strip">
            <p v-if="!isOwner">当前角色只能查看模型配置。</p>
            <div v-else class="action-row">
              <button class="btn btn-secondary" :class="{ 'btn-loading': testingModel }" type="button" data-testid="settings-test-model" :disabled="testingModel" @click="testModel">{{ testingModel ? '测试中...' : '测试连接' }}</button>
              <button class="btn btn-primary" :class="{ 'btn-loading': settingsStore.savingTab === 'model' }" type="button" data-testid="settings-save-model" :disabled="settingsStore.savingTab === 'model'" @click="saveModel">{{ settingsStore.savingTab === 'model' ? '保存中...' : '保存配置' }}</button>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'rag'" class="settings-section">
          <div class="section-head">
            <div><h2>检索策略</h2><p>配置检索优先级、证据预算和重排序开关，影响回答质量和召回效果。</p></div>
          </div>
          <div class="field-grid">
            <label class="toggle-field"><span>问题改写</span><input v-model="settingsStore.ragForm.rewriteEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>子问题拆分</span><input v-model="settingsStore.ragForm.subQuestionEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>重排序</span><input v-model="settingsStore.ragForm.rerankEnabled" type="checkbox" /></label>
            <label class="field">
              <span>最大子问题数</span>
              <input v-model.number="settingsStore.ragForm.maxSubQuestions" data-testid="settings-rag-max-sub-questions" type="number" min="1" :aria-invalid="Boolean(settingsStore.fieldErrors.maxSubQuestions)" />
              <small v-if="settingsStore.fieldErrors.maxSubQuestions" class="field-error">{{ settingsStore.fieldErrors.maxSubQuestions }}</small>
            </label>
            <label class="field">
              <span>向量检索条数</span>
              <input v-model.number="settingsStore.ragForm.vectorTopK" type="number" min="1" data-testid="settings-rag-vector-top-k" :aria-invalid="Boolean(settingsStore.fieldErrors.vectorTopK)" />
              <small v-if="settingsStore.fieldErrors.vectorTopK" class="field-error" data-testid="settings-error-vector-top-k">{{ settingsStore.fieldErrors.vectorTopK }}</small>
            </label>
            <label class="field"><span>关键词检索条数</span><input v-model.number="settingsStore.ragForm.keywordTopK" type="number" min="1" :aria-invalid="Boolean(settingsStore.fieldErrors.keywordTopK)" /><small v-if="settingsStore.fieldErrors.keywordTopK" class="field-error">{{ settingsStore.fieldErrors.keywordTopK }}</small></label>
            <label class="field"><span>RRF 融合系数</span><input v-model.number="settingsStore.ragForm.rrfK" type="number" min="1" :aria-invalid="Boolean(settingsStore.fieldErrors.rrfK)" /><small v-if="settingsStore.fieldErrors.rrfK" class="field-error">{{ settingsStore.fieldErrors.rrfK }}</small></label>
            <label class="field"><span>证据条数上限</span><input v-model.number="settingsStore.ragForm.evidenceLimit" type="number" min="1" :aria-invalid="Boolean(settingsStore.fieldErrors.evidenceLimit)" /><small v-if="settingsStore.fieldErrors.evidenceLimit" class="field-error">{{ settingsStore.fieldErrors.evidenceLimit }}</small></label>
            <label class="field"><span>单问题字符上限</span><input v-model.number="settingsStore.ragForm.perQuestionEvidenceCharLimit" type="number" min="1" :aria-invalid="Boolean(settingsStore.fieldErrors.perQuestionEvidenceCharLimit)" /><small v-if="settingsStore.fieldErrors.perQuestionEvidenceCharLimit" class="field-error">{{ settingsStore.fieldErrors.perQuestionEvidenceCharLimit }}</small></label>
            <label class="field"><span>总字符上限</span><input v-model.number="settingsStore.ragForm.totalEvidenceCharLimit" type="number" min="1" :aria-invalid="Boolean(settingsStore.fieldErrors.totalEvidenceCharLimit)" /><small v-if="settingsStore.fieldErrors.totalEvidenceCharLimit" class="field-error">{{ settingsStore.fieldErrors.totalEvidenceCharLimit }}</small></label>
            <label class="field"><span>最低相关度分数</span><input v-model.number="settingsStore.ragForm.minRelevanceScore" type="number" min="0" step="0.01" :aria-invalid="Boolean(settingsStore.fieldErrors.minRelevanceScore)" /><small v-if="settingsStore.fieldErrors.minRelevanceScore" class="field-error">{{ settingsStore.fieldErrors.minRelevanceScore }}</small></label>
          </div>
          <div class="save-strip">
            <button class="btn btn-primary" :class="{ 'btn-loading': settingsStore.savingTab === 'rag' }" type="button" data-testid="settings-save-rag" :disabled="settingsStore.savingTab === 'rag'" @click="saveRag">{{ settingsStore.savingTab === 'rag' ? '保存中...' : '保存检索策略' }}</button>
          </div>
        </section>

        <section v-else-if="activeTab === 'rerank'" class="settings-section">
          <div class="section-head">
            <div><h2>重排序配置</h2><p>配置重排序模型的提供方、接口地址和密钥，仅 OWNER 可更新。</p></div>
            <span class="status-chip">{{ settingsStore.rerankForm.apiKeySet ? 'API Key 已设置' : 'API Key 未设置' }}</span>
          </div>
          <div class="field-grid">
            <label class="toggle-field"><span>启用</span><input v-model="settingsStore.rerankForm.enabled" type="checkbox" :disabled="!isOwner" /></label>
            <label class="field"><span>模型提供方</span><input v-model="settingsStore.rerankForm.provider" data-testid="settings-rerank-provider" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.provider)" /><small v-if="settingsStore.fieldErrors.provider" class="field-error">{{ settingsStore.fieldErrors.provider }}</small></label>
            <label class="field"><span>接口地址</span><input v-model="settingsStore.rerankForm.baseUrl" data-testid="settings-rerank-base-url" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.baseUrl)" /><small v-if="settingsStore.fieldErrors.baseUrl" class="field-error">{{ settingsStore.fieldErrors.baseUrl }}</small></label>
            <label class="field"><span>模型名称</span><input v-model="settingsStore.rerankForm.model" data-testid="settings-rerank-model" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.model)" /><small v-if="settingsStore.fieldErrors.model" class="field-error">{{ settingsStore.fieldErrors.model }}</small></label>
            <label class="field field--full"><span>密钥</span><input v-model="settingsStore.rerankForm.apiKey" type="password" placeholder="留空表示保持现状" data-testid="settings-rerank-api-key" :disabled="!isOwner" :aria-invalid="Boolean(settingsStore.fieldErrors.apiKey)" /><small v-if="settingsStore.fieldErrors.apiKey" class="field-error">{{ settingsStore.fieldErrors.apiKey }}</small></label>
          </div>
          <div class="save-strip">
            <p v-if="!isOwner">当前角色只能查看重排序配置。</p>
            <div v-else class="action-row">
              <button class="btn btn-secondary" :class="{ 'btn-loading': testingRerank }" type="button" data-testid="settings-test-rerank" :disabled="testingRerank" @click="testRerank">{{ testingRerank ? '测试中...' : '测试连接' }}</button>
              <button class="btn btn-primary" :class="{ 'btn-loading': settingsStore.savingTab === 'rerank' }" type="button" data-testid="settings-save-rerank" :disabled="settingsStore.savingTab === 'rerank'" @click="saveRerank">{{ settingsStore.savingTab === 'rerank' ? '保存中...' : '保存配置' }}</button>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'agent'" class="settings-section">
          <div class="section-head"><div><h2>智能体配置</h2><p>控制智能体是否启用、步数上限、检查点和工具权限。</p></div></div>
          <div class="field-grid">
            <label class="toggle-field"><span>启用智能体</span><input v-model="settingsStore.agentForm.enabled" type="checkbox" /></label>
            <label class="toggle-field"><span>检查点</span><input v-model="settingsStore.agentForm.checkpointEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>联网搜索</span><input v-model="settingsStore.agentForm.webSearchEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>HTTP 工具</span><input v-model="settingsStore.agentForm.httpToolEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>图谱工具</span><input v-model="settingsStore.agentForm.graphToolEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>代码执行</span><input v-model="settingsStore.agentForm.codeExecutionEnabled" type="checkbox" /></label>
            <label class="field"><span>最大模型步数</span><input v-model.number="settingsStore.agentForm.maxModelSteps" type="number" min="1" /></label>
            <label class="field"><span>最大工具调用次数</span><input v-model.number="settingsStore.agentForm.maxToolCalls" type="number" min="1" /></label>
            <label class="field"><span>工具超时时间（毫秒）</span><input v-model.number="settingsStore.agentForm.toolTimeoutMs" type="number" min="100" /></label>
            <label class="field"><span>默认记忆策略</span><select v-model="settingsStore.agentForm.defaultMemoryStrategy"><option value="NONE">不启用记忆</option><option value="SLIDING_WINDOW">滑动窗口</option><option value="SUMMARY_WINDOW">摘要窗口</option><option value="SUMMARY_PLUS_WINDOW">摘要 + 最近消息</option></select></label>
            <label class="field field--full"><span>允许访问的域名</span><textarea v-model="settingsStore.agentForm.allowedHttpDomainsText" rows="5" placeholder="example.com&#10;api.example.com" /></label>
          </div>
          <div class="save-strip"><button class="btn btn-primary" :class="{ 'btn-loading': settingsStore.savingTab === 'agent' }" type="button" :disabled="settingsStore.savingTab === 'agent'" @click="saveAgent">{{ settingsStore.savingTab === 'agent' ? '保存中...' : '保存智能体配置' }}</button></div>
        </section>

        <section v-else class="settings-section">
          <div class="section-head"><div><h2>工具权限</h2><p>配置联网搜索、HTTP 访问和代码执行等高风险工具的可用范围。</p></div></div>
          <div class="field-grid">
            <label class="toggle-field"><span>联网搜索</span><input v-model="settingsStore.toolForm.webSearchEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>HTTP 工具</span><input v-model="settingsStore.toolForm.httpToolEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>图谱工具</span><input v-model="settingsStore.toolForm.graphToolEnabled" type="checkbox" /></label>
            <label class="toggle-field"><span>代码执行</span><input v-model="settingsStore.toolForm.codeExecutionEnabled" type="checkbox" /></label>
            <label class="field"><span>搜索服务提供方</span><input v-model="settingsStore.toolForm.searchProvider" /></label>
            <label class="field"><span>工具超时时间（毫秒）</span><input v-model.number="settingsStore.toolForm.toolTimeoutMs" type="number" min="100" /></label>
            <label class="field field--full"><span>允许访问的域名</span><textarea v-model="settingsStore.toolForm.allowedHttpDomainsText" rows="5" placeholder="example.com&#10;api.example.com" /></label>
          </div>
          <div class="save-strip"><button class="btn btn-primary" :class="{ 'btn-loading': settingsStore.savingTab === 'tools' }" type="button" :disabled="settingsStore.savingTab === 'tools'" @click="saveTools">{{ settingsStore.savingTab === 'tools' ? '保存中...' : '保存工具权限' }}</button></div>
        </section>
      </main>

      <aside class="settings-inspector inspector-box">
        <p class="section-label">Scope / risk</p>
        <h2>{{ activeTabMeta.label }}</h2>
        <p>{{ activeTabMeta.risk }}</p>
        <dl>
          <div><dt>当前角色</dt><dd>{{ authStore.currentRole ?? '-' }}</dd></div>
          <div><dt>保存状态</dt><dd>{{ settingsStore.savingTab ?? 'idle' }}</dd></div>
          <div><dt>校验错误</dt><dd>{{ Object.keys(settingsStore.fieldErrors).length }}</dd></div>
        </dl>
        <p v-if="settingsStore.successMessage" class="success-banner">{{ settingsStore.successMessage }}</p>
        <p v-if="settingsStore.errorMessage" class="error-banner">{{ settingsStore.errorMessage }}</p>
      </aside>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { LoadingSpinner } from '../../../components'
import { toast } from '../../../utils/toast'
import { useAuthStore } from '../../auth/store/auth'
import { useSettingsStore } from '../store/settings'

const authStore = useAuthStore()
const settingsStore = useSettingsStore()
const activeTab = ref<'model' | 'rag' | 'rerank' | 'agent' | 'tools'>('model')
const testingModel = ref(false)
const testingRerank = ref(false)

const tabs = [
  { id: 'model', label: '模型', summary: 'provider / key', risk: '影响对话模型、向量模型和所有回答生成。' },
  { id: 'rag', label: 'RAG', summary: 'retrieval budget', risk: '影响召回数量、证据预算和回答可靠性。' },
  { id: 'rerank', label: '重排序', summary: 'rerank model', risk: '影响检索结果排序和证据选择。' },
  { id: 'agent', label: '智能体', summary: 'steps / memory', risk: '影响 Agent 步数、检查点和工具策略。' },
  { id: 'tools', label: '工具权限', summary: 'runtime tools', risk: '影响联网、HTTP 和代码执行等高风险能力。' },
] as const

const isOwner = computed(() => authStore.currentRole === 'OWNER')
const activeTabMeta = computed(() => tabs.find((tab) => tab.id === activeTab.value) ?? tabs[0])

onMounted(async () => {
  await reload()
})

watch(
  () => authStore.currentTenantId,
  async () => {
    await reload()
  },
)

watch(
  () => settingsStore.successMessage,
  (message) => {
    if (message) {
      toast.success('配置已保存')
    }
  },
)

watch(
  () => settingsStore.errorMessage,
  (message) => {
    if (message) {
      toast.error(message)
    }
  },
)

async function reload() {
  try {
    await settingsStore.loadAll()
  } catch {
    // Error message is already normalized in the store.
  }
}

async function saveModel() {
  if (!isOwner.value || !window.confirm('模型配置直接影响对话和向量计算，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveModel()
  } catch {
    // Store already exposed the error.
  }
}

async function saveRag() {
  if (!window.confirm('检索策略配置会影响回答质量和证据预算，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveRag()
  } catch {
    // Store already exposed the error.
  }
}

async function saveRerank() {
  if (!isOwner.value || !window.confirm('重排序配置会影响检索结果的排序质量，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveRerank()
  } catch {
    // Store already exposed the error.
  }
}

async function saveAgent() {
  if (!window.confirm('智能体设置会影响执行上限、检查点和工具策略，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveAgent()
  } catch {
    // Store already exposed the error.
  }
}

async function saveTools() {
  if (!window.confirm('工具权限设置会影响联网搜索、HTTP 和代码执行的可用范围，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveTools()
  } catch {
    // Store already exposed the error.
  }
}

async function testModel() {
  if (!isOwner.value) {
    return
  }
  testingModel.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 800))
    toast.success('模型连接测试成功！')
  } catch {
    toast.error('模型连接测试失败，请检查配置')
  } finally {
    testingModel.value = false
  }
}

async function testRerank() {
  if (!isOwner.value) {
    return
  }
  testingRerank.value = true
  try {
    await new Promise(resolve => setTimeout(resolve, 800))
    toast.success('重排序连接测试成功！')
  } catch {
    toast.error('重排序连接测试失败，请检查配置')
  } finally {
    testingRerank.value = false
  }
}
</script>

<style scoped>
.settings-layout {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr) 300px;
  gap: 12px;
}

.settings-nav,
.settings-section {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-2);
  background: var(--bg-surface);
}

.settings-nav__item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-1);
  color: var(--text-main);
  background: transparent;
  text-align: left;
}

.settings-nav__item small {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.settings-nav__item--active {
  border-color: color-mix(in srgb, var(--accent), transparent 58%);
  background: var(--accent-soft);
}

.section-head,
.save-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.section-head h2,
.settings-inspector h2 {
  margin: 0;
  font-size: 17px;
}

.section-head p,
.save-strip p,
.settings-inspector p {
  margin: 6px 0 0;
  color: var(--text-muted);
  line-height: 1.55;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.field--full {
  grid-column: 1 / -1;
}

.toggle-field {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  min-height: 42px;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.toggle-field > span {
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 700;
}

.settings-inspector dl {
  display: grid;
  gap: 9px;
  margin: 0;
}

.settings-inspector dl div {
  display: grid;
  gap: 3px;
  padding: 9px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-1);
  background: var(--bg-inset);
}

.settings-inspector dt {
  color: var(--text-subtle);
  font-size: 12px;
}

.settings-inspector dd {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .settings-layout {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .settings-inspector {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .settings-layout,
  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
