import { isAxiosError, type AxiosInstance } from 'axios'
import type { TrackingRouteFailure } from './trackingRoutePlanner.ts'

export async function requestOrderTrackingRoute(client: AxiosInstance, id: number, signal: AbortSignal): Promise<unknown> {
  const { data } = await client.get<unknown>(`/orders/${id}/tracking/route`, { signal, timeout: 8_000 })
  return data
}

export function readRouteFailure(error: unknown): TrackingRouteFailure {
  if (!isAxiosError<unknown>(error)) return {}
  const response = error.response
  if (!response) return error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT' ? { status: 504 } : {}
  const data = response.data
  const code = typeof data === 'object' && data !== null && 'code' in data && typeof data.code === 'string'
    ? data.code : undefined
  return { status: response.status, code }
}
