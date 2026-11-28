import { api } from './client'

export async function fetchOrderTracking(orderId: number, signal: AbortSignal): Promise<unknown> {
  const response = await api.get<unknown>(`/orders/${orderId}/tracking`, { signal })
  return response.data
}
