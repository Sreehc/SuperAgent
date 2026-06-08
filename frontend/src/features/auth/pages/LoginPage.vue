<template>
  <main class="login-page">
    <section class="access-console">
      <BrandLogo size="large" show-text animated />
      <div class="access-console__copy">
        <p class="section-label">Agent/RAG operations console</p>
        <h1>SuperAgent control tower</h1>
        <p>统一进入企业知识、对话运行、证据链路和运行时配置。</p>
      </div>
      <div class="system-strips" aria-label="系统状态">
        <article v-for="item in statusStrips" :key="item.label" class="system-strip">
          <span :class="`system-strip__dot system-strip__dot--${item.tone}`"></span>
          <div>
            <strong>{{ item.label }}</strong>
            <p>{{ item.value }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="auth-dock" aria-label="登录表单">
      <form class="auth-module" @submit.prevent="submit">
        <div class="auth-module__header">
          <p class="section-label">Secure access</p>
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
          class="btn btn-primary btn-lg auth-module__submit"
          :class="{ 'btn-loading': submitting }"
          data-testid="login-submit"
          :disabled="submitting"
          type="submit"
        >
          {{ submitting ? '登录中...' : '进入工作台' }}
        </button>

        <div class="auth-module__hint">
          <span>测试账号</span>
          <code>admin / password123</code>
          <code>member / password123</code>
        </div>
      </form>
      <p class="auth-dock__env">localhost tenant runtime / build target api-v1</p>
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

const statusStrips = [
  { label: 'Knowledge index', value: 'ready for tenant-scoped retrieval', tone: 'ok' },
  { label: 'Trace capture', value: 'model calls and stages recorded', tone: 'info' },
  { label: 'Tenant access', value: 'role-gated console modules', tone: 'hot' },
]

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
  grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.65fr);
  min-height: 100vh;
  background: var(--bg-canvas);
}

.access-console,
.auth-dock {
  display: grid;
  align-content: center;
  padding: clamp(24px, 5vw, 72px);
}

.access-console {
  position: relative;
  gap: 34px;
  overflow: hidden;
  color: #eef2ec;
  background:
    radial-gradient(circle at 18% 18%, rgba(123, 210, 179, 0.18), transparent 28%),
    linear-gradient(135deg, #101417, #17201b 52%, #0b0f0d);
}

.access-console::before {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(238, 242, 236, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(238, 242, 236, 0.04) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.8), transparent);
  content: "";
}

.access-console > * {
  position: relative;
}

.access-console :deep(.product-mark) {
  color: #eef2ec;
}

.access-console :deep(.product-mark__copy small) {
  color: rgba(238, 242, 236, 0.58);
}

.access-console__copy {
  display: grid;
  gap: 12px;
  max-width: 760px;
}

.access-console h1 {
  max-width: 11ch;
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(48px, 7vw, 92px);
  line-height: 0.92;
  letter-spacing: 0;
}

.access-console p {
  max-width: 56ch;
  margin: 0;
  color: rgba(238, 242, 236, 0.68);
  font-size: 16px;
  line-height: 1.65;
}

.system-strips {
  display: grid;
  gap: 10px;
  max-width: 720px;
}

.system-strip {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 13px 14px;
  border: 1px solid rgba(238, 242, 236, 0.12);
  border-radius: var(--radius-2);
  background: rgba(238, 242, 236, 0.045);
  backdrop-filter: blur(10px);
}

.system-strip__dot {
  width: 9px;
  height: 9px;
  margin-top: 5px;
  border-radius: 999px;
  background: #7bd2b3;
  box-shadow: 0 0 0 5px rgba(123, 210, 179, 0.13);
}

.system-strip__dot--info {
  background: #86bdd0;
}

.system-strip__dot--hot {
  background: #ff9b69;
  box-shadow: 0 0 0 5px rgba(255, 155, 105, 0.13);
}

.system-strip strong {
  display: block;
  color: #ffffff;
  font-size: 14px;
}

.system-strip p {
  margin-top: 4px;
  font-family: var(--font-mono);
  font-size: 12px;
}

.auth-dock {
  align-content: center;
  gap: 14px;
}

.auth-module {
  display: grid;
  gap: 16px;
  width: 100%;
  max-width: 430px;
  padding: 22px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius-3);
  background: var(--bg-lift);
  box-shadow: var(--shadow-lift);
}

.auth-module__header h2 {
  margin: 4px 0 0;
  font-size: 25px;
}

.auth-module__submit {
  width: 100%;
}

.auth-module__hint {
  display: grid;
  gap: 6px;
  padding-top: 14px;
  border-top: 1px solid var(--line-soft);
  color: var(--text-muted);
  font-size: 13px;
}

.auth-module__hint code {
  color: var(--text-main);
}

.auth-dock__env {
  max-width: 430px;
  margin: 0;
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 11px;
}

@media (max-width: 860px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .access-console {
    min-height: 48vh;
  }

  .auth-dock {
    align-content: start;
  }
}
</style>
