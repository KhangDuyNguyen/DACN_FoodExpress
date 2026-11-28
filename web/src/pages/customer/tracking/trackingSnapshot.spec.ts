import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readTrackingLocation, readTrackingSnapshot } from './trackingSnapshot.ts'

const order = { orderId: 17, orderCode: 'COD-17', status: 'DELIVERING' }

test('keeps the delivery point when there is no GPS fix', () => {
  const deliveryPoint = { lat: 10.762, lng: 106.66 }
  assert.deepEqual(readTrackingSnapshot({ ...order, deliveryPoint, latestLocation: null }, 17), {
    status: 'DELIVERING',
    deliveryPoint,
    latestLocation: null,
  })
})

test('reads the latest GPS separately when the delivery point is missing', () => {
  const latestLocation = { lat: 10.765, lng: 106.665, recordedAt: '2026-11-24T16:30:00' }
  assert.deepEqual(readTrackingSnapshot({ ...order, latestLocation }, 17), {
    status: 'DELIVERING',
    deliveryPoint: null,
    latestLocation,
  })
})

test('filters non-numeric, non-finite and out-of-range coordinates', () => {
  for (const point of [
    { lat: 91, lng: 106 },
    { lat: 10, lng: -181 },
    { lat: NaN, lng: 106 },
    { lat: 10, lng: Infinity },
    { lat: '10', lng: 106 },
    { lat: 10 },
  ]) {
    const snapshot = readTrackingSnapshot({ ...order, deliveryPoint: point, latestLocation: point }, 17)
    assert.equal(snapshot.deliveryPoint, null)
    assert.equal(snapshot.latestLocation, null)
  }
})

test('treats explicit null points as absent and retains valid zero coordinates', () => {
  const snapshot = readTrackingSnapshot({ ...order, deliveryPoint: null, latestLocation: null }, 17)
  assert.equal(snapshot.deliveryPoint, null)
  assert.equal(snapshot.latestLocation, null)
  const deliveryPoint = { lat: 0, lng: 0 }
  assert.deepEqual(readTrackingSnapshot({ ...order, deliveryPoint }, 17).deliveryPoint, deliveryPoint)
})

test('rejects malformed and cross-order snapshots', () => {
  for (const payload of [null, [], { ...order, orderId: 18 }, { ...order, orderId: '17' }, { orderId: 17 }]) {
    assert.throws(() => readTrackingSnapshot(payload, 17), /Dữ liệu theo dõi đơn hàng không hợp lệ/)
  }
})

test('validates an ISO local timestamp without offset at second precision', () => {
  const point = { lat: 10, lng: 106 }
  for (const recordedAt of [null, '', '2026-11-24T16:30', '2026-11-24T16:30:00Z',
    '2026-11-24T16:30:00+07:00', '2026-11-24T16:30:00.123', '2026-02-29T00:00:00',
    '2026-11-24T24:30:00', '2026-11-24T16:60:00']) {
    assert.equal(readTrackingLocation({ ...point, recordedAt }), null)
  }
  const location = { ...point, recordedAt: '2028-02-29T00:00:00' }
  assert.deepEqual(readTrackingLocation(location), location)
})
