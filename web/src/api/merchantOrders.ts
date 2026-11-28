import axios from 'axios'
import { api } from './client'
import type { MerchantOrderSummary, OrderDetail, OrderStatus } from './orders'
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

export type MerchantRequestSession = { token: string; current: () => boolean }

// Follow the existing Notification client's token snapshot pattern for scoped Merchant requests.
const scopedApi = axios.create({ baseURL: api.defaults.baseURL })

async function checkedRequest<T>(request: () => Promise<{ data: T }>, session?: MerchantRequestSession, signal?: AbortSignal): Promise<T> {
  const check = () => {
    if (signal?.aborted || (session && !session.current())) throw new DOMException('Session changed', 'AbortError')
  }
  check()
  try {
    const response = await request()
    check()
    return response.data
  } catch (error) {
    check()
    throw error
  }
}

function requestOptions(signal?: AbortSignal, session?: MerchantRequestSession) {
  return { signal, ...(session ? { headers: { Authorization: `Bearer ${session.token}` } } : {}) }
}

export function fetchMyRestaurants(signal?: AbortSignal, session?: MerchantRequestSession): Promise<MerchantRestaurant[]> {
  return checkedRequest(() => (session ? scopedApi : api).get<MerchantRestaurant[]>('/merchant/restaurants', requestOptions(signal, session)), session, signal)
}

export function fetchMerchantOrders(params: MerchantOrderParams, signal?: AbortSignal, session?: MerchantRequestSession): Promise<PageResponse<MerchantOrderSummary>> {
  return checkedRequest(() => (session ? scopedApi : api).get<PageResponse<MerchantOrderSummary>>('/merchant/orders', { ...requestOptions(signal, session), params }), session, signal)
}

export function fetchMerchantOrder(id: number, signal?: AbortSignal, session?: MerchantRequestSession): Promise<OrderDetail> {
  return checkedRequest(() => (session ? scopedApi : api).get<OrderDetail>(`/merchant/orders/${id}`, requestOptions(signal, session)), session, signal)
}

export function changeOrderStatus(id: number, body: { status: OrderStatus; reason?: string }, session?: MerchantRequestSession): Promise<OrderDetail> {
  return checkedRequest(() => (session ? scopedApi : api).patch<OrderDetail>(`/merchant/orders/${id}/status`, body, requestOptions(undefined, session)), session)
}
