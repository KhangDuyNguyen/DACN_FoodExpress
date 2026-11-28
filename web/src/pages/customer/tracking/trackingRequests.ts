import axios, { isAxiosError } from 'axios'
import type { OrderDetail } from '../../../api/orders'
import { requestOrderTrackingRoute } from './orderTrackingRoute.ts'
import { readTrackingSnapshot } from './trackingSnapshot.ts'

export function trackingAccessError(error: unknown) {
  const status = isAxiosError(error) ? error.response?.status : undefined
  return status === 401 || status === 403 || status === 404 ? status : null
}

export function createTrackingRequests(baseURL: string, token: string) {
  // A request belongs to the session that dispatched it. Its 401 must never change another session.
  const client = axios.create({ baseURL, headers: { Authorization: `Bearer ${token}` } })

  return {
    async order(orderId: number, signal: AbortSignal) {
      const { data } = await client.get<OrderDetail>(`/orders/${orderId}`, { signal })
      return data
    },
    async snapshot(orderId: number, signal: AbortSignal) {
      const { data } = await client.get<unknown>(`/orders/${orderId}/tracking`, { signal })
      return readTrackingSnapshot(data, orderId)
    },
    route(orderId: number, signal: AbortSignal) {
      return requestOrderTrackingRoute(client, orderId, signal)
    },
    async cancel(orderId: number, reason: string) {
      const { data } = await client.post<OrderDetail>(`/orders/${orderId}/cancel`, { reason })
      return data
    },
  }
}
