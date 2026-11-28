import type { GeoJSONSource, Map } from '@vietmap/vietmap-gl-js'
import type { TrackingRouteState } from './trackingRoutePlanner.ts'

export const ROUTE_LAYER_ID = 'order-tracking-remaining-route'
const ROUTE_CASING_ID = 'order-tracking-route-casing'
const ROUTE_SOURCE_ID = 'order-tracking-route'

type RouteGeometry = Extract<TrackingRouteState, { status: 'ready' }>['geometry']

export function createTrackingRouteLayer(map: Map) {
  let geometry: RouteGeometry | null = null
  let applied: RouteGeometry | null = null

  function remove() {
    if (map.getLayer(ROUTE_LAYER_ID)) map.removeLayer(ROUTE_LAYER_ID)
    if (map.getLayer(ROUTE_CASING_ID)) map.removeLayer(ROUTE_CASING_ID)
    if (map.getSource(ROUTE_SOURCE_ID)) map.removeSource(ROUTE_SOURCE_ID)
    applied = null
  }

  function sync(styleLoaded = false) {
    if (!geometry) {
      remove()
      return
    }

    const source = map.getSource(ROUTE_SOURCE_ID) as GeoJSONSource | undefined
    const layer = map.getLayer(ROUTE_LAYER_ID)
    const casing = map.getLayer(ROUTE_CASING_ID)
    if ((!source || !layer || !casing) && !styleLoaded && !map.isStyleLoaded()) return

    if (!source || applied !== geometry || styleLoaded) {
      const data = {
        type: 'Feature' as const,
        properties: {},
        geometry: {
          type: 'LineString' as const,
          coordinates: geometry.coordinates.map(([lng, lat]) => [lng, lat]),
        },
      }
      if (source) source.setData(data)
      else map.addSource(ROUTE_SOURCE_ID, { type: 'geojson', data })
      applied = geometry
    }
    if (!casing) map.addLayer({
      id: ROUTE_CASING_ID, type: 'line', source: ROUTE_SOURCE_ID,
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.95 },
    }, layer ? ROUTE_LAYER_ID : undefined)
    if (!layer) map.addLayer({
      id: ROUTE_LAYER_ID, type: 'line', source: ROUTE_SOURCE_ID,
      layout: { 'line-join': 'round', 'line-cap': 'round' },
      paint: { 'line-color': '#2563eb', 'line-width': 5 },
    })
  }

  const styleLoaded = () => sync(true)
  map.on('style.load', styleLoaded)
  map.on('load', styleLoaded)
  return {
    update(next: RouteGeometry | null) {
      geometry = next
      sync()
    },
    dispose() {
      map.off('style.load', styleLoaded)
      map.off('load', styleLoaded)
      remove()
    },
  }
}
