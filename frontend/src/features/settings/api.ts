import axios from 'axios'
import { apiGet, http } from '../../api/http'
import type {
  AgentSettings,
  ModelSettings,
  RagSettings,
  RerankSettings,
  SecretUpdateResponse,
  ToolSettings,
  UpdateAgentSettingsRequest,
  UpdateModelSettingsRequest,
  UpdateRagSettingsRequest,
  UpdateRerankSettingsRequest,
  UpdateToolSettingsRequest,
  UpdateResponse,
  ValidationFieldError,
} from './types'

export class SettingsValidationError extends Error {
  fieldErrors: ValidationFieldError[]

  constructor(message: string, fieldErrors: ValidationFieldError[]) {
    super(message)
    this.name = 'SettingsValidationError'
    this.fieldErrors = fieldErrors
  }
}

export function getModelSettings() {
  return apiGet<ModelSettings>('/admin/settings/model')
}

export async function updateModelSettings(payload: UpdateModelSettingsRequest) {
  try {
    const response = await http.patch('/admin/settings/model', payload)
    return response.data as { success: boolean; code: string; message: string; data: SecretUpdateResponse; traceId: string }
  } catch (error) {
    throw normalizeSettingsError(error)
  }
}

export function getRagSettings() {
  return apiGet<RagSettings>('/admin/settings/rag')
}

export async function updateRagSettings(payload: UpdateRagSettingsRequest) {
  try {
    const response = await http.patch('/admin/settings/rag', payload)
    return response.data as { success: boolean; code: string; message: string; data: UpdateResponse; traceId: string }
  } catch (error) {
    throw normalizeSettingsError(error)
  }
}

export function getRerankSettings() {
  return apiGet<RerankSettings>('/admin/settings/rerank')
}

export async function updateRerankSettings(payload: UpdateRerankSettingsRequest) {
  try {
    const response = await http.patch('/admin/settings/rerank', payload)
    return response.data as { success: boolean; code: string; message: string; data: SecretUpdateResponse; traceId: string }
  } catch (error) {
    throw normalizeSettingsError(error)
  }
}

export function getAgentSettings() {
  return apiGet<AgentSettings>('/admin/settings/agent')
}

export async function updateAgentSettings(payload: UpdateAgentSettingsRequest) {
  try {
    const response = await http.patch('/admin/settings/agent', payload)
    return response.data as { success: boolean; code: string; message: string; data: UpdateResponse; traceId: string }
  } catch (error) {
    throw normalizeSettingsError(error)
  }
}

export function getToolSettings() {
  return apiGet<ToolSettings>('/admin/settings/tools')
}

export async function updateToolSettings(payload: UpdateToolSettingsRequest) {
  try {
    const response = await http.patch('/admin/settings/tools', payload)
    return response.data as { success: boolean; code: string; message: string; data: UpdateResponse; traceId: string }
  } catch (error) {
    throw normalizeSettingsError(error)
  }
}

function normalizeSettingsError(error: unknown) {
  if (axios.isAxiosError(error)) {
    const responseData = error.response?.data as
      | { message?: string; data?: { errors?: ValidationFieldError[] } }
      | undefined
    const fieldErrors = responseData?.data?.errors
    if (Array.isArray(fieldErrors) && fieldErrors.length > 0) {
      return new SettingsValidationError(responseData?.message ?? 'Request validation failed', fieldErrors)
    }
  }
  return error
}
