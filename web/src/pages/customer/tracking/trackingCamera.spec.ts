import assert from 'node:assert/strict'
import { test } from 'node:test'
import type { Map } from '@vietmap/vietmap-gl-js'
import { createTrackingCamera } from './trackingCamera.ts'

const delivery = { lat: 10.762, lng: 106.66 }
const driver = { lat: 10.768, lng: 106.667 }
const next = { lat: 10.775, lng: 106.673 }

function harness() {
  const moves: Array<Parameters<Map['easeTo']>[0]> = []
  const bounds: Array<Parameters<Map['fitBounds']>> = []
  const following: boolean[] = []
  let stops = 0
  const map = {
    easeTo(options: Parameters<Map['easeTo']>[0]) { moves.push(options) },
    fitBounds(...options: Parameters<Map['fitBounds']>) { bounds.push(options) },
    stop() { stops += 1 },
  } as unknown as Map
  const camera = createTrackingCamera(map, (active) => following.push(active))
  return { camera, moves, bounds, following, get stops() { return stops } }
}

test('starts off, frames initial points as they appear and leaves later GPS alone', () => {
  const { camera, moves, bounds, following } = harness()
  camera.update(null, null)
  camera.update(delivery, null)
  assert.deepEqual(moves, [{ center: [delivery.lng, delivery.lat], zoom: 15, duration: 0 }])
  camera.update(delivery, driver)
  assert.deepEqual(bounds, [[[[delivery.lng, delivery.lat], [driver.lng, driver.lat]], {
    padding: 55, maxZoom: 16, duration: 0,
  }]])
  camera.update(delivery, next)
  assert.equal(moves.length, 1)
  assert.equal(bounds.length, 1)
  assert.deepEqual(following, [])
})

test('follow centers successive fixes, retains zoom and ignores duplicate positions', () => {
  const { camera, moves, following } = harness()
  camera.toggleFollow(driver)
  camera.update(delivery, { ...driver })
  camera.update(delivery, next)
  assert.deepEqual(following, [true])
  assert.equal(moves.length, 2)
  assert.deepEqual(moves[0].center, [driver.lng, driver.lat])
  assert.deepEqual(moves[1].center, [next.lng, next.lat])
  assert.equal(moves[0].zoom, undefined)
  assert.ok(moves[0].duration && moves[0].duration > 0)
  assert.equal(moves[0].easeId, moves[1].easeId)
})

test('manual off interrupts follow and subsequent fixes leave the camera alone', () => {
  const result = harness()
  result.camera.toggleFollow(driver)
  result.camera.toggleFollow(driver)
  result.camera.update(delivery, next)
  assert.deepEqual(result.following, [true, false])
  assert.equal(result.stops, 1)
  assert.equal(result.moves.length, 1)
  assert.equal(result.bounds.length, 0)
})

test('SDK events with a DOM gesture disable follow without stopping the user gesture', () => {
  const result = harness()
  result.camera.toggleFollow(driver)
  result.camera.gesture({})
  result.camera.update(delivery, next)
  assert.deepEqual(result.following, [true])
  result.camera.gesture({ originalEvent: new Event('wheel') })
  result.camera.update(delivery, driver)
  assert.deepEqual(result.following, [true, false])
  assert.equal(result.stops, 0)
  assert.equal(result.moves.length, 2)
})

test('panning before the first GPS fix prevents later automatic framing', () => {
  const { camera, moves, bounds } = harness()
  camera.update(delivery, null)
  camera.gesture({ originalEvent: new Event('touchmove') })
  camera.update(delivery, driver)
  assert.equal(moves.length, 1)
  assert.equal(bounds.length, 0)
})

test('overview switches off follow and uses bounded zoom for close or coincident points', () => {
  const { camera, following, moves, bounds } = harness()
  camera.toggleFollow(driver)
  camera.overview(delivery, { ...delivery, lat: delivery.lat + 0.00001 })
  assert.deepEqual(following, [true, false])
  assert.equal(bounds[0][1]?.maxZoom, 16)
  assert.equal(bounds[0][1]?.padding, 55)
  camera.overview(delivery, { ...delivery })
  assert.deepEqual(moves[1].center, [delivery.lng, delivery.lat])
  assert.equal(moves[1].zoom, 15)
})

test('overview handles a single point and no points, and follow resets when GPS is removed', () => {
  const result = harness()
  result.camera.toggleFollow(null)
  result.camera.overview(null, null)
  assert.deepEqual(result.moves, [])
  result.camera.overview(null, driver)
  assert.deepEqual(result.moves[0].center, [driver.lng, driver.lat])
  result.camera.toggleFollow(driver)
  result.camera.update(delivery, null)
  assert.deepEqual(result.following, [true, false])
  assert.equal(result.stops, 1)
  const newSession = harness()
  newSession.camera.update(delivery, next)
  assert.deepEqual(newSession.following, [])
  assert.equal(newSession.bounds.length, 1)
})
