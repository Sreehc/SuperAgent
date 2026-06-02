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
          <h3>模型设置</h3>
          <p>OWNER 可修改模型与 embedding 入口，ADMIN 仅查看。</p>
        </div>
        <span class="status-chip">{{ settingsStore.modelForm.apiKeySet ? 'API Key 已设置' : 'API Key 未设置' }}</span>
      </div>

      <div class="field-grid">
        <label class="field">
          <span>Provider</span>
          <input v-model="settingsStore.modelForm.provider" disabled />
        </label>
        <label class="field">
          <span>Base URL</span>
          <input
            v-model="settingsStore.modelForm.baseUrl"
            data-testid="settings-model-base-url"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.baseUrl)"
          />
          <small v-if="settingsStore.fieldErrors.baseUrl" class="field-error" data-testid="settings-error-base-url">{{ settingsStore.fieldErrors.baseUrl }}</small>
        </label>
        <label class="field">
          <span>Chat Model</span>
          <input
            v-model="settingsStore.modelForm.chatModel"
            data-testid="settings-model-chat-model"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.chatModel)"
          />
          <small v-if="settingsStore.fieldErrors.chatModel" class="field-error">{{ settingsStore.fieldErrors.chatModel }}</small>
        </label>
        <label class="field">
          <span>Embedding Model</span>
          <input
            v-model="settingsStore.modelForm.embeddingModel"
            data-testid="settings-model-embedding-model"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.embeddingModel)"
          />
          <small v-if="settingsStore.fieldErrors.embeddingModel" class="field-error">{{ settingsStore.fieldErrors.embeddingModel }}</small>
        </label>
        <label class="field field--full">
          <span>API Key</span>
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
          <h3>RAG 设置</h3>
          <p>OWNER 和 ADMIN 都可以调整检索、预算和 rerank 开关。</p>
        </div>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>Rewrite</span>
          <input v-model="settingsStore.ragForm.rewriteEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Sub Questions</span>
          <input v-model="settingsStore.ragForm.subQuestionEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Rerank</span>
          <input v-model="settingsStore.ragForm.rerankEnabled" type="checkbox" />
        </label>
        <label class="field">
          <span>Max Sub Questions</span>
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
          <span>Vector Top K</span>
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
          <span>Keyword Top K</span>
          <input
            v-model.number="settingsStore.ragForm.keywordTopK"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.keywordTopK)"
          />
          <small v-if="settingsStore.fieldErrors.keywordTopK" class="field-error">{{ settingsStore.fieldErrors.keywordTopK }}</small>
        </label>
        <label class="field">
          <span>RRF K</span>
          <input
            v-model.number="settingsStore.ragForm.rrfK"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.rrfK)"
          />
          <small v-if="settingsStore.fieldErrors.rrfK" class="field-error">{{ settingsStore.fieldErrors.rrfK }}</small>
        </label>
        <label class="field">
          <span>Evidence Limit</span>
          <input
            v-model.number="settingsStore.ragForm.evidenceLimit"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.evidenceLimit)"
          />
          <small v-if="settingsStore.fieldErrors.evidenceLimit" class="field-error">{{ settingsStore.fieldErrors.evidenceLimit }}</small>
        </label>
        <label class="field">
          <span>Per-question Char Limit</span>
          <input
            v-model.number="settingsStore.ragForm.perQuestionEvidenceCharLimit"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.perQuestionEvidenceCharLimit)"
          />
          <small v-if="settingsStore.fieldErrors.perQuestionEvidenceCharLimit" class="field-error">{{ settingsStore.fieldErrors.perQuestionEvidenceCharLimit }}</small>
        </label>
        <label class="field">
          <span>Total Char Limit</span>
          <input
            v-model.number="settingsStore.ragForm.totalEvidenceCharLimit"
            type="number"
            min="1"
            :aria-invalid="Boolean(settingsStore.fieldErrors.totalEvidenceCharLimit)"
          />
          <small v-if="settingsStore.fieldErrors.totalEvidenceCharLimit" class="field-error">{{ settingsStore.fieldErrors.totalEvidenceCharLimit }}</small>
        </label>
        <label class="field">
          <span>Min Relevance Score</span>
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
          {{ settingsStore.savingTab === 'rag' ? '保存中...' : '保存 RAG 设置' }}
        </button>
      </div>
    </section>

    <section v-else-if="activeTab === 'rerank'" class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>Rerank 设置</h3>
          <p>高风险配置，仅 OWNER 可更新；保存前会二次确认。</p>
        </div>
        <span class="status-chip">{{ settingsStore.rerankForm.apiKeySet ? 'API Key 已设置' : 'API Key 未设置' }}</span>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>Enabled</span>
          <input v-model="settingsStore.rerankForm.enabled" type="checkbox" :disabled="!isOwner" />
        </label>
        <label class="field">
          <span>Provider</span>
          <input
            v-model="settingsStore.rerankForm.provider"
            data-testid="settings-rerank-provider"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.provider)"
          />
          <small v-if="settingsStore.fieldErrors.provider" class="field-error">{{ settingsStore.fieldErrors.provider }}</small>
        </label>
        <label class="field">
          <span>Base URL</span>
          <input
            v-model="settingsStore.rerankForm.baseUrl"
            data-testid="settings-rerank-base-url"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.baseUrl)"
          />
          <small v-if="settingsStore.fieldErrors.baseUrl" class="field-error">{{ settingsStore.fieldErrors.baseUrl }}</small>
        </label>
        <label class="field">
          <span>Model</span>
          <input
            v-model="settingsStore.rerankForm.model"
            data-testid="settings-rerank-model"
            :disabled="!isOwner"
            :aria-invalid="Boolean(settingsStore.fieldErrors.model)"
          />
          <small v-if="settingsStore.fieldErrors.model" class="field-error">{{ settingsStore.fieldErrors.model }}</small>
        </label>
        <label class="field field--full">
          <span>API Key</span>
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
        <p v-if="!isOwner" class="caption">当前角色只能查看 rerank 配置。</p>
        <button
          class="pill-button"
          type="button"
          data-testid="settings-save-rerank"
          :disabled="!isOwner || settingsStore.savingTab === 'rerank'"
          @click="saveRerank"
        >
          {{ settingsStore.savingTab === 'rerank' ? '保存中...' : '保存 Rerank 设置' }}
        </button>
      </div>
    </section>

    <section v-else-if="activeTab === 'agent'" class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>Agent 设置</h3>
          <p>控制 Agent 是否启用、步数上限、Checkpoint 和默认记忆策略。</p>
        </div>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>Enabled</span>
          <input v-model="settingsStore.agentForm.enabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Checkpoint</span>
          <input v-model="settingsStore.agentForm.checkpointEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Web Search</span>
          <input v-model="settingsStore.agentForm.webSearchEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>HTTP Tool</span>
          <input v-model="settingsStore.agentForm.httpToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Graph Tool</span>
          <input v-model="settingsStore.agentForm.graphToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Code Execution</span>
          <input v-model="settingsStore.agentForm.codeExecutionEnabled" type="checkbox" />
        </label>
        <label class="field">
          <span>Max Model Steps</span>
          <input v-model.number="settingsStore.agentForm.maxModelSteps" type="number" min="1" />
        </label>
        <label class="field">
          <span>Max Tool Calls</span>
          <input v-model.number="settingsStore.agentForm.maxToolCalls" type="number" min="1" />
        </label>
        <label class="field">
          <span>Tool Timeout (ms)</span>
          <input v-model.number="settingsStore.agentForm.toolTimeoutMs" type="number" min="100" />
        </label>
        <label class="field">
          <span>Default Memory Strategy</span>
          <select v-model="settingsStore.agentForm.defaultMemoryStrategy">
            <option value="NONE">NONE</option>
            <option value="SLIDING_WINDOW">SLIDING_WINDOW</option>
            <option value="SUMMARY_WINDOW">SUMMARY_WINDOW</option>
            <option value="SUMMARY_PLUS_WINDOW">SUMMARY_PLUS_WINDOW</option>
          </select>
        </label>
        <label class="field field--full">
          <span>Allowed HTTP Domains</span>
          <textarea
            v-model="settingsStore.agentForm.allowedHttpDomainsText"
            rows="5"
            placeholder="example.com&#10;api.example.com"
          />
        </label>
      </div>

      <div class="panel__footer">
        <button class="pill-button" type="button" :disabled="settingsStore.savingTab === 'agent'" @click="saveAgent">
          {{ settingsStore.savingTab === 'agent' ? '保存中...' : '保存 Agent 设置' }}
        </button>
      </div>
    </section>

    <section v-else class="card-shell panel">
      <div class="panel__header">
        <div>
          <h3>Tools 设置</h3>
          <p>控制搜索 provider、HTTP allowlist 和高风险工具开关。</p>
        </div>
      </div>

      <div class="field-grid">
        <label class="toggle-field">
          <span>Web Search</span>
          <input v-model="settingsStore.toolForm.webSearchEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>HTTP Tool</span>
          <input v-model="settingsStore.toolForm.httpToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Graph Tool</span>
          <input v-model="settingsStore.toolForm.graphToolEnabled" type="checkbox" />
        </label>
        <label class="toggle-field">
          <span>Code Execution</span>
          <input v-model="settingsStore.toolForm.codeExecutionEnabled" type="checkbox" />
        </label>
        <label class="field">
          <span>Search Provider</span>
          <input v-model="settingsStore.toolForm.searchProvider" />
        </label>
        <label class="field">
          <span>Tool Timeout (ms)</span>
          <input v-model.number="settingsStore.toolForm.toolTimeoutMs" type="number" min="100" />
        </label>
        <label class="field field--full">
          <span>Allowed HTTP Domains</span>
          <textarea
            v-model="settingsStore.toolForm.allowedHttpDomainsText"
            rows="5"
            placeholder="example.com&#10;api.example.com"
          />
        </label>
      </div>

      <div class="panel__footer">
        <button class="pill-button" type="button" :disabled="settingsStore.savingTab === 'tools'" @click="saveTools">
          {{ settingsStore.savingTab === 'tools' ? '保存中...' : '保存 Tools 设置' }}
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
  { id: 'model', label: '模型' },
  { id: 'rag', label: 'RAG' },
  { id: 'rerank', label: 'Rerank' },
  { id: 'agent', label: 'Agent' },
  { id: 'tools', label: 'Tools' },
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
  if (!window.confirm('模型配置会直接影响对话与 Embedding 调用，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveModel()
  } catch {
    // Store already exposed the error.
  }
}

async function saveRag() {
  if (!window.confirm('RAG 配置会影响检索、证据预算和回答质量，确认保存吗？')) {
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
  if (!window.confirm('Rerank 配置会影响召回后排序，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveRerank()
  } catch {
    // Store already exposed the error.
  }
}

async function saveAgent() {
  if (!window.confirm('Agent 设置会影响执行上限、Checkpoint 和工具策略，确认保存吗？')) {
    return
  }
  try {
    await settingsStore.saveAgent()
  } catch {
    // Store already exposed the error.
  }
}

async function saveTools() {
  if (!window.confirm('Tools 设置会影响联网、HTTP 和代码执行权限，确认保存吗？')) {
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
.field textarea,
.toggle-field input {
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
