import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createTrackingSession, initialTrackingState, type TrackingSocketEvents } from './trackingRealtimeSession.ts'
import { readTrackingSnapshot, type TrackingSnapshot } from './trackingSnapshot.ts'

const first = { lat: 10.7, lng: 106.6, recordedAt: '2026-11-25T10:00:00' }
const sameSecond = { ...first, lat: 10.71 }
const later = { ...first, recordedAt: '2026-11-25T10:00:01' }
const deliveryPoint = { lat: 10.8, lng: 106.7 }
const snapshot = (location: TrackingSnapshot['latestLocation'], status = 'DELIVERING'): TrackingSnapshot => ({
  status, deliveryPoint, latestLocation: location,
})

function harness(orderId = 11) {
  let state = initialTrackingState
  let current = true
  let closes = 0
  let timer: (() => void) | undefined
  const requests: Array<{ signal: AbortSignal; resolve: (value: unknown) => void; reject: (error: unknown) => void }> = []
  const sockets: TrackingSocketEvents[] = []
  const session = createTrackingSession({
    load: async (signal) => {
      const payload = await new Promise((resolve, reject) => requests.push({ signal, resolve, reject }))
      return readTrackingSnapshot(payload, orderId)
    },
    connect: (events) => {
      sockets.push(events)
      return () => { closes += 1 }
    },
    accessError: (error) => error === 401 || error === 403 || error === 404 ? error : null,
    isCurrent: () => current,
    emit: (update) => { state = update },
    scheduleStale: (mark) => {
      timer = mark
      return () => { timer = undefined }
    },
  })

  return {
    ...session, requests, sockets,
    get state() { return state },
    get closes() { return closes },
    get timer() { return timer },
    changeSession() { current = false },
    async resolve(index: number, value: TrackingSnapshot, id = orderId) {
      requests[index].resolve({ ...value, orderId: id })
      await Promise.resolve()
      await Promise.resolve()
    },
  }
}

function live(session = harness()) {
  session.updateStatus('DELIVERING')
  session.sockets[0].connected()
  return session
}

test('initial REST fix is visible before STOMP connects', async () => {
  const session = harness()
  session.updateStatus('DELIVERING')
  await session.resolve(0, snapshot(first))
  assert.deepEqual(session.state.location, first)
  session.sockets[0].connected()
  assert.equal(session.requests.length, 2)
  assert.equal(session.state.connection, 'live')
})

test('a same-second frame between request and response wins over REST', async () => {
  const session = live()
  session.sockets[0].location(sameSecond)
  await session.resolve(1, snapshot(first))
  assert.deepEqual(session.state.location, sameSecond)
  assert.deepEqual(session.state.deliveryPoint, deliveryPoint)
})

test('manual REST with an older fix retains newer STOMP data', async () => {
  const session = live()
  session.sockets[0].location(later)
  const refresh = session.refresh()
  assert.equal(session.requests[1].signal.aborted, true)
  await session.resolve(2, snapshot(first))
  await refresh
  assert.deepEqual(session.state.location, later)
})

test('same-second frames with different coordinates follow receive order', () => {
  const session = live()
  session.sockets[0].location(first)
  session.sockets[0].location(sameSecond)
  assert.deepEqual(session.state.location, sameSecond)
})

test('an identical frame never resets the freshness timer', () => {
  const session = live()
  session.sockets[0].location(first)
  const original = session.timer
  original?.()
  session.sockets[0].location(first)
  assert.equal(session.timer, original)
  assert.equal(session.state.stale, true)
  session.sockets[0].location(sameSecond)
  assert.equal(session.state.stale, false)
})

test('out-of-order and malformed frames are ignored', () => {
  const session = live()
  const socket = session.sockets[0]
  socket.location(later)
  for (const message of [first, null, 'bad JSON', { ...later, lng: 181 }, { ...later, recordedAt: 'today' }]) {
    socket.location(message)
  }
  assert.deepEqual(session.state.location, later)
})

test('a null or canceled snapshot cannot clear a valid fix', async () => {
  const session = live()
  session.sockets[0].location(first)
  await session.resolve(1, snapshot(null))
  await session.resolve(0, snapshot(later))
  assert.deepEqual(session.state.location, first)
})

test('offline and transient REST errors preserve the last fix, independently of staleness', async () => {
  const session = live()
  session.sockets[0].location(first)
  session.sockets[0].disconnected()
  session.sockets[0].location(later)
  const refresh = session.refresh()
  session.requests[2].reject(new Error('Network error'))
  await refresh
  assert.equal(session.state.connection, 'offline')
  assert.deepEqual(session.state.location, first)
  session.timer?.()
  assert.equal(session.state.stale, true)
})

test('reconnect resyncs REST, with one live transport for repeated order status', async () => {
  const session = live()
  const socket = session.sockets[0]
  socket.location(first)
  await session.resolve(1, snapshot(first))
  session.updateStatus('DELIVERING')
  socket.disconnected()
  socket.connecting(true)
  assert.equal(session.state.connection, 'reconnecting')
  socket.connected()
  assert.equal(session.requests.length, 3)
  await session.resolve(2, snapshot(later))
  assert.equal(session.sockets.length, 1)
  assert.equal(session.state.connection, 'live')
  assert.deepEqual(session.state.location, later)
})

test('order change ignores old REST and socket callbacks and rejects a cross-order snapshot', async () => {
  const old = live(harness(11))
  old.dispose()
  const next = live(harness(12))
  old.sockets[0].location(first)
  await old.resolve(1, snapshot(later))
  await next.resolve(1, snapshot(first), 11)
  assert.equal(old.state.location, null)
  assert.equal(next.state.location, null)
  assert.ok(next.state.error)
})

test('logout or account change blocks callbacks before the old session is disposed', async () => {
  const old = live()
  const next = live()
  next.sockets[0].location(later)
  old.changeSession()
  old.requests[1].reject(401)
  old.sockets[0].location(first)
  await Promise.resolve()
  await Promise.resolve()
  assert.equal(old.state.denied, null)
  assert.equal(old.state.location, null)
  assert.deepEqual(next.state.location, later)
})

test('unmount aborts requests and clears transport, stale timer and queued callbacks', async () => {
  const session = live()
  session.sockets[0].location(first)
  const queuedTimer = session.timer
  session.dispose()
  const finalState = session.state
  session.sockets[0].connected()
  queuedTimer?.()
  await session.resolve(1, snapshot(later))
  assert.equal(session.requests[1].signal.aborted, true)
  assert.equal(session.closes, 1)
  assert.equal(session.timer, undefined)
  assert.equal(session.state, finalState)
})

for (const status of ['DELIVERED', 'CANCELLED']) {
  test(`${status} clears GPS and prevents pending REST and later detail polling from restarting STOMP`, async () => {
    const session = live()
    session.sockets[0].location(first)
    await session.resolve(1, snapshot(first))
    void session.refresh()
    session.updateStatus(status)
    await session.resolve(2, snapshot(later))
    session.updateStatus('DELIVERING')
    session.sockets[0].location(later)
    assert.equal(session.closes, 1)
    assert.equal(session.sockets.length, 1)
    assert.equal(session.state.finished, true)
    assert.equal(session.state.connection, 'idle')
    assert.equal(session.state.location, null)
    assert.equal(session.timer, undefined)
    assert.deepEqual(session.state.deliveryPoint, deliveryPoint)
  })
}

test('a terminal snapshot stops tracking even when the detail poll is behind', async () => {
  const session = live()
  session.sockets[0].location(first)
  await session.resolve(1, snapshot(first, 'DELIVERED'))
  assert.equal(session.state.finished, true)
  assert.equal(session.state.location, null)
  assert.equal(session.closes, 1)
})

test('a current access denial stops tracking and clears the map data', async () => {
  for (const status of [401, 403, 404]) {
    const session = live()
    await session.resolve(1, snapshot(first))
    const refresh = session.refresh()
    session.requests[2].reject(status)
    await refresh
    assert.equal(session.state.denied, status)
    assert.equal(session.state.location, null)
    assert.equal(session.state.deliveryPoint, null)
    assert.equal(session.closes, 1)
  }
})
