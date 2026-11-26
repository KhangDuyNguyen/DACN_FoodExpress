import { api } from './client'
import type { Role } from './auth'
import type { PageResponse } from './page'

// Khop voi AdminUserController o backend (package user, tuan 6)

export type UserStatus = 'ACTIVE' | 'LOCKED' | 'PENDING' | 'DELETED'

export interface AdminUser {
  id: number
  fullName: string
  phone: string
  email: string | null
  role: Role
  status: UserStatus
  createdAt: string
  lastLoginAt: string | null
}

export interface AdminUserQuery {
  role?: Role
  status?: UserStatus
  q?: string // tim trong ho ten, SDT, email
  page: number
  size: number
}

export async function fetchAdminUsers(params: AdminUserQuery): Promise<PageResponse<AdminUser>> {
  const res = await api.get<PageResponse<AdminUser>>('/admin/users', { params })
  return res.data
}

// Khoa (LOCKED) hoac mo khoa (ACTIVE). Backend khong cho khoa tai khoan ADMIN (409).
export async function changeUserStatus(id: number, status: 'ACTIVE' | 'LOCKED'): Promise<AdminUser> {
  const res = await api.patch<AdminUser>(`/admin/users/${id}/status`, { status })
  return res.data
}
