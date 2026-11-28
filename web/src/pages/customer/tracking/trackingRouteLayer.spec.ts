import assert from 'node:assert/strict'
import { test } from 'node:test'
import type { Map as Vietmap } from '@vietmap/vietmap-gl-js'
import { createTrackingRouteLayer, ROUTE_LAYER_ID } from './trackingRouteLayer.ts'

const original = { type: 'LineString' as const, coordinates: [[106.70, 10.78], [106.71, 10.79]] as const }
const trimmed = { type: 'LineString' as const, coordinates: [[106.705, 10.785], [106.71, 10.79]] as const }

function fixture() {
  let ready = false
  const handlers = new Map<string, () => void>()
  const calls: string[] = []
  let source: unknown
  const layers = new Map<string, unknown>()
  const mock = {
    on: (name: string, fire: () => void) => handlers.set(name, fire),
    off: (name: string) => handlers.delete(name),
    isStyleLoaded: () => ready,
    getSource: () => source,
    getLayer: (id: string) => layers.get(id),
    addSource(_id: string, options: unknown) {
      calls.push('add-source')
      assert.ok(options)
      source = { setData: (data: { geometry: typeof trimmed }) => {
        calls.push('set-data')
        assert.deepEqual(data.geometry.coordinates, trimmed.coordinates)
      } }
    },
    addLayer(config: { id: string; paint: { 'line-color': string } }) {
      const line = config.id === ROUTE_LAYER_ID
      calls.push(line ? 'add-line' : 'add-casing')
      assert.equal(config.paint['line-color'], line ? '#2563eb' : '#ffffff')
      layers.set(config.id, config)
    },
    removeLayer(id: string) {
      calls.push(id === ROUTE_LAYER_ID ? 'remove-line' : 'remove-casing')
      layers.delete(id)
    },
    removeSource() { calls.push('remove-source'); source = undefined },
  }
  return {
    map: mock as unknown as Vietmap, calls, handlers,
    reload() {
      ready = true
      source = undefined
      layers.clear()
      handlers.get('style.load')?.()
    },
  }
}

test('defers geometry until style load, updates the existing source and rebuilds after style reload', () => {
  const h = fixture()
  const layer = createTrackingRouteLayer(h.map)
  layer.update(original)
  assert.deepEqual(h.calls, [])
  h.reload()
  layer.update(trimmed)
  layer.update(trimmed)
  assert.deepEqual(h.calls, ['add-source', 'add-casing', 'add-line', 'set-data'])
  h.reload()
  assert.deepEqual(h.calls.slice(4), ['add-source', 'add-casing', 'add-line'])
})

test('hides the route in layer-source order and removes listeners on disposal', () => {
  const h = fixture()
  const layer = createTrackingRouteLayer(h.map)
  h.reload()
  layer.update(original)
  layer.update(null)
  assert.deepEqual(h.calls, ['add-source', 'add-casing', 'add-line', 'remove-line', 'remove-casing', 'remove-source'])
  h.reload()
  layer.dispose()
  assert.equal(h.handlers.size, 0)
  assert.equal(h.calls.length, 6)
})
