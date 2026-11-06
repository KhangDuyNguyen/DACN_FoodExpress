import { api } from './client'

// Khop voi package order o backend

export type OrderStatus =
  | 'PENDING_PAYMENT' | 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_PICKUP'
  | 'PICKED_UP' | 'DELIVERING' | 'DELIVERED' | 'CANCELLED' | 'REJECTED'

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
