import { useEffect, useRef, useState } from 'react'
import { Client, ReconnectionTimeMode, type StompSubscription } from '@stomp/stompjs'
import { createTrackingSession, initialTrackingState, type TrackingSocketEvents } from './trackingRealtimeSession'
import { trackingAccessError, type createTrackingRequests } from './trackingRequests'

function connectToOrder(orderId: number, token: string, isCurrent: () => boolean, events: TrackingSocketEvents) {
  const topic = `/topic/orders/${orderId}/location`
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const client = new Client({
    brokerURL: `${protocol}//${window.location.host}/ws`,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5_000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: 30_000,
    connectionTimeout: 10_000,
    heartbeatIncoming: 0,
    heartbeatOutgoing: 0,
  })
  let closed = false
  let attempted = false
  let subscription: StompSubscription | undefined
  const current = () => !closed && isCurrent()

  client.beforeConnect = () => {
    if (!current()) {
      void client.deactivate({ force: true })
      return
    }
    events.connecting(attempted)
    attempted = true
  }
  client.onConnect = () => {
    if (!current() || subscription) return
    const activeSubscription = client.subscribe(topic, (message) => {
      if (!current() || subscription !== activeSubscription || message.headers.destination !== topic) return
      let payload: unknown
      try {
        payload = JSON.parse(message.body)
      } catch {
        return
      }
      events.location(payload)
    })
    subscription = activeSubscription
    events.connected()
  }
  client.onStompError = () => {
    if (current()) events.brokerError()
  }
  client.onWebSocketError = () => {
    if (current()) events.disconnected()
  }
  client.onWebSocketClose = () => {
    subscription = undefined
    if (current()) events.disconnected()
  }

  function networkOffline() {
    if (!current()) return
    events.disconnected()
    client.forceDisconnect()
  }

  window.addEventListener('offline', networkOffline)
  client.activate()
  return () => {
    closed = true
    window.removeEventListener('offline', networkOffline)
    try {
      if (client.connected) subscription?.unsubscribe()
    } finally {
      subscription = undefined
      void client.deactivate({ force: true })
    }
  }
}

export function useOrderTrackingRealtime(
  orderId: number, status: string, token: string, requests: ReturnType<typeof createTrackingRequests>,
) {
  const [state, setState] = useState(initialTrackingState)
  const sessionRef = useRef<ReturnType<typeof createTrackingSession> | null>(null)

  useEffect(() => {
    const isCurrent = () => localStorage.getItem('token') === token
    const session = createTrackingSession({
      load: (signal) => requests.snapshot(orderId, signal),
      connect: (events) => connectToOrder(orderId, token, isCurrent, events),
      accessError: trackingAccessError,
      isCurrent,
      emit: setState,
      scheduleStale: (markStale) => {
        const timer = window.setTimeout(markStale, 30_000)
        return () => window.clearTimeout(timer)
      },
    })
    sessionRef.current = session
    return () => {
      session.dispose()
      sessionRef.current = null
    }
  }, [orderId, token, requests])

  useEffect(() => {
    sessionRef.current?.updateStatus(status)
  }, [orderId, status, token, requests])

  return { state, refresh: () => sessionRef.current?.refresh() }
}
