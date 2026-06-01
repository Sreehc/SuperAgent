export interface ApiEnvelope<T> {
  success: boolean
  code: string
  message: string
  data: T
  traceId: string
}
