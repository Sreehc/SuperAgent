import { apiGet, http } from '../../api/http'

export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER'

export interface MemberItem {
  userId: number
  username: string
  displayName: string
  role: MemberRole
  status: string
  joinedAt: string
}

export interface InvitationItem {
  id: number
  email: string
  role: MemberRole
  status: 'pending' | 'accepted' | 'expired' | 'revoked'
  invitedBy: number
  expiresAt: string
  acceptedAt: string | null
  createdAt: string
}

export interface InvitationCreatedResponse {
  id: number
  tenantId: number
  email: string
  role: MemberRole
  status: string
  expiresAt: string
  invitedBy: number
  token: string
}

export interface MemberCreatePayload {
  username: string
  displayName?: string
  email?: string
  password: string
  role: MemberRole
}

export interface MemberCreateResponse {
  userId: number
  username: string
  displayName: string
  email: string | null
  role: MemberRole
  status: string
}

export function listMembers(tenantId: number) {
  return apiGet<MemberItem[]>(`/tenants/${tenantId}/members`)
}

export function createMember(tenantId: number, payload: MemberCreatePayload) {
  return http.post<{ data: MemberCreateResponse }>(`/tenants/${tenantId}/members`, payload).then((r) => r.data.data)
}

export function listInvitations(tenantId: number, params?: { status?: string }) {
  const query = params?.status ? `?status=${encodeURIComponent(params.status)}` : ''
  return apiGet<InvitationItem[]>(`/tenants/${tenantId}/members/invitations${query}`)
}

export function createInvitation(tenantId: number, payload: { email: string; role: MemberRole }) {
  return http.post<{ data: InvitationCreatedResponse }>(`/tenants/${tenantId}/members/invitations`, payload).then((r) => r.data.data)
}

export function revokeInvitation(tenantId: number, invitationId: number) {
  return http.delete<{ data: { revoked: boolean } }>(`/tenants/${tenantId}/members/invitations/${invitationId}`).then((r) => r.data.data)
}

export function updateMember(
  tenantId: number,
  userId: number,
  payload: { role?: MemberRole; status?: 'active' | 'suspended' },
) {
  return http
    .patch<{ data: { userId: number; role: string; status: string } }>(`/tenants/${tenantId}/members/${userId}`, payload)
    .then((r) => r.data.data)
}

export function removeMember(tenantId: number, userId: number) {
  return http.delete<{ data: { removed: boolean } }>(`/tenants/${tenantId}/members/${userId}`).then((r) => r.data.data)
}

export function resetMemberPassword(tenantId: number, userId: number, payload: { password: string }) {
  return http
    .post<{ data: { userId: number; reset: boolean } }>(`/tenants/${tenantId}/members/${userId}/password`, payload)
    .then((r) => r.data.data)
}
