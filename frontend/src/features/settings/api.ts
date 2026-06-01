import { apiGet, http } from '../../api/http'
import type {
  ModelSettings,
  RagSettings,
  RerankSettings,
  SecretUpdateResponse,
  UpdateModelSettingsRequest,
  UpdateRagSettingsRequest,
  UpdateRerankSettingsRequest,
  UpdateResponse,
} from './types'

export function getModelSettings() {
  return apiGet<ModelSettings>('/admin/settings/model')
}

export async function updateModelSettings(payload: UpdateModelSettingsRequest) {
  const response = await http.patch('/admin/settings/model', payload)
  return response.data as { success: boolean; code: string; message: string; data: SecretUpdateResponse; traceId: string }
}

export function getRagSettings() {
  return apiGet<RagSettings>('/admin/settings/rag')
}

export async function updateRagSettings(payload: UpdateRagSettingsRequest) {
  const response = await http.patch('/admin/settings/rag', payload)
  return response.data as { success: boolean; code: string; message: string; data: UpdateResponse; traceId: string }
}

export function getRerankSettings() {
  return apiGet<RerankSettings>('/admin/settings/rerank')
}

export async function updateRerankSettings(payload: UpdateRerankSettingsRequest) {
  const response = await http.patch('/admin/settings/rerank', payload)
  return response.data as { success: boolean; code: string; message: string; data: SecretUpdateResponse; traceId: string }
}
