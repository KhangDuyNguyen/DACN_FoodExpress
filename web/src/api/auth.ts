import { api } from './client'

export type Role = 'CUSTOMER' | 'MERCHANT' | 'DRIVER' | 'ADMIN'

export interface User {
  id: number
  fullName: string
  phone: string
  email: string | null
  role: Role
  status: string
  avatarUrl: string | null
  createdAt: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface LoginRequest {
  identifier: string
  password: string
}

export interface RegisterRequest {
  fullName: string
  phone: string
  email?: string
  password: string
  role: 'CUSTOMER' | 'MERCHANT' | 'DRIVER'
}

export async function login(body: LoginRequest): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/auth/login', body)
  return res.data
}

export async function register(body: RegisterRequest): Promise<AuthResponse> {
  const res = await api.post<AuthResponse>('/auth/register', body)
  return res.data
}

export async function fetchMe(): Promise<User> {
  const res = await api.get<User>('/users/me')
  return res.data
}
