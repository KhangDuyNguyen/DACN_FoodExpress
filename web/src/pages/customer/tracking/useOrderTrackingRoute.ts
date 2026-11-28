import { useEffect, useRef, useState } from 'react'
import type { TrackingState } from './trackingRealtimeSession'
import type { createTrackingRequests } from './trackingRequests'
import { createTrackingRouteSession, inactiveRouteView } from './trackingRouteSession'

export function useOrderTrackingRoute(
  enabled: boolean, orderId: number, status: string, token: string,
  tracking: TrackingState, requests: ReturnType<typeof createTrackingRequests>,
) {
  // OrderTrackingDetails is keyed by order ID and login revision.
  const [view, setView] = useState(inactiveRouteView)
  const session = useRef<ReturnType<typeof createTrackingRouteSession> | null>(null)
  const { connection, denied, deliveryPoint, finished, location, stale } = tracking
  const canTrack = enabled && !denied && !finished

  useEffect(() => {
    if (!enabled) return
    const scope = createTrackingRouteSession({
      nowMs: () => performance.now(),
      schedule(run, delayMs) {
        const timer = window.setTimeout(run, delayMs)
        return () => window.clearTimeout(timer)
      },
      load: requests.route,
      isCurrent: () => localStorage.getItem('token') === token,
      emit: setView,
    })
    session.current = scope
    return () => {
      scope.dispose()
      session.current = null
    }
  }, [enabled, orderId, requests, token])

  useEffect(() => {
    session.current?.update({
      orderId, sessionId: 'current', status, connection: canTrack ? connection : 'idle',
      deliveryPoint, location, stale,
    })
  }, [canTrack, connection, deliveryPoint, location, orderId, requests, stale, status, token])

  return canTrack && status === 'DELIVERING' && connection === 'live' && !stale && location && deliveryPoint
    ? view : inactiveRouteView
}
