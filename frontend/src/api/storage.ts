const ACCESS_TOKEN_KEY = 'superagent.accessToken'
const REFRESH_TOKEN_KEY = 'superagent.refreshToken'
const CURRENT_TENANT_ID_KEY = 'superagent.currentTenantId'

export function readAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY)
}

export function writeAccessToken(value: string | null) {
  syncItem(ACCESS_TOKEN_KEY, value)
}

export function readRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function writeRefreshToken(value: string | null) {
  syncItem(REFRESH_TOKEN_KEY, value)
}

export function readCurrentTenantId() {
  const raw = localStorage.getItem(CURRENT_TENANT_ID_KEY)
  return raw ? Number(raw) : null
}

export function writeCurrentTenantId(value: number | null) {
  syncItem(CURRENT_TENANT_ID_KEY, value == null ? null : String(value))
}

function syncItem(key: string, value: string | null) {
  if (value == null || value === '') {
    localStorage.removeItem(key)
    return
  }

  localStorage.setItem(key, value)
}
