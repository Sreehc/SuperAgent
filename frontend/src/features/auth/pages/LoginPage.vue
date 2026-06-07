<template>
  <main class="login-page">
    <section class="login-brief">
      <BrandLogo size="large" show-text />
      <div class="login-brief__copy">
        <p class="page-kicker">Agent/RAG operations console</p>
        <h1>进入 SuperAgent 工作台</h1>
        <p>管理租户知识库、运行对话、追踪模型链路和调整运行时配置。</p>
      </div>
      <dl class="login-brief__metrics">
        <div>
          <dt>Modules</dt>
          <dd>Chat / KB / Trace</dd>
        </div>
        <div>
          <dt>Access</dt>
          <dd>Tenant roles</dd>
        </div>
        <div>
          <dt>Runtime</dt>
          <dd>RAG + Agent</dd>
        </div>
      </dl>
    </section>

    <section class="login-panel" aria-label="登录表单">
      <form class="login-form" @submit.prevent="submit">
        <div class="login-form__header">
          <p class="page-kicker">Sign in</p>
          <h2>账号登录</h2>
        </div>

        <label class="field">
          <span>用户名</span>
          <input v-model.trim="form.username" data-testid="login-username" name="username" autocomplete="username" placeholder="admin" />
        </label>

        <label class="field">
          <span>密码</span>
          <input
            v-model="form.password"
            data-testid="login-password"
            name="password"
            type="password"
            autocomplete="current-password"
            placeholder="password123"
          />
        </label>

        <p v-if="errorMessage" class="error-banner">{{ errorMessage }}</p>

        <button
          class="btn btn-primary btn-lg login-form__submit"
          :class="{ 'btn-loading': submitting }"
          data-testid="login-submit"
          :disabled="submitting"
          type="submit"
        >
          {{ submitting ? '登录中...' : '登录' }}
        </button>

        <div class="login-form__hint">
          <span>测试账号</span>
          <code>admin / password123</code>
          <code>member / password123</code>
        </div>
      </form>
    </section>
  </main>
</template>

<script setup lang="ts">
import axios from 'axios'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BrandLogo } from '../../../components'
import { useAuthStore } from '../store/auth'

const router = useRouter()
const authStore = useAuthStore()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: 'admin',
  password: 'password123',
})

async function submit() {
  errorMessage.value = ''
  submitting.value = true

  try {
    await authStore.login({
      username: form.username,
      password: form.password,
    })
    await router.replace(authStore.consumeRedirect())
  } catch (error) {
    if (axios.isAxiosError(error)) {
      errorMessage.value = error.response?.data?.message ?? '登录失败，请检查账号或密码。'
    } else {
      errorMessage.value = '登录失败，请稍后重试。'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 460px);
  min-height: 100vh;
}

.login-brief,
.login-panel {
  display: grid;
  align-content: center;
  padding: clamp(24px, 5vw, 72px);
}

.login-brief {
  gap: 34px;
  border-right: 1px solid var(--color-border);
  background: var(--color-surface);
}

.login-brief__copy {
  display: grid;
  gap: 12px;
  max-width: 620px;
}

.login-brief h1 {
  max-width: 12ch;
  margin: 0;
  font-size: clamp(36px, 6vw, 68px);
  line-height: 0.98;
  letter-spacing: -0.05em;
}

.login-brief p {
  max-width: 56ch;
  margin: 0;
  color: var(--color-text-muted);
  font-size: 16px;
  line-height: 1.7;
}

.login-brief__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  max-width: 680px;
  margin: 0;
}

.login-brief__metrics div {
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-muted);
}

.login-brief__metrics dt {
  margin-bottom: 8px;
  color: var(--color-text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 800;
}

.login-brief__metrics dd {
  margin: 0;
  font-weight: 760;
}

.login-panel {
  background: var(--color-bg);
}

.login-form {
  display: grid;
  gap: 16px;
  width: 100%;
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-raised);
}

.login-form__header h2 {
  margin: 0;
  font-size: 24px;
}

.login-form__submit {
  width: 100%;
}

.login-form__hint {
  display: grid;
  gap: 6px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-muted);
  font-size: 13px;
}

.login-form__hint code {
  color: var(--color-text);
}

@media (max-width: 860px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-brief {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .login-brief__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
