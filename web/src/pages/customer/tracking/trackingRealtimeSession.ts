import { readTrackingLocation, type TrackingLocation, type TrackingPoint, type TrackingSnapshot } from './trackingSnapshot.ts'

type Connection = 'idle' | 'connecting' | 'live' | 'reconnecting' | 'offline'
type AccessError = 401 | 403 | 404

export interface TrackingState {
  connection: Connection
  deliveryPoint: TrackingPoint | null
  location: TrackingLocation | null
  hasSnapshot: boolean
  loading: boolean
  error: unknown
  denied: AccessError | null
  finished: boolean
  stale: boolean
}

export const initialTrackingState: TrackingState = {
  connection: 'idle', deliveryPoint: null, location: null, hasSnapshot: false,
  loading: false, error: null, denied: null, finished: false, stale: false,
}

interface Dependencies {
  load: (signal: AbortSignal) => Promise<TrackingSnapshot>
  connect: (events: TrackingSocketEvents) => () => void
  accessError: (error: unknown) => AccessError | null
  isCurrent: () => boolean
  emit: (state: TrackingState) => void
  scheduleStale: (markStale: () => void) => () => void
}

export interface TrackingSocketEvents {
  connecting: (reconnecting: boolean) => void
  connected: () => void
  disconnected: () => void
  brokerError: () => void
  location: (payload: unknown) => void
}

export function isTrackingFinished(status: string) {
  return status === 'DELIVERED' || status === 'CANCELLED' || status === 'REJECTED'
}

export function createTrackingSession({ load, connect, accessError, isCurrent, emit, scheduleStale }: Dependencies) {
  let state = initialTrackingState
  let active = true
  let started = false
  let request: AbortController | undefined
  let closeSocket: (() => void) | undefined
  let socketGeneration = 0
  let stopStale: (() => void) | undefined
  let frameRevision = 0

  function current() {
    return active && isCurrent()
  }

  function publish(update: Partial<TrackingState>) {
    if (!current()) return
    state = { ...state, ...update }
    emit(state)
  }

  function abortRequest() {
    request?.abort()
    request = undefined
  }

  function stopSocket() {
    socketGeneration += 1
    closeSocket?.()
    closeSocket = undefined
  }

  function stopStaleTimer() {
    stopStale?.()
    stopStale = undefined
  }

  function finish() {
    stopSocket()
    stopStaleTimer()
    abortRequest()
    publish({ finished: true, connection: 'idle', location: null, stale: false, loading: false })
  }

  function merge(location: TrackingLocation | null, revisionAtRequest?: number) {
    if (!location || state.finished || state.denied) return
    const previous = state.location
    if (previous && location.recordedAt < previous.recordedAt) return

    if (revisionAtRequest === undefined) frameRevision += 1
    if (previous && location.recordedAt === previous.recordedAt) {
      if (revisionAtRequest !== undefined && revisionAtRequest !== frameRevision) return
      if (previous.lat === location.lat && previous.lng === location.lng) return
    }

    stopStaleTimer()
    publish({ location, stale: false })
    stopStale = scheduleStale(() => {
      if (state.location === location) publish({ stale: true })
    })
  }

  async function refresh(allowFinished = false) {
    if (!current() || state.denied || (state.finished && !allowFinished)) return
    abortRequest()
    const controller = new AbortController()
    request = controller
    const revisionAtRequest = frameRevision
    publish({ loading: true, error: null })

    try {
      const snapshot = await load(controller.signal)
      if (!current() || request !== controller) return
      request = undefined
      if (isTrackingFinished(snapshot.status)) finish()
      publish({ hasSnapshot: true, deliveryPoint: snapshot.deliveryPoint, loading: false })
      merge(snapshot.latestLocation, revisionAtRequest)
    } catch (error) {
      if (!current() || request !== controller) return
      request = undefined
      const denied = accessError(error)
      if (denied) {
        stopSocket()
        stopStaleTimer()
        publish({ ...initialTrackingState, denied, error })
      } else {
        publish({ error, loading: false })
      }
    }
  }

  function updateStatus(status: string) {
    if (!current() || state.denied) return
    const firstStatus = !started
    started = true
    if (isTrackingFinished(status) && !state.finished) {
      finish()
      if (!state.hasSnapshot) void refresh(true)
      return
    }
    if (state.finished) return
    if (firstStatus) void refresh()

    if (status !== 'DELIVERING') {
      stopSocket()
      publish({ connection: 'idle' })
      return
    }
    if (closeSocket) return

    const generation = ++socketGeneration
    const sameSocket = () => current() && generation === socketGeneration
    publish({ connection: 'connecting' })
    closeSocket = connect({
      connecting(reconnecting) {
        if (sameSocket()) publish({ connection: reconnecting ? 'reconnecting' : 'connecting' })
      },
      connected() {
        if (!sameSocket()) return
        publish({ connection: 'live' })
        // Close the gap between the initial snapshot and subscription, including on reconnect.
        void refresh()
      },
      disconnected() {
        if (sameSocket()) publish({ connection: 'offline' })
      },
      brokerError() {
        if (!sameSocket()) return
        publish({ connection: 'offline' })
        void refresh()
      },
      location(payload) {
        if (sameSocket() && state.connection === 'live') merge(readTrackingLocation(payload))
      },
    })
  }

  return {
    updateStatus,
    refresh: () => refresh(),
    dispose() {
      active = false
      abortRequest()
      stopSocket()
      stopStaleTimer()
    },
  }
}
