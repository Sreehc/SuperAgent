import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiEnvelope } from './types'
import { readAccessToken, readCurrentTenantId } from './storage'

type RetriableRequestConfig = AxiosRequestConfig & { __superagentRetried?: boolean }

let unauthorizedHandler: (() => void) | null = null
let refreshHandler: (() => Promise<boolean>) | null = null
let refreshPromise: Promise<boolean> | null = null
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const timeoutSeconds = Number(import.meta.env.VITE_SSE_TIMEOUT_SECONDS || 120)

export const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: timeoutSeconds * 1000,
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  const accessToken = readAccessToken()
  const tenantId = readCurrentTenantId()

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  if (tenantId) {
    config.headers['X-Tenant-Id'] = tenantId.toString()
  }

  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as RetriableRequestConfig | undefined
    if (error.response?.status !== 401 || !config || config.__superagentRetried) {
      return Promise.reject(error)
    }

    const url = String(config.url || '')
    if (url.includes('/auth/login') || url.includes('/auth/refresh')) {
      unauthorizedHandler?.()
      return Promise.reject(error)
    }

    if (!refreshHandler) {
      unauthorizedHandler?.()
      return Promise.reject(error)
    }

    config.__superagentRetried = true
    refreshPromise ??= refreshHandler().finally(() => {
      refreshPromise = null
    })

    const refreshed = await refreshPromise
    if (!refreshed) {
      unauthorizedHandler?.()
      return Promise.reject(error)
    }

    return http(config)
  },
)

export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

export function setRefreshHandler(handler: () => Promise<boolean>) {
  refreshHandler = handler
}

export async function apiGet<T>(url: string) {
  const response = await http.get<ApiEnvelope<T>>(url)
  return response.data
}

export async function apiPost<T, B = unknown>(url: string, body?: B) {
  const response = await http.post<ApiEnvelope<T>>(url, body)
  return response.data
}
