import { useState, type FormEvent } from 'react'
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
      <form className="login-card surface-box" onSubmit={onSubmit}>
        <div className="login-card__brand">
          <BrandLogo size="large" />
          <div className="workspace-title">
            <h1>SuperAgent Console</h1>
            <p>登录以进入 agent 运行控制台</p>
          </div>
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
        </Button>
      </form>
    </div>
  )
}
