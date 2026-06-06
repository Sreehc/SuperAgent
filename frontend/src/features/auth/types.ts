export type TenantRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export interface LoginRequest {
  username: string
  password: string
}

export interface UserSummary {
  id: number
  username: string
  displayName: string
}

export interface LoginTenantSummary {
  id: number
  name: string
  role: TenantRole
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: UserSummary
  defaultTenant: LoginTenantSummary
}

export interface MeTenantSummary {
  id: number
  name: string
  role: TenantRole
}

export interface MeResponse {
  id: number
  username: string
  displayName: string
  tenants: MeTenantSummary[]
}

export interface TenantListItem {
  id: number
  name: string
  code: string
  role: TenantRole
  status: string
}

export interface LogoutResponse {
  revoked: boolean
}

export interface RefreshResponse {
  accessToken: string
  expiresIn: number
}
