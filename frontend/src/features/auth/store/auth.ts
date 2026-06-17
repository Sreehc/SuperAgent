import { create } from 'zustand'
import { apiGet, apiPost } from '@/api/http'
import {
  clearLegacyRefreshToken,
  readAccessToken,
  readCurrentTenantId,
  writeAccessToken,
  writeCurrentTenantId,
} from '@/api/storage'
import type {
  LoginRequest,
  LoginResponse,
  LogoutResponse,
  MeResponse,
  RefreshResponse,
  TenantListItem,
  TenantRole,
  UserSummary,
} from '../types'

interface AuthState {
  accessToken: string | null
  user: UserSummary | null
  tenants: TenantListItem[]
  currentTenantId: number | null
  initialized: boolean
  loadingProfile: boolean
  pendingRedirect: string | null
}

interface AuthActions {
  hydrate: () => void
  rememberRedirect: (path: string) => void
  consumeRedirect: () => string
  clearSession: () => void
  handleUnauthorized: (path?: string) => void
  login: (payload: LoginRequest) => Promise<void>
  loadProfile: () => Promise<void>
  ensureSession: () => Promise<boolean>
  switchTenant: (tenantId: number) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<boolean>
}

export type AuthStore = AuthState & AuthActions

const initialState: AuthState = {
  accessToken: null,
  user: null,
  tenants: [],
  currentTenantId: null,
  initialized: false,
  loadingProfile: false,
  pendingRedirect: null,
}

export const useAuthStore = create<AuthStore>((set, get) => ({
  ...initialState,

  hydrate() {
    clearLegacyRefreshToken()
    set({ accessToken: readAccessToken(), currentTenantId: readCurrentTenantId(), initialized: true })
  },

  rememberRedirect(path) {
    set({ pendingRedirect: path })
  },

  consumeRedirect() {
    const target = get().pendingRedirect ?? '/chat'
    set({ pendingRedirect: null })
    return target
  },

  clearSession() {
    writeAccessToken(null)
    writeCurrentTenantId(null)
    set({ accessToken: null, user: null, tenants: [], currentTenantId: null })
  },

  handleUnauthorized(path = '/login') {
    if (get().accessToken) {
      set({ pendingRedirect: path })
    }
    get().clearSession()
  },

  async login(payload) {
    const response = await apiPost<LoginResponse, LoginRequest>('/auth/login', payload)
    writeAccessToken(response.data.accessToken)
    writeCurrentTenantId(response.data.defaultTenant.id)
    set({
      accessToken: response.data.accessToken,
      user: response.data.user,
      currentTenantId: response.data.defaultTenant.id,
    })
    await get().loadProfile()
  },

  async loadProfile() {
    set({ loadingProfile: true })
    try {
      const [meResponse, tenantResponse] = await Promise.all([
        apiGet<MeResponse>('/auth/me'),
        apiGet<TenantListItem[]>('/tenants'),
      ])

      const tenants = tenantResponse.data
      let currentTenantId = get().currentTenantId
      if (!currentTenantId && tenants.length > 0) {
        currentTenantId = tenants[0].id
      }
      if (currentTenantId) {
        writeCurrentTenantId(currentTenantId)
      }

      set({
        user: {
          id: meResponse.data.id,
          username: meResponse.data.username,
          displayName: meResponse.data.displayName,
        },
        tenants,
        currentTenantId,
      })
    } finally {
      set({ loadingProfile: false })
    }
  },

  async ensureSession() {
    if (!get().accessToken) {
      const refreshed = await get().refresh()
      if (!refreshed) {
        return false
      }
    }

    if (get().user && get().tenants.length > 0) {
      return true
    }

    try {
      await get().loadProfile()
      return true
    } catch {
      get().clearSession()
      return false
    }
  },

  async switchTenant(tenantId) {
    await apiPost<{ tenantId: number; role: TenantRole }, null>(`/tenants/${tenantId}/switch`)
    writeCurrentTenantId(tenantId)
    set({ currentTenantId: tenantId })
    await get().loadProfile()
  },

  async logout() {
    try {
      await apiPost<LogoutResponse, null>('/auth/logout')
    } catch {
      // Keep logout idempotent on the client.
    }
    get().clearSession()
  },

  async refresh() {
    try {
      const response = await apiPost<RefreshResponse, null>('/auth/refresh')
      writeAccessToken(response.data.accessToken)
      set({ accessToken: response.data.accessToken })
      return true
    } catch {
      get().clearSession()
      return false
    }
  },
}))

// Derived selectors (pure functions over state)
export const selectIsAuthenticated = (s: AuthStore) => Boolean(s.accessToken && s.user)
export const selectCurrentTenant = (s: AuthStore) => s.tenants.find((t) => t.id === s.currentTenantId) ?? null
export const selectCurrentRole = (s: AuthStore): TenantRole | null => selectCurrentTenant(s)?.role ?? null
