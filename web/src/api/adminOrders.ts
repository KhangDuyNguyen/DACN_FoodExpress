import { api } from './client'
import type { OrderDetail, OrderStatus } from './orders'
import type { PageResponse } from './page'

// Khop voi AdminOrderController o backend (package order, tuan 6)

export interface AdminOrderSummary {
  id: number
  code: string
  restaurantId: number
  restaurantName: string
  customerName: string
  customerPhone: string
  status: OrderStatus
  paymentMethod: 'COD' | 'VNPAY' | 'MOMO'
  totalAmount: number
  driverName: string | null
  createdAt: string
}

export interface AdminOrderQuery {
  status?: OrderStatus
  paymentMethod?: 'COD' | 'VNPAY' | 'MOMO'
  q?: string // dau ma don hoac dau SDT khach
  from?: string // yyyy-MM-dd, tinh ca ngay nay
  to?: string // yyyy-MM-dd, tinh ca ngay nay
  page: number
  size: number
}

export async function fetchAdminOrders(params: AdminOrderQuery): Promise<PageResponse<AdminOrderSummary>> {
  const res = await api.get<PageResponse<AdminOrderSummary>>('/admin/orders', { params })
  return res.data
}

export async function fetchAdminOrder(id: number): Promise<OrderDetail> {
  const res = await api.get<OrderDetail>(`/admin/orders/${id}`)
  return res.data
}
