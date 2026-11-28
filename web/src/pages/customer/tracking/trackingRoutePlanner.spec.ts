import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  createTrackingRoutePlanner, type TrackingRouteFailure, type TrackingRouteInput,
} from './trackingRoutePlanner.ts'
import type { TrackingPoint } from './trackingSnapshot.ts'

const METERS_PER_DEGREE = 111_195
const BASE_LAT = 10.78
const BASE_LNG = 106.70
const LONGITUDE_SCALE = METERS_PER_DEGREE * Math.cos(BASE_LAT * Math.PI / 180)

function point(east: number, north = 0): TrackingPoint {
  return { lat: BASE_LAT + north / METERS_PER_DEGREE, lng: BASE_LNG + east / LONGITUDE_SCALE }
}

function coordinates(p: TrackingPoint) {
  return [p.lng, p.lat]
}

const straight = [point(0), point(100), point(200), point(300)]
const destination = straight.at(-1)!

function response(points = straight, options: { orderId?: number; origin?: TrackingPoint } = {}) {
  return {
    orderId: options.orderId ?? 11,
    vehicleProfile: 'motorcycle',
    geometry: { type: 'LineString', coordinates: points.map(coordinates) },
    distanceMeters: 330,
    durationMs: 120_000,
    origin: { locationId: 100, ...options.origin ?? points[0], recordedAt: '2026-11-25T05:34:00.123456Z' },
    generatedAt: '2026-11-25T05:34:01Z',
  }
}

function setup(target = destination) {
  let time = 1000
  let seconds = 0
  const planner = createTrackingRoutePlanner(() => time)
  let context: TrackingRouteInput = {
    orderId: 11, sessionId: 'customer-session-1', status: 'DELIVERING', connection: 'live',
    stale: false, location: null, deliveryPoint: target,
  }

  function update(changes: Partial<TrackingRouteInput> = {}) {
    context = { ...context, ...changes }
    planner.update(context)
  }

  function fix(position: TrackingPoint, second = seconds++) {
    update({ location: { ...position, recordedAt: `2026-11-25T12:34:${String(second).padStart(2, '0')}` } })
  }

  function ticket() {
    const state = planner.getState()
    assert.equal(state.status, 'updating')
    assert.ok(state.ticket)
    return state.ticket
  }

  function begin() {
    const request = planner.begin(ticket())
    assert.ok(request)
    return request
  }

  function ready() {
    const state = planner.getState()
    assert.equal(state.status, 'ready')
    return state
  }

  function load(points = straight) {
    fix(points[0])
    const request = begin()
    planner.complete(request, response(points))
    return ready()
  }

  return {
    planner, update, fix, ticket, begin, ready, load,
    advance: (ms: number) => { time += ms },
  }
}

function approx(actual: number, expected: number, tolerance = 0.05) {
  assert.ok(Math.abs(actual - expected) < tolerance, `${actual} was not within ${tolerance} of ${expected}`)
}

test('accepts the first backend LineString, reading UTC separately from local Tracking time', () => {
  const h = setup()
  const state = h.load()
  assert.deepEqual(state.geometry, { type: 'LineString', coordinates: straight.map(coordinates) })
  approx(state.remainingDistanceMeters, 300)
  approx(state.originalGeometryDistanceMeters, 300)
  assert.equal(state.providerDistanceMeters, 330)
  assert.equal(state.estimatedDurationMs, 120_000)
})

test('trims a projection inside a segment and computes distance and ETA without requesting again', () => {
  const h = setup()
  h.load()
  h.fix(point(75, 10))
  const state = h.ready()
  approx(state.remainingDistanceMeters, 225)
  approx(state.geometry.coordinates[0][0], point(75).lng, 0.0000001)
  approx(state.geometry.coordinates[0][1], BASE_LAT, 0.0000001)
  assert.deepEqual(state.geometry.coordinates.slice(1), straight.slice(1).map(coordinates))
  assert.equal(state.estimatedDurationMs, 90_000)
  h.advance(30_000)
  assert.equal(h.planner.getState(), state)
})

test('the route projection never changes the input coordinates used by the GPS marker', () => {
  const h = setup()
  h.load()
  const location = Object.freeze({ ...point(75, 12), recordedAt: '2026-11-25T12:34:05' })
  h.update({ location })
  assert.notEqual(h.ready().geometry.coordinates[0][1], location.lat)
  assert.deepEqual(location, { ...point(75, 12), recordedAt: '2026-11-25T12:34:05' })
})

test('does not trim backwards on a small reversal and confirms a real U-turn away from remaining route', () => {
  const h = setup()
  h.load()
  h.fix(point(150))
  const progress = h.ready().remainingDistanceMeters
  h.fix(point(141))
  assert.equal(h.ready().remainingDistanceMeters, progress)
  h.fix(point(99))
  h.fix(point(91))
  assert.equal(h.ready().remainingDistanceMeters, progress)
  h.fix(point(82))
  assert.equal(h.ticket().startAtMs, 16_000)
})

test('a single noisy fix keeps the route and a fix within trim distance clears that miss', () => {
  const h = setup()
  h.load()
  h.fix(point(20, 100))
  approx(h.ready().remainingDistanceMeters, 300)
  h.fix(point(25, 12))
  h.fix(point(33, 58))
  h.fix(point(43, 58))
  approx(h.ready().remainingDistanceMeters, 275)
})

test('the 30 m boundary trims and the 45 m boundary keeps geometry and resets the miss sequence', () => {
  const h = setup()
  h.load()
  h.fix(point(20, 30))
  approx(h.ready().remainingDistanceMeters, 280)
  h.fix(point(32, 46))
  h.fix(point(39, 46))
  h.fix(point(49, 45))
  approx(h.ready().remainingDistanceMeters, 280)
  h.fix(point(59, 46))
  h.fix(point(69, 46))
  h.fix(point(79, 30.1))
  approx(h.ready().remainingDistanceMeters, 280)
  h.fix(point(89, 46))
  h.fix(point(99, 46))
  assert.equal(h.planner.getState().status, 'ready')
  h.fix(point(110, 46))
  assert.equal(h.planner.getState().status, 'updating')
})

test('three different off-route fixes in the same timestamp second confirm deviation', () => {
  const h = setup()
  h.load()
  h.fix(point(20, 60), 3)
  h.fix(point(30, 60), 3)
  h.fix(point(40, 60), 3)
  assert.equal(h.ticket().startAtMs, 16_000)
  assert.equal('geometry' in h.planner.getState(), false)
  assert.equal('estimatedDurationMs' in h.planner.getState(), false)
})

test('duplicate frame, stationary GPS and repeating positions do not accumulate a false deviation', () => {
  const h = setup()
  h.load()
  const a = point(20, 60)
  const b = point(27, 60)
  h.fix(a)
  h.fix(a, 1)
  h.fix(a)
  h.fix(b)
  h.fix(a)
  h.fix(point(21, 60))
  h.fix(b)
  assert.equal(h.planner.getState().status, 'ready')
  h.fix(point(35, 60), 0) // older timestamp
  assert.equal(h.planner.getState().status, 'ready')
  h.fix(point(35, 60))
  assert.equal(h.planner.getState().status, 'updating')
})

test('honors the exact 15 second cooldown and admits one request per ticket', () => {
  const h = setup()
  h.load()
  h.fix(point(20, 60))
  h.fix(point(30, 60))
  h.fix(point(40, 60))
  const ticket = h.ticket()
  h.advance(14_999)
  assert.equal(h.planner.begin(ticket), null)
  h.advance(1)
  assert.equal(h.planner.begin({ ...ticket }), null)
  const request = h.planner.begin(ticket)
  assert.equal(request?.orderId, 11)
  assert.equal(request?.signal.aborted, false)
  assert.equal(h.planner.begin(ticket), null)
  assert.deepEqual(h.planner.getState(), { status: 'updating', ticket: null })
})

test('holds the demo budget through reconnect, route failure and re-entry to the same order', () => {
  const h = setup()
  h.fix(point(0))
  for (let n = 0; n < 5; n += 1) {
    const request = h.begin()
    if (n === 3) h.planner.fail(request, { status: 504 })
    else h.planner.complete(request, response())
    h.update({ connection: 'offline' })
    h.advance(15_000)
    h.update({ connection: 'live' })
  }
  assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason: 'ROUTE_CLIENT_BUDGET_EXHAUSTED' })
  h.update({ orderId: 12 })
  assert.ok(h.begin())
  h.update({ orderId: 11 })
  assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason: 'ROUTE_CLIENT_BUDGET_EXHAUSTED' })
})

test('uses fixes arriving in flight to trim a late but still valid route', () => {
  const h = setup()
  h.fix(point(0))
  const request = h.begin()
  h.fix(point(38))
  h.fix(point(73))
  h.planner.complete(request, response())
  approx(h.ready().remainingDistanceMeters, 227)
})

test('a sparse on-route jump beyond 250 m trims without a reroute', () => {
  const path = [point(0), point(300), point(600), point(900)]
  const h = setup(path.at(-1))
  h.load(path)
  h.fix(point(580))
  approx(h.ready().remainingDistanceMeters, 320)
})

test('a sparse fix near the destination is matched beyond a clipped 30–45 m candidate', () => {
  const path = [point(0), point(130), point(320), point(600)]
  const h = setup(path.at(-1))
  h.load(path)
  h.fix(point(300))
  h.fix(point(592))
  assert.deepEqual(h.planner.getState(), { status: 'arrived', remainingDistanceMeters: 0, estimatedDurationMs: 0 })
})

test('a reconnect response with an older coalesced origin can match the latest distant fix', () => {
  const path = [point(0), point(300), point(600), point(900)]
  const h = setup(path.at(-1))
  h.load(path)
  h.update({ connection: 'offline' })
  h.fix(point(520))
  h.update({ connection: 'live' })
  h.advance(15_000)
  h.planner.complete(h.begin(), response(path))
  approx(h.ready().remainingDistanceMeters, 380)
})

test('a new route in the 30–45 m band stays hidden until a fix can be trimmed', () => {
  const h = setup()
  h.fix(point(40, 40))
  h.planner.complete(h.begin(), response())
  assert.deepEqual(h.planner.getState(), { status: 'awaiting-fix' })
  h.fix(point(40, 40))
  h.fix(point(42, 38))
  assert.deepEqual(h.planner.getState(), { status: 'awaiting-fix' })
  h.fix(point(65, 25))
  approx(h.ready().remainingDistanceMeters, 235)
})

test('on a cooldown ticket uses the latest fix and allows a different coalesced response origin', () => {
  const h = setup()
  h.load()
  h.fix(point(20, 60))
  h.fix(point(30, 60))
  h.fix(point(40, 60))
  h.fix(point(120, 60))
  h.advance(15_000)
  const request = h.begin()
  const coalesced = [point(10, 60), point(130, 60), point(220, 60), destination]
  h.planner.complete(request, response(coalesced))
  const current = h.ready()
  assert.ok(current.originalGeometryDistanceMeters - current.remainingDistanceMeters > 100)
  approx(current.geometry.coordinates[0][0], point(120).lng, 0.0000001)
})

test('does not render an obsolete in-flight response after three new off-route fixes', () => {
  const h = setup()
  h.fix(point(0))
  const old = h.begin()
  h.fix(point(20, 71))
  h.fix(point(30, 72))
  h.fix(point(40, 71))
  h.planner.complete(old, response())
  assert.equal(h.ticket().startAtMs, 16_000)
  assert.equal('geometry' in h.planner.getState(), false)
  h.advance(15_000)
  const replacement = h.begin()
  const newRoute = [point(40, 71), point(200, 35), destination]
  h.planner.complete(replacement, response(newRoute))
  const state = h.ready()
  h.planner.complete(old, response())
  assert.equal(h.planner.getState(), state)
})

test('three earlier off-route fixes followed by recovery do not invalidate a pending response', () => {
  const h = setup()
  h.fix(point(0))
  const request = h.begin()
  h.fix(point(20, 71))
  h.fix(point(30, 72))
  h.fix(point(40, 71))
  h.fix(point(85))
  h.planner.complete(request, response())
  approx(h.ready().remainingDistanceMeters, 215)
})

test('holds a mismatching coalesced route until a new fix confirms it or a third miss invalidates it', () => {
  const h = setup()
  h.fix(point(80, 75))
  const request = h.begin()
  h.planner.complete(request, response())
  assert.deepEqual(h.planner.getState(), { status: 'awaiting-fix' })
  h.fix(point(80, 75))
  h.fix(point(89, 75))
  assert.deepEqual(h.planner.getState(), { status: 'awaiting-fix' })
  h.fix(point(101, 76))
  assert.equal(h.ticket().startAtMs, 16_000)
})

test('uses the earlier segment on parallel road legs when both matches are plausible', () => {
  const path = [point(0), point(100), point(100, 10), point(20, 10), point(20, 120)]
  const h = setup(path.at(-1))
  h.load(path)
  h.fix(point(70, 6))
  const state = h.ready()
  approx(state.originalGeometryDistanceMeters - state.remainingDistanceMeters, 70)
  approx(state.geometry.coordinates[0][1], BASE_LAT, 0.0000001)
})

test('does not jump to the later leg at a self-crossing', () => {
  const path = [point(0), point(100, 100), point(0, 100), point(100), point(180)]
  const h = setup(path.at(-1))
  h.load(path)
  h.fix(point(45, 45))
  h.fix(point(50, 50))
  const state = h.ready()
  approx(state.originalGeometryDistanceMeters - state.remainingDistanceMeters, Math.sqrt(2) * 50)
  assert.deepEqual(state.geometry.coordinates[1], coordinates(path[1]))
})

test('compacts consecutive duplicate polyline vertices, including the terminal vertex', () => {
  const path = [point(0), point(0), point(100), point(100), destination, destination]
  const h = setup()
  const initial = h.load(path)
  assert.equal(initial.geometry.coordinates.length, 3)
  h.fix(point(100))
  assert.deepEqual(h.ready().geometry.coordinates, [coordinates(point(100)), coordinates(destination)])
})

test('near destination requires both short remaining progress and proximity to the actual delivery point', () => {
  const h = setup()
  h.load()
  h.fix(point(200))
  h.fix(point(265))
  assert.equal(h.planner.getState().status, 'ready')
  h.fix(point(290))
  assert.deepEqual(h.planner.getState(), { status: 'arrived', remainingDistanceMeters: 0, estimatedDurationMs: 0 })
})

test('physical proximity alone does not mark arrived on a long road loop', () => {
  const path = [point(0), point(110), point(110, 130), point(10, 130), point(10, 5)]
  const h = setup(path.at(-1))
  h.load(path)
  h.fix(point(7, 5))
  assert.ok(h.ready().remainingDistanceMeters > 400)
})

test('a snapped endpoint outside arrival range never becomes a fictitious straight delivery segment', () => {
  const farDoor = point(320)
  const h = setup(farDoor)
  h.fix(point(0))
  const request = h.begin()
  h.planner.complete(request, response(straight))
  h.fix(point(150))
  h.fix(point(300))
  assert.deepEqual(h.planner.getState(), { status: 'awaiting-fix' })
})

const invalidResponses: [string, (route: ReturnType<typeof response>) => unknown][] = [
  ['wrong order', route => ({ ...route, orderId: 12 })],
  ['unsupported profile', route => ({ ...route, vehicleProfile: 'bicycle' })],
  ['different GeoJSON type', route => ({ ...route, geometry: { ...route.geometry, type: 'Point' } })],
  ['swapped axes', route => ({ ...route, geometry: { ...route.geometry, coordinates: [[BASE_LAT, BASE_LNG], [10, 107]] } })],
  ['nonfinite coordinate', route => ({ ...route, geometry: { ...route.geometry, coordinates: [[NaN, BASE_LAT], coordinates(destination)] } })],
  ['all duplicate vertices', route => ({ ...route, geometry: { ...route.geometry, coordinates: [coordinates(point(0)), coordinates(point(0))] } })],
  ['detached origin', route => ({ ...route, origin: { ...route.origin, ...point(0, 160) } })],
  ['detached destination', () => response([point(0), point(100)])],
  ['nonfinite provider distance', route => ({ ...route, distanceMeters: Infinity })],
  ['zero duration', route => ({ ...route, durationMs: 0 })],
  ['noninteger origin id', route => ({ ...route, origin: { ...route.origin, locationId: 1.5 } })],
  ['offset-free route origin', route => ({ ...route, origin: { ...route.origin, recordedAt: '2026-11-25T05:34:00' } })],
  ['invalid UTC calendar date', route => ({ ...route, generatedAt: '2026-11-31T05:34:01Z' })],
  ['route generated before origin', route => ({ ...route, generatedAt: '2026-11-25T05:33:59Z' })],
]

for (const [label, corrupt] of invalidResponses) {
  test(`rejects invalid response with ${label}`, () => {
    const h = setup()
    h.fix(point(0))
    h.planner.complete(h.begin(), corrupt(response()))
    assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason: 'ROUTE_RESPONSE_INVALID' })
  })
}

const invalidations: [string, Partial<TrackingRouteInput>][] = [
  ['connectivity loss', { connection: 'offline' }],
  ['stale GPS', { stale: true }],
  ['another order', { orderId: 12 }],
  ['another session', { sessionId: 'customer-session-2' }],
  ['logout', { sessionId: null }],
  ['new delivery point', { deliveryPoint: point(310, 25) }],
  ['invalid delivery point', { deliveryPoint: { lat: 91, lng: BASE_LNG } }],
  ['terminal status', { status: 'DELIVERED' }],
]

for (const [label, changes] of invalidations) {
  test(`${label} aborts the active request and ignores both old success and old failure`, () => {
    const h = setup()
    h.fix(point(0))
    const old = h.begin()
    h.update(changes)
    assert.equal(old.signal.aborted, true)
    const newState = h.planner.getState()
    h.planner.complete(old, response())
    h.planner.fail(old, { status: 504 })
    assert.equal(h.planner.getState(), newState)
    assert.equal('geometry' in newState, false)
  })
}

test('disconnect clears a ready route and ETA, and reconnect respects the same order cooldown', () => {
  const h = setup()
  h.load()
  h.update({ connection: 'reconnecting' })
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
  h.update({ connection: 'live' })
  assert.equal(h.ticket().startAtMs, 16_000)
})

test('stale location clears geometry and ETA until Tracking declares a new eligible fix', () => {
  const h = setup()
  h.load()
  h.update({ stale: true })
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
  h.fix(point(20))
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
  h.update({ stale: false })
  assert.equal(h.ticket().startAtMs, 16_000)
})

test('a terminal order never resumes route from a late DELIVERING state', () => {
  const h = setup()
  h.load()
  h.update({ status: 'CANCELLED' })
  h.update({ status: 'DELIVERING' })
  h.fix(point(120))
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
})

test('requires active delivery, session, connection, valid local timestamp and coordinates', () => {
  const h = setup()
  h.update({ status: 'PREPARING' })
  h.fix(point(0))
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
  h.update({ status: 'DELIVERING', location: { ...point(0), recordedAt: '2026-11-25T12:34:01Z' } })
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
  h.update({ location: { lat: 10.78, lng: 200, recordedAt: '2026-11-25T12:34:01' } })
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
  h.fix(point(0))
  assert.ok(h.begin())
})

test('a superseded ticket cannot start and clear aborts the replacement', () => {
  const h = setup()
  h.fix(point(0))
  const old = h.ticket()
  h.update({ orderId: 12 })
  assert.equal(h.planner.begin(old), null)
  const replacement = h.begin()
  h.planner.clear()
  assert.equal(replacement.signal.aborted, true)
  assert.deepEqual(h.planner.getState(), { status: 'inactive' })
})

const stableFailures: [TrackingRouteFailure, string][] = [
  [{ status: 429 }, 'ROUTE_RATE_LIMITED'],
  [{ status: 422, code: 'ROUTE_PROFILE_UNSUPPORTED' }, 'ROUTE_PROFILE_UNSUPPORTED'],
  [{ status: 422, code: 'ROUTE_NOT_FOUND' }, 'ROUTE_NOT_FOUND'],
  [{ status: 503 }, 'ROUTE_UNAVAILABLE'],
  [{ status: 403 }, 'ROUTE_FORBIDDEN'],
  [{ status: 404 }, 'ROUTE_ORDER_NOT_FOUND'],
]

for (const [failure, reason] of stableFailures) {
  test(`does not auto-retry ${reason} even across reconnect in the same scope`, () => {
    const h = setup()
    h.fix(point(0))
    h.planner.fail(h.begin(), failure)
    h.advance(60_000)
    h.update({ connection: 'offline' })
    h.update({ connection: 'live' })
    h.fix(point(50))
    assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason })
  })
}

test('timeout gets one cooldown-limited retry without ETA and a second timeout stops auto-retry', () => {
  const h = setup()
  h.fix(point(0))
  h.planner.fail(h.begin(), { status: 504, code: 'ROUTE_UPSTREAM_TIMEOUT' })
  assert.equal(h.ticket().startAtMs, 16_000)
  assert.equal('estimatedDurationMs' in h.planner.getState(), false)
  h.advance(15_000)
  h.planner.fail(h.begin(), { status: 504 })
  h.advance(15_000)
  h.fix(point(10))
  assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason: 'ROUTE_UPSTREAM_TIMEOUT' })
})

for (const code of ['ROUTE_GPS_UNAVAILABLE', 'ROUTE_ORDER_NOT_READY', 'ROUTE_CONTEXT_CHANGED']) {
  test(`${code} waits for a newer Tracking fix before its one allowed retry`, () => {
    const h = setup()
    h.fix(point(0))
    h.planner.fail(h.begin(), { status: 409, code })
    h.advance(15_000)
    h.update({ connection: 'offline' })
    h.update({ connection: 'live' })
    h.fix(point(0), 0)
    assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason: code })
    h.fix(point(0))
    h.planner.fail(h.begin(), { status: 409, code })
    h.fix(point(20))
    assert.deepEqual(h.planner.getState(), { status: 'unavailable', reason: code })
  })
}

test('upstream 502 gets one retry and a successful replacement restores normal planning', () => {
  const h = setup()
  h.fix(point(0))
  h.planner.fail(h.begin(), { status: 502, code: 'ROUTE_UPSTREAM_ERROR' })
  h.advance(15_000)
  const body = { ...response(), vehicleProfile: 'car' }
  h.planner.complete(h.begin(), body)
  assert.equal(h.ready().status, 'ready')
  h.fix(point(20, 60))
  h.fix(point(30, 60))
  h.fix(point(40, 60))
  assert.equal(h.ticket().startAtMs, 31_000)
})
