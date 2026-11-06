import { api } from './client'
import type { OrderDetail, OrderStatus, OrderSummary } from './orders'
import type { PageResponse } from './page'

// API cho chu quan (backend: /api/merchant/restaurants, /api/merchant/orders)

export interface MerchantRestaurant {
  id: number
  name: string
  address: string
  openStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED'
  status: 'PENDING' | 'ACTIVE' | 'HIDDEN' | 'SUSPENDED'
}

export interface MerchantOrderParams {
  restaurantId?: number // bo trong = tat ca chi nhanh cua minh
  status?: OrderStatus
  page: number
  size: number
}

export async function fetchMyRestaurants(): Promise<MerchantRestaurant[]> {
  const res = await api.get<MerchantRestaurant[]>('/merchant/restaurants')
  return res.data
}

export async function fetchMerchantOrders(params: MerchantOrderParams): Promise<PageResponse<OrderSummary>> {
  const res = await api.get<PageResponse<OrderSummary>>('/merchant/orders', { params })
  return res.data
}

export async function fetchMerchantOrder(id: number): Promise<OrderDetail> {
  const res = await api.get<OrderDetail>(`/merchant/orders/${id}`)
  return res.data
}

// reason bat buoc khi status = CANCELLED
export async function changeOrderStatus(id: number, body: { status: OrderStatus; reason?: string }): Promise<OrderDetail> {
  const res = await api.patch<OrderDetail>(`/merchant/orders/${id}/status`, body)
  return res.data
}
