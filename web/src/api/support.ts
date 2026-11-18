import { api } from './client'
import type { PageResponse } from './page'

export type SupportTicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export interface SupportTicket {
  id: number
  userId?: number
  user_id?: number
  customerName?: string
  userName?: string
  customerEmail?: string
  userEmail?: string
  orderId?: number | null
  order_id?: number | null
  subject: string
  description?: string | null
  status: SupportTicketStatus
  createdAt: string
  created_at?: string
  resolvedAt?: string | null
  resolved_at?: string | null
}

export interface CreateSupportTicketRequest {
  subject: string
  description?: string
  orderId?: number | null
}

export interface CustomerTicketsQuery {
  status?: SupportTicketStatus
  page?: number
  size?: number
}

export interface AdminTicketsQuery {
  status?: SupportTicketStatus
  keyword?: string
  page?: number
  size?: number
}

export interface UpdateTicketStatusRequest {
  status: SupportTicketStatus
}

/**
 * Customer: Tạo ticket hỗ trợ mới
 * POST /api/support/tickets
 */
export async function createSupportTicket(data: CreateSupportTicketRequest): Promise<SupportTicket> {
  const res = await api.post<SupportTicket>('/support/tickets', data)
  return res.data
}

/**
 * Customer: Lấy danh sách ticket của chính mình
 * GET /api/support/tickets
 */
export async function fetchMySupportTickets(params?: CustomerTicketsQuery): Promise<PageResponse<SupportTicket>> {
  const res = await api.get<PageResponse<SupportTicket>>('/support/tickets', { params })
  return res.data
}

/**
 * Admin: Lấy danh sách tất cả các ticket hỗ trợ
 * GET /api/admin/tickets
 */
export async function fetchAdminTickets(params?: AdminTicketsQuery): Promise<PageResponse<SupportTicket>> {
  const res = await api.get<PageResponse<SupportTicket>>('/admin/tickets', { params })
  return res.data
}

/**
 * Admin: Cập nhật trạng thái ticket
 * PATCH /api/admin/tickets/{id}
 */
export async function updateAdminTicketStatus(id: number, status: SupportTicketStatus): Promise<SupportTicket> {
  const res = await api.patch<SupportTicket>(`/admin/tickets/${id}`, { status })
  return res.data
}
