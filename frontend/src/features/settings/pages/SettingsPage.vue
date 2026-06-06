<template>
  <section class="settings-page">
    <header class="card-shell page-header">
      <div>
        <p class="eyebrow">/settings</p>
        <h2>运行时设置</h2>
        <p>模型、RAG、Agent 和工具策略统一在这里维护，密钥只展示“已设置”状态。</p>
      </div>
      <button class="ghost-button" type="button" data-testid="settings-refresh" @click="reload">刷新</button>
    </header>

    <section class="card-shell tabs">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        class="tab-button"
        :class="{ 'tab-button--active': activeTab === tab.id }"
        type="button"
        @click="activeTab = tab.id"
      >
        {{ tab.label }}
      </button>
    </section>

    <p v-if="settingsStore.errorMessage" class="banner banner--error">{{ settingsStore.errorMessage }}</p>
    <p v-if="settingsStore.successMessage" class="banner banner--success">{{ settingsStore.successMessage }}</p>

    <section v-if="settingsStore.loading" class="card-shell">正在加载设置...</section>

    <section v-else-if="activeTab === 'model'" class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>模型配置</h3>
          <p>配置对话模型和向量模型，提供方与密钥由 OWNER 维护，ADMIN 仅可查看。</p>
        </div>
        <span class="status-chip">{{ settingsStore.modelForm.apiKeySet ? '密钥已设置' : '密钥未设置' }}</span>
      </div>

      <div class="field-grid">
        <label class="field">
          <span>模型提供方</span>
          <input v-model="settingsStore.modelForm.provider" disabled />
        </label>
        <label class="field">
          <span>接口地址</span>
          <input
            v-model="settingsStore.modelForm.baseUrl"
            data-testid="settings-model-base-url"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.baseUrl)"
          />
          <small v-if="settingsStore.fieldErrors.baseUrl" class="field-error" data-testid="settings-error-base-url">{{ settingsStore.fieldErrors.baseUrl }}</small>
        </label>
        <label class="field">
          <span>对话模型</span>
          <input
            v-model="settingsStore.modelForm.chatModel"
            data-testid="settings-model-chat-model"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.chatModel)"
          />
          <small v-if="settingsStore.fieldErrors.chatModel" class="field-error">{{ settingsStore.fieldErrors.chatModel }}</small>
        </label>
        <label class="field">
          <span>向量模型</span>
          <input
            v-model="settingsStore.modelForm.embeddingModel"
            data-testid="settings-model-embedding-model"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.embeddingModel)"
          />
          <small v-if="settingsStore.fieldErrors.embeddingModel" class="field-error">{{ settingsStore.fieldErrors.embeddingModel }}</small>
        </label>
        <label class="field field--full">
          <span>密钥</span>
          <input
            v-model="settingsStore.modelForm.apiKey"
            type="password"
            placeholder="留空表示保持现状"
            data-testid="settings-model-api-key"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.apiKey)"
          />
          <small v-if="settingsStore.fieldErrors.apiKey" class="field-error">{{ settingsStore.fieldErrors.apiKey }}</small>
        </label>
      </div>

      <div class="panel__footer">
        <p v-if="!isOwner" class="caption">当前角色只能查看模型配置。</p>
        <button
          class="pill-button"
          type="button"
          data-testid="settings-save-model"
          :disabled="!isOwner || settingsStore.savingTab === 'model'"
          @click="saveModel"
        >
          {{ settingsStore.savingTab === 'model' ? '保存中...' : '保存模型设置' }}
        </button>
      </div>
    </section>

    <section v-else-if="activeTab === 'rag'" class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>检索策略</h3>
          <p>配置检索优先级、证据预算和重排序开关，影响回答质量和召回效果。</p>
        </div>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>问题改写</span>
          <input v-model="settingsStore.ragForm.rewriteEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>子问题拆分</span>
          <input v-model="settingsStore.ragForm.subQuestionEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>重排序</span>
          <input v-model="settingsStore.ragForm.rerankEnabled" type="checkbox" />
        </label>
        <label class="field">
          <span>最大子问题数</span>
          <input
            v-model.number="settingsStore.ragForm.maxSubQuestions"
            data-testid="settings-rag-max-sub-questions"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.maxSubQuestions)"
          />
          <small v-if="settingsStore.fieldErrors.maxSubQuestions" class="field-error">{{ settingsStore.fieldErrors.maxSubQuestions }}</small>
        </label>
        <label class="field">
          <span>向量检索条数</span>
          <input
            v-model.number="settingsStore.ragForm.vectorTopK"
            type="number"
            min="1"
            data-testid="settings-rag-vector-top-k"
            :aria-invalid="Boolean(settingsStore.fieldErrors.vectorTopK)"
          />
          <small v-if="settingsStore.fieldErrors.vectorTopK" class="field-error" data-testid="settings-error-vector-top-k">{{ settingsStore.fieldErrors.vectorTopK }}</small>
        </label>
        <label class="field">
          <span>关键词检索条数</span>
          <input
            v-model.number="settingsStore.ragForm.keywordTopK"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.keywordTopK)"
          />
          <small v-if="settingsStore.fieldErrors.keywordTopK" class="field-error">{{ settingsStore.fieldErrors.keywordTopK }}</small>
        </label>
        <label class="field">
          <span>RRF 融合系数</span>
          <input
            v-model.number="settingsStore.ragForm.rrfK"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.rrfK)"
          />
          <small v-if="settingsStore.fieldErrors.rrfK" class="field-error">{{ settingsStore.fieldErrors.rrfK }}</small>
        </label>
        <label class="field">
          <span>证据条数上限</span>
          <input
            v-model.number="settingsStore.ragForm.evidenceLimit"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.evidenceLimit)"
          />
          <small v-if="settingsStore.fieldErrors.evidenceLimit" class="field-error">{{ settingsStore.fieldErrors.evidenceLimit }}</small>
        </label>
        <label class="field">
          <span>单问题字符上限</span>
          <input
            v-model.number="settingsStore.ragForm.perQuestionEvidenceCharLimit"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.perQuestionEvidenceCharLimit)"
          />
          <small v-if="settingsStore.fieldErrors.perQuestionEvidenceCharLimit" class="field-error">{{ settingsStore.fieldErrors.perQuestionEvidenceCharLimit }}</small>
        </label>
        <label class="field">
          <span>总字符上限</span>
          <input
            v-model.number="settingsStore.ragForm.totalEvidenceCharLimit"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.totalEvidenceCharLimit)"
          />
          <small v-if="settingsStore.fieldErrors.totalEvidenceCharLimit" class="field-error">{{ settingsStore.fieldErrors.totalEvidenceCharLimit }}</small>
        </label>
        <label class="field">
          <span>最低相关度分数</span>
          <input
            v-model.number="settingsStore.ragForm.minRelevanceScore"
            type="number"
            min="0"
            step="0.01"
            :aria-invalid="Boolean(settingsStore.fieldErrors.minRelevanceScore)"
          />
          <small v-if="settingsStore.fieldErrors.minRelevanceScore" class="field-error">{{ settingsStore.fieldErrors.minRelevanceScore }}</small>
        </label>
      </div>

      <div class="panel__footer">
        <button
          class="pill-button"
          type="button"
          data-testid="settings-save-rag"
          :disabled="settingsStore.savingTab === 'rag'"
          @click="saveRag"
        >
          {{ settingsStore.savingTab === 'rag' ? '保存中...' : '保存检索策略' }}
        </button>
      </div>
    </section>

    <section v-else-if="activeTab === 'rerank'" class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>重排序配置</h3>
          <p>配置重排序模型的提供方、接口地址和密钥，仅 OWNER 可更新。</p>
        </div>
        <span class="status-chip">{{ settingsStore.rerankForm.apiKeySet ? '密钥已设置' : '密钥未设置' }}</span>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>启用</span>
          <input v-model="settingsStore.rerankForm.enabled" type="checkbox" :disabled="!isOwner" />
        </label>
        <label class="field">
          <span>模型提供方</span>
          <input
            v-model="settingsStore.rerankForm.provider"
            data-testid="settings-rerank-provider"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.provider)"
          />
          <small v-if="settingsStore.fieldErrors.provider" class="field-error">{{ settingsStore.fieldErrors.provider }}</small>
        </label>
        <label class="field">
          <span>接口地址</span>
          <input
            v-model="settingsStore.rerankForm.baseUrl"
            data-testid="settings-rerank-base-url"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.baseUrl)"
          />
          <small v-if="settingsStore.fieldErrors.baseUrl" class="field-error">{{ settingsStore.fieldErrors.baseUrl }}</small>
        </label>
        <label class="field">
          <span>模型名称</span>
          <input
            v-model="settingsStore.rerankForm.model"
            data-testid="settings-rerank-model"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.model)"
          />
          <small v-if="settingsStore.fieldErrors.model" class="field-error">{{ settingsStore.fieldErrors.model }}</small>
        </label>
        <label class="field field--full">
          <span>密钥</span>
          <input
            v-model="settingsStore.rerankForm.apiKey"
            type="password"
            placeholder="留空表示保持现状"
            data-testid="settings-rerank-api-key"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.apiKey)"
          />
          <small v-if="settingsStore.fieldErrors.apiKey" class="field-error">{{ settingsStore.fieldErrors.apiKey }}</small>
        </label>
      </div>

      <div class="panel__footer">
        <p v-if="!isOwner" class="caption">当前角色只能查看重排序配置。</p>
        <button
          class="pill-button"
          type="button"
          data-testid="settings-save-rerank"
          :disabled="!isOwner || settingsStore.savingTab === 'rerank'"
          @click="saveRerank"
        >
          {{ settingsStore.savingTab === 'rerank' ? '保存中...' : '保存重排序配置' }}
        </button>
      </div>
    </section>

    <section v-else-if="activeTab === 'agent'" class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>智能体配置</h3>
          <p>控制智能体是否启用、步数上限、检查点和工具权限。</p>
        </div>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>启用智能体</span>
          <input v-model="settingsStore.agentForm.enabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>检查点</span>
          <input v-model="settingsStore.agentForm.checkpointEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>联网搜索</span>
          <input v-model="settingsStore.agentForm.webSearchEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>HTTP 工具</span>
          <input v-model="settingsStore.agentForm.httpToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>图谱工具</span>
          <input v-model="settingsStore.agentForm.graphToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>代码执行</span>
          <input v-model="settingsStore.agentForm.codeExecutionEnabled" type="checkbox" />
        </label>
        <label class="field">
          <span>最大模型步数</span>
          <input v-model.number="settingsStore.agentForm.maxModelSteps" type="number" min="1" />
        </label>
        <label class="field">
          <span>最大工具调用次数</span>
          <input v-model.number="settingsStore.agentForm.maxToolCalls" type="number" min="1" />
        </label>
        <label class="field">
          <span>工具超时时间（毫秒）</span>
          <input v-model.number="settingsStore.agentForm.toolTimeoutMs" type="number" min="100" />
        </label>
        <label class="field">
          <span>默认记忆策略</span>
          <select v-model="settingsStore.agentForm.defaultMemoryStrategy">
            <option value="NONE">不启用记忆</option>
            <option value="SLIDING_WINDOW">滑动窗口</option>
            <option value="SUMMARY_WINDOW">摘要窗口</option>
            <option value="SUMMARY_PLUS_WINDOW">摘要 + 最近消息</option>
          </select>
        </label>
        <label class="field field--full">
          <span>允许访问的域名</span>
          <textarea
            v-model="settingsStore.agentForm.allowedHttpDomainsText"
            rows="5"
            placeholder="example.com&#10;api.example.com"
          />
        </label>
      </div>

      <div class="panel__footer">
        <button class="pill-button" type="button" :disabled="settingsStore.savingTab === 'agent'" @click="saveAgent">
          {{ settingsStore.savingTab === 'agent' ? '保存中...' : '保存智能体配置' }}
        </button>
      </div>
    </section>

    <section v-else class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>工具权限</h3>
          <p>配置联网搜索、HTTP 访问和代码执行等高风险工具的可用范围。</p>
        </div>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>联网搜索</span>
          <input v-model="settingsStore.toolForm.webSearchEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>HTTP 工具</span>
          <input v-model="settingsStore.toolForm.httpToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>图谱工具</span>
          <input v-model="settingsStore.toolForm.graphToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>代码执行</span>
          <input v-model="settingsStore.toolForm.codeExecutionEnabled" type="checkbox" />
        </label>
        <label class="field">
          <span>搜索服务提供方</span>
          <input v-model="settingsStore.toolForm.searchProvider" />
        </label>
        <label class="field">
          <span>工具超时时间（毫秒）</span>
          <input v-model.number="settingsStore.toolForm.toolTimeoutMs" type="number" min="100" />
        </label>
        <label class="field field--full">
          <span>允许访问的域名</span>
          <textarea
            v-model="settingsStore.toolForm.allowedHttpDomainsText"
            rows="5"
            placeholder="example.com&#10;api.example.com"
          />
        </label>
      </div>

      <div class="panel__footer">
        <button class="pill-button" type="button" :disabled="settingsStore.savingTab === 'tools'" @click="saveTools">
          {{ settingsStore.savingTab === 'tools' ? '保存中...' : '保存工具权限' }}
        </button>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '../../auth/store/auth'
import { useSettingsStore } from '../store/settings'

const authStore = useAuthStore()
const settingsStore = useSettingsStore()
const activeTab = ref<'model' | 'rag' | 'rerank' | 'agent' | 'tools'>('model')

const tabs = [
  { id: 'model', label: '模型配置' },
  { id: 'rag', label: '检索策略' },
  { id: 'rerank', label: '重排序' },
  { id: 'agent', label: '智能体' },
  { id: 'tools', label: '工具权限' },
] as const

const isOwner = computed(() => authStore.currentRole === 'OWNER')

onMounted(async () => {
  await reload()
})

watch(
  () => authStore.currentTenantId,
  async () => {
    await reload()
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
  if (!isOwner.value) {
    return
  }
  if (!window.confirm('模型配置直接影响对话和向量计算，确认保存吗？')) {
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
  if (!isOwner.value) {
    return
  }
  if (!window.confirm('重排序配置会影响检索结果的排序质量，确认保存吗？')) {
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
</script>

<style scoped>
.settings-page {
  display: grid;
  gap: 1rem;
}

.card-shell {
  border-radius: calc(var(--radius-md) + 4px);
  border: 1px solid var(--line-soft);
  background: var(--bg-panel);
  box-shadow: var(--shadow-soft);
  padding: 1rem 1.2rem;
}

.page-header,
.panel__header,
.panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.page-header h2,
.panel h3 {
  margin: 0.2rem 0;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
}

.tabs {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.tab-button,
.ghost-button,
.pill-button {
  border-radius: 999px;
  padding: 0.75rem 1rem;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.78);
}

.tab-button--active {
  background: rgba(199, 109, 63, 0.12);
  border-color: rgba(199, 109, 63, 0.36);
}

.pill-button {
  border: 0;
  background: linear-gradient(135deg, var(--bg-accent), #d78655);
  color: var(--text-contrast);
}

.panel {
  display: grid;
  gap: 1rem;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.9rem;
}

.field,
.toggle-field {
  display: grid;
  gap: 0.45rem;
}

.field-error {
  color: var(--danger);
  font-size: 0.82rem;
}

.field--full {
  grid-column: 1 / -1;
}

.field input,
.field select,
.field textarea {
  padding: 0.85rem 0.95rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.84);
}

.field textarea {
  resize: vertical;
}

.toggle-field {
  grid-template-columns: 1fr auto;
  align-items: center;
}

.status-chip {
  display: inline-flex;
  padding: 0.2rem 0.65rem;
  border-radius: 999px;
  background: rgba(27, 47, 61, 0.08);
  color: var(--text-secondary);
}

.banner {
  margin: 0;
  padding: 0.9rem 1rem;
  border-radius: var(--radius-sm);
}

.banner--error {
  background: rgba(179, 63, 45, 0.08);
  color: var(--danger);
}

.banner--success {
  background: rgba(45, 106, 79, 0.1);
  color: var(--success);
}

.caption,
.eyebrow {
  margin: 0;
  color: var(--text-secondary);
}

.eyebrow {
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

@media (max-width: 960px) {
  .page-header,
  .panel__header,
  .panel__footer {
    flex-direction: column;
    align-items: stretch;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
