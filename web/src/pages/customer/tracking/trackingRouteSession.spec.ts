import assert from 'node:assert/strict'
import { test } from 'node:test'
import { AxiosError } from 'axios'
import { createTrackingRouteSession, type TrackingRouteView } from './trackingRouteSession.ts'
import type { TrackingRouteInput } from './trackingRoutePlanner.ts'

const source = { lng: 106.70, lat: 10.78 }
const destination = { lng: 106.704, lat: 10.78 }
const input: TrackingRouteInput = {
  orderId: 501, sessionId: 'fixture-session', status: 'DELIVERING', connection: 'live', stale: false,
  location: { ...source, recordedAt: '2026-11-25T22:00:01' }, deliveryPoint: destination,
}
const payload = {
  orderId: 501, vehicleProfile: 'motorcycle',
  geometry: { type: 'LineString', coordinates: [[source.lng, source.lat], [destination.lng, destination.lat]] },
  distanceMeters: 500, durationMs: 90_000,
  origin: { ...source, locationId: 1, recordedAt: '2026-11-25T15:00:01Z' },
  generatedAt: '2026-11-25T15:00:02Z',
}

function setup() {
  let now = 10
  let sessionCurrent = true
  const entries = new Map<() => void, number>()
  const events: TrackingRouteView[] = []
  const requests: { orderId: number; signal: AbortSignal; finish: (response: unknown) => void; fail: (error: unknown) => void }[] = []
  const dependencies = {
    nowMs: () => now,
    schedule(run: () => void, delay: number) {
      entries.set(run, now + delay)
      return () => { entries.delete(run) }
    },
    load(orderId: number, signal: AbortSignal) {
      return new Promise<unknown>((finish, fail) => requests.push({ orderId, signal, finish, fail }))
    },
    isCurrent: () => sessionCurrent,
    emit: (view: TrackingRouteView) => { events.push(view) },
  }
  function advance(ms = 0) {
    now += ms
    for (const [work, due] of entries) {
      if (due <= now) {
        entries.delete(work)
        work()
      }
    }
  }
  return {
    session: createTrackingRouteSession(dependencies), requests, entries, events, dependencies, advance,
    replaceSession: () => { sessionCurrent = false },
  }
}

test('multiple GPS updates schedule one ticket and one dispatch with the current order', async () => {
  const h = setup()
  h.session.update(input)
  h.session.update({ ...input, location: { ...input.location!, lng: 106.7005 } })
  assert.equal(h.entries.size, 1)
  h.advance()
  h.session.update({ ...input, location: { ...input.location!, lng: 106.701 } })
  h.advance()
  assert.equal(h.requests.length, 1)
  assert.equal(h.requests[0].orderId, 501)
  h.requests[0].finish(payload)
  await Promise.resolve()
  assert.equal(h.events.at(-1)?.state.status, 'ready')
  assert.equal(h.entries.size, 0)
})

test('StrictMode setup-cleanup-setup cancels the first task before calling Axios', () => {
  const h = setup()
  h.session.update(input)
  const first = [...h.entries.keys()][0]
  h.session.dispose()
  const remount = createTrackingRouteSession(h.dependencies)
  remount.update(input)
  first()
  h.advance()
  assert.equal(h.requests.length, 1)
  remount.dispose()
  assert.equal(h.requests[0].signal.aborted, true)
})

test('stale input aborts HTTP and late response cannot replace the new waiting state', async () => {
  const h = setup()
  h.session.update(input)
  h.advance()
  h.session.update({ ...input, stale: true })
  assert.equal(h.requests[0].signal.aborted, true)
  h.requests[0].finish(payload)
  await Promise.resolve()
  assert.equal(h.events.at(-1)?.state.status, 'inactive')
  h.session.update(input)
  h.advance(14_999)
  assert.equal(h.requests.length, 1)
  h.advance(1)
  assert.equal(h.requests.length, 2)
})

test('identity changes and terminal cancel timers and HTTP without stale emissions', async () => {
  const h = setup()
  h.session.update(input)
  h.advance()
  h.session.update({ ...input, status: 'DELIVERED' })
  assert.equal(h.requests[0].signal.aborted, true)
  h.session.update(input)
  assert.equal(h.entries.size, 0)
  h.replaceSession()
  h.requests[0].finish(payload)
  await Promise.resolve()
  assert.equal(h.events.at(-1)?.state.status, 'inactive')
})

test('Axios timeout retries after cooldown only, and disposal cancels the retry timer', async () => {
  const h = setup()
  h.session.update(input)
  h.advance()
  h.requests[0].fail(new AxiosError('fixture timeout', AxiosError.ECONNABORTED))
  await Promise.resolve()
  assert.equal(h.entries.size, 1)
  h.advance(14_999)
  assert.equal(h.requests.length, 1)
  h.session.dispose()
  assert.equal(h.entries.size, 0)
  h.advance(60_000)
  assert.equal(h.requests.length, 1)
})

test('a late response after session replacement is ignored independently of Axios abort timing', async () => {
  const h = setup()
  h.session.update(input)
  h.advance()
  h.replaceSession()
  const emitted = h.events.length
  h.requests[0].fail(new AxiosError('late synthetic failure'))
  await Promise.resolve()
  assert.equal(h.events.length, emitted)
  assert.equal(h.entries.size, 0)
  h.session.dispose()
})
