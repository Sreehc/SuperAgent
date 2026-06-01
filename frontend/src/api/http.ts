import axios from 'axios'
import type { ApiEnvelope } from './types'
import { readAccessToken, readCurrentTenantId } from './storage'

let unauthorizedHandler: (() => void) | null = null
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'
const timeoutSeconds = Number(import.meta.env.VITE_SSE_TIMEOUT_SECONDS || 120)

export const http = axios.create({
  baseURL: apiBaseUrl,
  timeout: timeoutSeconds * 1000,
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
  (error) => {
    if (error.response?.status === 401) {
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)

export function setUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

export async function apiGet<T>(url: string) {
  const response = await http.get<ApiEnvelope<T>>(url)
  return response.data
}

export async function apiPost<T, B = unknown>(url: string, body?: B) {
  const response = await http.post<ApiEnvelope<T>>(url, body)
  return response.data
}
