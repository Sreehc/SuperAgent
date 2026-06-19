import { useState, type FormEvent } from 'react'
import { ArrowRight, CheckCircle, Database, GitBranch, LockKey, Pulse } from '@phosphor-icons/react'
import { useNavigate } from 'react-router-dom'
import { BrandLogo } from '@/components/BrandLogo'
import { Button } from '@/shared/ui'
import { useAuthStore } from '../store/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await useAuthStore.getState().login({ username, password })
      navigate(useAuthStore.getState().consumeRedirect(), { replace: true })
    } catch {
      setError('登录失败，请检查用户名或密码。')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-panel">
        <section className="login-brief" aria-label="SuperAgent 控制台概览">
          <div className="login-brief__brand">
            <BrandLogo size="large" />
            <div>
              <p className="section-label">SuperAgent</p>
              <h1>Agent 运行控制台</h1>
            </div>
          </div>

          <div className="login-brief__copy">
            <p>统一管理对话执行、知识库、Trace 与租户设置。</p>
            <p>保持冷静、可追踪、面向工程团队的工作台体验。</p>
          </div>

          <div className="login-signal-grid" aria-hidden="true">
            <div className="login-signal login-signal--wide">
              <Pulse size={18} weight="duotone" />
              <span>Runtime</span>
              <strong>healthy</strong>
            </div>
            <div className="login-signal">
              <GitBranch size={18} weight="duotone" />
              <span>Runs</span>
              <strong>traceable</strong>
            </div>
            <div className="login-signal">
              <Database size={18} weight="duotone" />
              <span>Knowledge</span>
              <strong>indexed</strong>
            </div>
          </div>
        </section>

        <form className="login-card" onSubmit={onSubmit}>
          <div className="login-card__brand">
            <div>
              <p className="section-label">Sign in</p>
              <h2>进入控制台</h2>
            </div>
            <span className="login-card__lock" aria-hidden="true">
              <LockKey size={20} weight="duotone" />
            </span>
          </div>

          <label className="field">
            <span>用户名</span>
            <input
              data-testid="login-username"
              value={username}
              autoComplete="username"
              onChange={(event) => setUsername(event.target.value)}
              required
            />
          </label>

          <label className="field">
            <span>密码</span>
            <input
              data-testid="login-password"
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>

          {error && <p className="error-banner">{error}</p>}

          <Button data-testid="login-submit" type="submit" variant="primary" size="lg" loading={submitting}>
            登录
            <ArrowRight size={16} aria-hidden="true" />
          </Button>

          <div className="login-card__assurance" aria-hidden="true">
            <CheckCircle size={16} weight="fill" />
            <span>访问受租户与角色权限控制</span>
          </div>
        </form>
      </div>
    </div>
  )
}
