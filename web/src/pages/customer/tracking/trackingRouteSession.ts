import { readRouteFailure } from './orderTrackingRoute.ts'
import {
  createTrackingRoutePlanner, type RouteTicket, type TrackingRouteInput, type TrackingRouteState,
} from './trackingRoutePlanner.ts'

export interface TrackingRouteView {
  state: TrackingRouteState
  hadRoute: boolean
}

export const inactiveRouteView: TrackingRouteView = { state: { status: 'inactive' }, hadRoute: false }

interface Dependencies {
  nowMs: () => number
  schedule: (run: () => void, delayMs: number) => () => void
  load: (orderId: number, signal: AbortSignal) => Promise<unknown>
  isCurrent: () => boolean
  emit: (view: TrackingRouteView) => void
}

export function createTrackingRouteSession({ nowMs, schedule, load, isCurrent, emit }: Dependencies) {
  const planner = createTrackingRoutePlanner(nowMs)
  let active = true
  let hadRoute = false
  let view = inactiveRouteView
  let timer: { ticket: RouteTicket; cancel: () => void } | null = null

  const current = () => active && isCurrent()

  function cancelTimer() {
    timer?.cancel()
    timer = null
  }

  function publish() {
    if (!current()) return
    const state = planner.getState()
    if (state.status === 'ready' || state.status === 'arrived') hadRoute = true
    if (state.status === 'inactive') hadRoute = false
    if (view.state !== state || view.hadRoute !== hadRoute) {
      view = { state, hadRoute }
      emit(view)
    }

    const ticket = state.status === 'updating' ? state.ticket : null
    if (timer?.ticket === ticket) return
    cancelTimer()
    if (!ticket) return
    const entry = {
      ticket,
      cancel: schedule(() => {
        if (timer !== entry || !current()) return
        timer = null
        const request = planner.begin(ticket)
        if (request) void run(request)
        publish()
      }, Math.max(0, Math.ceil(ticket.startAtMs - nowMs()))),
    }
    timer = entry
  }

  async function run(request: NonNullable<ReturnType<typeof planner.begin>>) {
    try {
      const response = await load(request.orderId, request.signal)
      if (current() && !request.signal.aborted) planner.complete(request, response)
    } catch (error) {
      if (current() && !request.signal.aborted) planner.fail(request, readRouteFailure(error))
    }
    publish()
  }

  return {
    update(input: TrackingRouteInput) {
      if (!current()) return
      planner.update(input)
      publish()
    },
    dispose() {
      active = false
      cancelTimer()
      planner.clear()
    },
  }
}
