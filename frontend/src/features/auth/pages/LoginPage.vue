<template>
  <div class="login-page">
    <section class="login-story">
      <div class="login-story__panel">
        <p class="login-story__eyebrow">SuperAgent</p>
        <h1>先登录，再直接进入 AI 工作台。</h1>
        <p class="login-story__body">
          当前阶段只接入登录态、基础布局和角色菜单控制。登录成功后会直接落到
          <code>/chat</code> 占位页。
        </p>

        <div class="login-story__chips">
          <span>Vue 3 + Vite</span>
          <span>Pinia 状态层</span>
          <span>JWT + 租户上下文</span>
        </div>
      </div>
    </section>

    <section class="login-form-wrap">
      <form class="login-form" @submit.prevent="submit">
        <div class="login-form__header">
          <p>登录</p>
          <h2>进入默认租户下的工作台</h2>
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

        <p v-if="errorMessage" class="login-form__error">{{ errorMessage }}</p>

        <button class="login-form__submit" data-testid="login-submit" :disabled="submitting" type="submit">
          {{ submitting ? '登录中...' : '登录并进入 /chat' }}
        </button>

        <div class="login-form__hint">
          <strong>本地种子账号</strong>
          <span>admin / password123</span>
          <span>member / password123</span>
        </div>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
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
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 520px);
}

.login-story,
.login-form-wrap {
  display: grid;
  place-items: center;
  padding: 2rem;
}

.login-story {
  background:
    linear-gradient(145deg, rgba(19, 45, 64, 0.94), rgba(33, 58, 78, 0.92)),
    radial-gradient(circle at top right, rgba(199, 109, 63, 0.26), transparent 30%);
  color: var(--text-contrast);
}

.login-story__panel {
  max-width: 560px;
  padding: 3rem;
}

.login-story__eyebrow {
  margin: 0;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  font-size: 0.74rem;
  color: rgba(255, 247, 239, 0.66);
}

.login-story h1 {
  margin: 0.6rem 0 1rem;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
  font-size: clamp(2.4rem, 4vw, 4.6rem);
  line-height: 0.98;
}

.login-story__body {
  margin: 0;
  max-width: 42ch;
  color: rgba(255, 247, 239, 0.78);
}

.login-story__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin-top: 1.8rem;
}

.login-story__chips span {
  padding: 0.65rem 0.95rem;
  border-radius: 999px;
  border: 1px solid rgba(255, 247, 239, 0.16);
  background: rgba(255, 255, 255, 0.05);
}

.login-form {
  width: min(100%, 440px);
  padding: 2rem;
  border-radius: calc(var(--radius-lg) + 4px);
  background: var(--bg-panel-strong);
  box-shadow: var(--shadow-card);
  border: 1px solid rgba(36, 33, 27, 0.09);
}

.login-form__header p {
  margin: 0;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.14em;
  font-size: 0.74rem;
}

.login-form__header h2 {
  margin: 0.4rem 0 1.4rem;
  font-family: 'Fraunces', 'Iowan Old Style', serif;
  font-size: 2rem;
  line-height: 1.05;
}

.field {
  display: grid;
  gap: 0.55rem;
  margin-bottom: 1rem;
}

.field span {
  color: var(--text-secondary);
}

.field input {
  width: 100%;
  padding: 0.95rem 1rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.88);
}

.field input:focus {
  outline: 2px solid rgba(199, 109, 63, 0.24);
  border-color: rgba(199, 109, 63, 0.5);
}

.login-form__submit {
  width: 100%;
  margin-top: 0.5rem;
  border: 0;
  border-radius: 999px;
  padding: 0.95rem 1.2rem;
  background: linear-gradient(135deg, var(--bg-accent), #d78655);
  color: var(--text-contrast);
  font-weight: 600;
  box-shadow: 0 16px 30px rgba(199, 109, 63, 0.24);
}

.login-form__submit:disabled {
  opacity: 0.7;
}

.login-form__error {
  margin: 0.25rem 0 0;
  color: var(--danger);
}

.login-form__hint {
  display: grid;
  gap: 0.25rem;
  margin-top: 1.4rem;
  padding-top: 1rem;
  border-top: 1px dashed var(--line-soft);
  color: var(--text-secondary);
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-story__panel,
  .login-form {
    padding: 1.6rem;
  }
}
</style>
