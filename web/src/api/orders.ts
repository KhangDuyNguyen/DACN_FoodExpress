import { api } from './client'
import type { PageResponse } from './page'

// Khop voi package order o backend

export type OrderStatus =
  | 'PENDING_PAYMENT' | 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_PICKUP'
  | 'PICKED_UP' | 'DELIVERING' | 'DELIVERED' | 'CANCELLED' | 'REJECTED'

// 5 trang thai dung trong tuan 3 (giong OrderStatusFlow o backend)
export const ORDER_STATUS_LABEL: Partial<Record<OrderStatus, string>> = {
  PLACED: 'Chờ xác nhận',
  PREPARING: 'Đang chuẩn bị',
  DELIVERING: 'Đang giao',
  DELIVERED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
}

export function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABEL[status] ?? status
}

// Don da xong (hoan thanh hoac da huy) thi khong doi trang thai nua
export function isFinished(status: OrderStatus | undefined): boolean {
  return status === 'DELIVERED' || status === 'CANCELLED'
}

export interface OrderSummary {
  id: number
  code: string
  restaurantId: number
  restaurantName: string
  status: OrderStatus
  totalAmount: number
  itemCount: number
  receiverName: string
  deliveryAddress: string
  createdAt: string
}

export interface PriceBreakdown {
  subtotal: number
  deliveryFee: number
  discountAmount: number
  totalAmount: number
  distanceKm: number
}

export interface OrderItem {
  menuItemId: number | null
  name: string // ten mon luc dat
  unitPrice: number // gia luc dat
  quantity: number
  note: string | null
  lineTotal: number
}

export interface OrderHistory {
  status: OrderStatus
  changedAt: string
  note: string | null
}

export interface OrderDetail {
  id: number
  code: string
  status: OrderStatus
  customerName: string
  restaurant: { id: number; name: string; phone: string | null; address: string }
  receiverName: string
  receiverPhone: string
  deliveryAddress: string
  note: string | null
  paymentMethod: 'COD' | 'VNPAY' | 'MOMO'
  items: OrderItem[]
  subtotal: number
  deliveryFee: number
  discountAmount: number
  totalAmount: number
  distanceKm: number | null
  cancelReason: string | null
  createdAt: string
  acceptedAt: string | null
  deliveredAt: string | null
  cancelledAt: string | null
  history: OrderHistory[]
}

// Khong gui mon hay gia: server lay mon tu gio va tu tinh tien
export interface CreateOrderBody {
  receiverName: string
  receiverPhone: string
  deliveryAddress: string
  deliveryLat: number
  deliveryLng: number
  note?: string
  paymentMethod: 'COD'
}

export async function previewOrder(body: { deliveryLat: number; deliveryLng: number }): Promise<PriceBreakdown> {
  const res = await api.post<PriceBreakdown>('/orders/preview', body)
  return res.data
}

export async function createOrder(body: CreateOrderBody): Promise<OrderDetail> {
  const res = await api.post<OrderDetail>('/orders', body)
  return res.data
}

// ── Don cua khach (Module D) ──

export async function fetchMyOrders(params: { status?: OrderStatus; page: number; size: number }): Promise<PageResponse<OrderSummary>> {
  const res = await api.get<PageResponse<OrderSummary>>('/orders', { params })
  return res.data
}

export async function fetchMyOrder(id: number): Promise<OrderDetail> {
  const res = await api.get<OrderDetail>(`/orders/${id}`)
  return res.data
}

export async function cancelMyOrder(id: number, reason: string): Promise<OrderDetail> {
  const res = await api.post<OrderDetail>(`/orders/${id}/cancel`, { reason })
  return res.data
}
