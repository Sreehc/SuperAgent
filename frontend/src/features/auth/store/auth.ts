import { defineStore } from 'pinia'
import { apiGet, apiPost } from '../../../api/http'
import { clearLegacyRefreshToken, readAccessToken, readCurrentTenantId, writeAccessToken, writeCurrentTenantId } from '../../../api/storage'
import type { LoginRequest, LoginResponse, LogoutResponse, MeResponse, RefreshResponse, TenantListItem, TenantRole, UserSummary } from '../types'

interface AuthState {
  accessToken: string | null
  user: UserSummary | null
  tenants: TenantListItem[]
  currentTenantId: number | null
  initialized: boolean
  loadingProfile: boolean
  pendingRedirect: string | null
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    user: null,
    tenants: [],
    currentTenantId: null,
    initialized: false,
    loadingProfile: false,
    pendingRedirect: null,
  }),
  getters: {
    hasToken: (state) => Boolean(state.accessToken),
    isAuthenticated: (state) => Boolean(state.accessToken && state.user),
    currentTenant: (state) => state.tenants.find((tenant) => tenant.id === state.currentTenantId) ?? null,
    currentRole(): TenantRole | null {
      return this.currentTenant?.role ?? null
    },
  },
  actions: {
    hydrate() {
      clearLegacyRefreshToken()
      this.accessToken = readAccessToken()
      this.currentTenantId = readCurrentTenantId()
      this.initialized = true
    },
    rememberRedirect(path: string) {
      this.pendingRedirect = path
    },
    consumeRedirect() {
      const target = this.pendingRedirect ?? '/chat'
      this.pendingRedirect = null
      return target
    },
    clearSession() {
      this.accessToken = null
      this.user = null
      this.tenants = []
      this.currentTenantId = null
      writeAccessToken(null)
      writeCurrentTenantId(null)
    },
    handleUnauthorized(path = '/login') {
      if (this.accessToken) {
        this.pendingRedirect = path
      }
      this.clearSession()
    },
    async login(payload: LoginRequest) {
      const response = await apiPost<LoginResponse, LoginRequest>('/auth/login', payload)
      this.accessToken = response.data.accessToken
      this.user = response.data.user
      this.currentTenantId = response.data.defaultTenant.id
      writeAccessToken(response.data.accessToken)
      writeCurrentTenantId(response.data.defaultTenant.id)
      await this.loadProfile()
    },
    async loadProfile() {
      this.loadingProfile = true
      try {
        const [meResponse, tenantResponse] = await Promise.all([
          apiGet<MeResponse>('/auth/me'),
          apiGet<TenantListItem[]>('/tenants'),
        ])

        this.user = {
          id: meResponse.data.id,
          username: meResponse.data.username,
          displayName: meResponse.data.displayName,
        }
        this.tenants = tenantResponse.data

        if (!this.currentTenantId && this.tenants.length > 0) {
          this.currentTenantId = this.tenants[0].id
        }

        if (this.currentTenantId) {
          writeCurrentTenantId(this.currentTenantId)
        }
      } finally {
        this.loadingProfile = false
      }
    },
    async ensureSession() {
      if (!this.accessToken) {
        const refreshed = await this.refresh()
        if (!refreshed) {
          return false
        }
      }

      if (this.user && this.tenants.length > 0) {
        return true
      }

      try {
        await this.loadProfile()
        return true
      } catch {
        this.clearSession()
        return false
      }
    },
    async switchTenant(tenantId: number) {
      await apiPost<{ tenantId: number; role: TenantRole }, null>(`/tenants/${tenantId}/switch`)
      this.currentTenantId = tenantId
      writeCurrentTenantId(tenantId)
      await this.loadProfile()
    },
    async logout() {
      try {
        await apiPost<LogoutResponse, null>('/auth/logout')
      } catch {
        // Keep logout idempotent on the client.
      }

      this.clearSession()
    },
    async refresh() {
      try {
        const response = await apiPost<RefreshResponse, null>('/auth/refresh')
        this.accessToken = response.data.accessToken
        writeAccessToken(response.data.accessToken)
        return true
      } catch {
        this.clearSession()
        return false
      }
    },
  },
})
