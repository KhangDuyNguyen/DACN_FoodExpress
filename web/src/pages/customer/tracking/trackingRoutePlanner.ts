import type { TrackingState } from './trackingRealtimeSession.ts'
import { readTrackingLocation, type TrackingLocation, type TrackingPoint } from './trackingSnapshot.ts'

const TRIM_METERS = 30
const OFF_ROUTE_METERS = 45
const OFF_ROUTE_FIXES = 3
const DISTINCT_FIX_METERS = 3
const MATCH_TOLERANCE_METERS = 8
const MIN_LOOK_AHEAD_METERS = 60
const MAX_LOOK_AHEAD_METERS = 250
const MAX_SPARSE_LOOK_AHEAD_METERS = 1000
const ARRIVAL_METERS = 15
const ENDPOINT_METERS = 150
const COOLDOWN_MS = 15_000
const REQUEST_BUDGET = 5
const MAX_WAITING_FIXES = 32
const METERS_PER_DEGREE = 111_195
const ROUNDING_METERS = 0.000001

export interface TrackingRouteInput {
  orderId: number | null
  /** Opaque identity for the current login, supplied by the caller. */
  sessionId: string | null
  status: string
  connection: TrackingState['connection']
  stale: boolean
  location: TrackingLocation | null
  deliveryPoint: TrackingPoint | null
}

export interface RouteTicket {
  readonly startAtMs: number
}

export interface RouteRequest {
  readonly orderId: number
  readonly signal: AbortSignal
}

export interface TrackingRouteFailure {
  status?: number
  code?: string
}

const backendReasons = [
  'ROUTE_FORBIDDEN', 'ROUTE_ORDER_NOT_FOUND', 'ROUTE_ORDER_NOT_READY', 'ROUTE_GPS_UNAVAILABLE',
  'ROUTE_DESTINATION_INVALID', 'ROUTE_CONTEXT_CHANGED', 'ROUTE_PROFILE_UNSUPPORTED', 'ROUTE_NOT_FOUND',
  'ROUTE_RATE_LIMITED', 'ROUTE_UNAVAILABLE', 'ROUTE_UPSTREAM_ERROR', 'ROUTE_UPSTREAM_TIMEOUT',
] as const
type RouteReason = typeof backendReasons[number]
  | 'ROUTE_TRANSPORT_ERROR' | 'ROUTE_RESPONSE_INVALID' | 'ROUTE_CLIENT_BUDGET_EXHAUSTED'

type Coordinate = readonly [lng: number, lat: number]
type RouteMetrics = Readonly<{ remainingDistanceMeters: number; estimatedDurationMs: number }>

export type TrackingRouteState =
  | Readonly<{ status: 'inactive' | 'awaiting-fix' }>
  | Readonly<{ status: 'updating'; ticket: RouteTicket | null }>
  | Readonly<{ status: 'unavailable'; reason: RouteReason }>
  | Readonly<{ status: 'arrived' } & RouteMetrics>
  | Readonly<{
    status: 'ready'
    geometry: Readonly<{ type: 'LineString'; coordinates: readonly Coordinate[] }>
    originalGeometryDistanceMeters: number
    providerDistanceMeters: number
  } & RouteMetrics>

interface Segment {
  from: TrackingPoint
  to: TrackingPoint
  start: number
  length: number
}
interface Match {
  index: number
  point: TrackingPoint
  progress: number
  distance: number
}
interface Route {
  segments: Segment[]
  length: number
  providerDistanceMeters: number
  durationMs: number
  lastMatchedFix: TrackingPoint
  match: Match
  offRoute: TrackingPoint[]
  confirmed: boolean
}

function isPoint(point: TrackingPoint | null): point is TrackingPoint {
  return point != null && Number.isFinite(point.lat) && Math.abs(point.lat) <= 90
    && Number.isFinite(point.lng) && Math.abs(point.lng) <= 180
}

function samePoint(a: TrackingPoint | null, b: TrackingPoint | null) {
  return a === b || (a !== null && b !== null && a.lat === b.lat && a.lng === b.lng)
}

function isNewFix(previous: TrackingLocation | null, fix: TrackingLocation) {
  // Compare only validated timestamps from the same local Tracking stream.
  return previous === null || fix.recordedAt > previous.recordedAt
    || (fix.recordedAt === previous.recordedAt && !samePoint(previous, fix))
}

function longitudeDelta(from: number, to: number) {
  return ((to - from + 540) % 360) - 180
}

function metersBetween(from: TrackingPoint, to: TrackingPoint) {
  const meanLatitude = (from.lat + to.lat) * Math.PI / 360
  return METERS_PER_DEGREE * Math.hypot(to.lat - from.lat, longitudeDelta(from.lng, to.lng) * Math.cos(meanLatitude))
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null ? value as Record<string, unknown> : null
}

function positiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

function utcMillis(value: unknown) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(value)) return NaN
  const parsed = new Date(value)
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 19) === value.slice(0, 19)
    ? parsed.getTime() : NaN
}

function readRoute(value: unknown, orderId: number, destination: TrackingPoint): Route | null {
  const body = asRecord(value)
  if (!body || body.orderId !== orderId || (body.vehicleProfile !== 'motorcycle' && body.vehicleProfile !== 'car')) return null
  const { distanceMeters, durationMs } = body
  if (typeof distanceMeters !== 'number' || !Number.isFinite(distanceMeters) || distanceMeters <= 0
    || !positiveInteger(durationMs)) return null

  const origin = asRecord(body.origin)
  if (!origin || !positiveInteger(origin.locationId) || typeof origin.lat !== 'number' || typeof origin.lng !== 'number') return null
  const originPoint = { lat: origin.lat, lng: origin.lng }
  // These are both server UTC instants. Tracking's offset-free recordedAt is never used here.
  if (!isPoint(originPoint) || !(utcMillis(origin.recordedAt) <= utcMillis(body.generatedAt))) return null

  const geometry = asRecord(body.geometry)
  const coordinates: unknown = geometry?.coordinates
  if (geometry?.type !== 'LineString' || !Array.isArray(coordinates) || coordinates.length < 2
    || coordinates.length > 20_000) return null

  const segments: Segment[] = []
  let previous: TrackingPoint | null = null
  let length = 0
  for (const coordinate of coordinates as unknown[]) {
    if (!Array.isArray(coordinate) || coordinate.length !== 2
      || typeof coordinate[0] !== 'number' || typeof coordinate[1] !== 'number') return null
    const point = { lng: coordinate[0], lat: coordinate[1] }
    if (!isPoint(point)) return null
    if (previous === null) {
      if (metersBetween(point, originPoint) > ENDPOINT_METERS) return null
    } else {
      const segmentLength = metersBetween(previous, point)
      if (segmentLength > 0) {
        segments.push({ from: previous, to: point, start: length, length: segmentLength })
        length += segmentLength
      }
    }
    previous = point
  }
  if (segments.length === 0 || previous === null || metersBetween(previous, destination) > ENDPOINT_METERS) return null

  return {
    segments, length, providerDistanceMeters: distanceMeters, durationMs, lastMatchedFix: originPoint,
    match: { index: 0, point: segments[0].from, progress: 0, distance: 0 },
    offRoute: [], confirmed: false,
  }
}

function projectWithin(fix: TrackingPoint, route: Route, lookAhead: number): Match {
  const start = route.match.progress
  const end = start + lookAhead
  const scale = Math.cos(fix.lat * Math.PI / 180)
  const candidates: Match[] = []
  for (let index = route.match.index; index < route.segments.length; index += 1) {
    const segment = route.segments[index]
    if (segment.start > end) break
    const lng = longitudeDelta(segment.from.lng, segment.to.lng)
    const dx = lng * scale
    const dy = segment.to.lat - segment.from.lat
    const fraction = (longitudeDelta(segment.from.lng, fix.lng) * scale * dx + (fix.lat - segment.from.lat) * dy)
      / (dx * dx + dy * dy)
    const lower = Math.max(0, (start - segment.start) / segment.length)
    const upper = Math.min(1, (end - segment.start) / segment.length)
    const t = Math.min(upper, Math.max(lower, fraction))
    const point = t === 0 ? segment.from : t === 1 ? segment.to : {
      lat: segment.from.lat + dy * t, lng: ((segment.from.lng + lng * t + 540) % 360) - 180,
    }
    candidates.push({ index, point, progress: Math.max(start, segment.start + segment.length * t),
      distance: metersBetween(point, fix) })
  }

  const nearest = candidates.reduce((best, candidate) => candidate.distance < best.distance ? candidate : best)
  // Prefer earlier progress among near ties, within the same distance band.
  const bandLimit = nearest.distance <= TRIM_METERS + ROUNDING_METERS
    ? TRIM_METERS + ROUNDING_METERS : OFF_ROUTE_METERS + ROUNDING_METERS
  return candidates.find(candidate => candidate.distance <= Math.min(bandLimit, nearest.distance + MATCH_TOLERANCE_METERS))
    ?? nearest
}

function project(fix: TrackingPoint, route: Route): Match {
  // Prefer continuity around crossings. Search farther only after a large GPS gap with no plausible local segment.
  const movement = metersBetween(route.lastMatchedFix, fix)
  const local = projectWithin(fix, route, Math.min(MAX_LOOK_AHEAD_METERS, MIN_LOOK_AHEAD_METERS + movement * 2))
  const clipped = local.progress >= route.match.progress + MAX_LOOK_AHEAD_METERS - ROUNDING_METERS
  if (movement <= MAX_LOOK_AHEAD_METERS || local.distance <= TRIM_METERS + ROUNDING_METERS
    || (!clipped && local.distance <= OFF_ROUTE_METERS + ROUNDING_METERS)) return local
  return projectWithin(fix, route, Math.min(MAX_SPARSE_LOOK_AHEAD_METERS, movement + MIN_LOOK_AHEAD_METERS))
}

function observeRoute(route: Route, fix: TrackingPoint) {
  const match = project(fix, route)
  if (match.distance <= OFF_ROUTE_METERS + ROUNDING_METERS) {
    route.offRoute = []
    if (match.distance <= TRIM_METERS + ROUNDING_METERS) {
      route.match = match
      route.lastMatchedFix = fix
    }
  } else if (route.offRoute.length < OFF_ROUTE_FIXES
    && route.offRoute.every(point => metersBetween(point, fix) >= DISTINCT_FIX_METERS)) {
    route.offRoute.push(fix)
  }
  return match.distance
}

function failureReason(failure: TrackingRouteFailure): RouteReason {
  switch (failure.status) {
    case 401: case 403: return 'ROUTE_FORBIDDEN'
    case 404: return 'ROUTE_ORDER_NOT_FOUND'
    case 429: return 'ROUTE_RATE_LIMITED'
    case 503: return 'ROUTE_UNAVAILABLE'
    case 504: return 'ROUTE_UPSTREAM_TIMEOUT'
  }
  const reason = backendReasons.find(code => code === failure.code)
  if (reason) return reason
  if (failure.status === 422) return 'ROUTE_NOT_FOUND'
  return failure.status === undefined ? 'ROUTE_TRANSPORT_ERROR' : 'ROUTE_UPSTREAM_ERROR'
}

/** Deterministic route policy. The caller drives the clock, dispatches requests and renders GPS separately. */
export function createTrackingRoutePlanner(nowMs: () => number) {
  let state: TrackingRouteState = { status: 'inactive' }
  let orderId: number | null = null
  let sessionId: string | null = null
  let destination: TrackingPoint | null = null
  let terminal = false
  let latestFix: TrackingLocation | null = null
  let route: Route | null = null
  let flight: { request: RouteRequest; controller: AbortController; fixes: TrackingPoint[] } | null = null
  let stopped: RouteReason | null = null
  let waitForFix: { after: TrackingLocation; reason: RouteReason } | null = null
  let retried = false
  const usage = new Map<number, { count: number; lastStartedAt: number }>()

  function clearActivity() {
    const previous = flight
    flight = null
    latestFix = null
    route = null
    state = { status: 'inactive' }
    previous?.controller.abort()
  }

  function schedule(id: number) {
    route = null
    const budget = usage.get(id)
    if (budget && budget.count >= REQUEST_BUDGET) {
      state = { status: 'unavailable', reason: 'ROUTE_CLIENT_BUDGET_EXHAUSTED' }
      return
    }
    const now = nowMs()
    const startAtMs = budget ? Math.max(now, budget.lastStartedAt + COOLDOWN_MS) : now
    state = { status: 'updating', ticket: Object.freeze({ startAtMs }) }
  }

  function showRoute(id: number, current: Route, fix: TrackingPoint, target: TrackingPoint, distance: number) {
    if (current.offRoute.length >= OFF_ROUTE_FIXES) {
      schedule(id)
      return
    }
    if (!current.confirmed && distance > TRIM_METERS + ROUNDING_METERS) {
      state = { status: 'awaiting-fix' }
      return
    }
    current.confirmed = true
    retried = false
    const remaining = Math.max(0, current.length - current.match.progress)
    if (remaining <= TRIM_METERS && metersBetween(fix, target) <= ARRIVAL_METERS) {
      state = { status: 'arrived', remainingDistanceMeters: 0, estimatedDurationMs: 0 }
      return
    }

    const first = current.match.point
    const coordinates: Coordinate[] = [[first.lng, first.lat]]
    for (let index = current.match.index; index < current.segments.length; index += 1) {
      const end = current.segments[index].to
      if (coordinates.length > 1 || !samePoint(first, end)) coordinates.push([end.lng, end.lat])
    }
    if (coordinates.length < 2 || remaining === 0) {
      state = { status: 'awaiting-fix' }
      return
    }
    state = {
      status: 'ready', geometry: { type: 'LineString', coordinates },
      originalGeometryDistanceMeters: current.length, providerDistanceMeters: current.providerDistanceMeters,
      remainingDistanceMeters: remaining, estimatedDurationMs: Math.round(current.durationMs * (remaining / current.length)),
    }
  }

  function update(input: TrackingRouteInput) {
    if (orderId !== input.orderId || sessionId !== input.sessionId) {
      clearActivity()
      orderId = input.orderId
      sessionId = input.sessionId
      terminal = false
      stopped = null
      waitForFix = null
      retried = false
    }

    const target = isPoint(input.deliveryPoint) ? input.deliveryPoint : null
    if (!samePoint(destination, target)) {
      clearActivity()
      destination = target && { ...target }
    }
    terminal ||= input.status === 'DELIVERED' || input.status === 'CANCELLED' || input.status === 'REJECTED'
    const fix = readTrackingLocation(input.location)
    if (terminal || !positiveInteger(orderId) || !sessionId || !target || !fix || input.stale
      || input.status !== 'DELIVERING' || input.connection !== 'live') {
      clearActivity()
      return
    }
    if (stopped) {
      state = { status: 'unavailable', reason: stopped }
      return
    }

    const previous = latestFix
    if (!isNewFix(previous, fix)) return
    latestFix = fix

    if (flight) {
      if (!samePoint(flight.fixes.at(-1) ?? null, fix)) {
        if (flight.fixes.length === MAX_WAITING_FIXES) flight.fixes.shift()
        flight.fixes.push(fix)
      }
    } else if (route) {
      if (!samePoint(previous, fix)) showRoute(orderId, route, fix, target, observeRoute(route, fix))
    } else if (waitForFix) {
      if (isNewFix(waitForFix.after, fix)) {
        waitForFix = null
        retried = true
        schedule(orderId)
      } else {
        state = { status: 'unavailable', reason: waitForFix.reason }
      }
    } else if (state.status === 'inactive') {
      schedule(orderId)
    }
  }

  function begin(ticket: RouteTicket): RouteRequest | null {
    const now = nowMs()
    if (state.status !== 'updating' || state.ticket !== ticket || now < ticket.startAtMs || flight || !orderId || !latestFix) return null

    const controller = new AbortController()
    const request = Object.freeze({ orderId, signal: controller.signal })
    flight = { request, controller, fixes: [latestFix] }
    usage.set(orderId, { count: (usage.get(orderId)?.count ?? 0) + 1, lastStartedAt: now })
    state = { status: 'updating', ticket: null }
    return request
  }

  function complete(request: RouteRequest, value: unknown) {
    if (flight?.request !== request || !destination || !latestFix) return
    const { fixes } = flight
    flight = null
    route = readRoute(value, request.orderId, destination)
    if (!route) {
      stopped = 'ROUTE_RESPONSE_INVALID'
      state = { status: 'unavailable', reason: stopped }
      return
    }

    let distance = 0
    for (const fix of fixes) distance = observeRoute(route, fix)
    showRoute(request.orderId, route, latestFix, destination, distance)
  }

  function fail(request: RouteRequest, failure: TrackingRouteFailure = {}) {
    if (flight?.request !== request) return
    flight = null
    const reason = failureReason(failure)
    if (!retried && (reason === 'ROUTE_TRANSPORT_ERROR' || reason === 'ROUTE_UPSTREAM_ERROR' || reason === 'ROUTE_UPSTREAM_TIMEOUT')) {
      retried = true
      schedule(request.orderId)
    } else {
      if (!retried && latestFix && (reason === 'ROUTE_GPS_UNAVAILABLE' || reason === 'ROUTE_ORDER_NOT_READY'
        || reason === 'ROUTE_CONTEXT_CHANGED')) {
        waitForFix = { after: latestFix, reason }
      } else {
        stopped = reason
      }
      state = { status: 'unavailable', reason }
    }
  }

  return { update, begin, complete, fail, getState: () => state, clear: clearActivity }
}
