import { useEffect, useRef, useState } from 'react'
import type { Map, Marker } from '@vietmap/vietmap-gl-js'
import { AimOutlined, ExpandOutlined } from '@ant-design/icons'
import type { OrderDriver } from '../../../api/orders'
import { createTrackingCamera } from './trackingCamera'
import { createTrackingRouteLayer } from './trackingRouteLayer'
import type { TrackingPoint } from './trackingSnapshot'

// The country-wide camera is used when the snapshot has no pins.
const VIETNAM_BOUNDS: [[number, number], [number, number]] = [[102, 8], [110, 24]]

interface Renderer {
  map: Map
  createMarker: (color: string, label: string, vehicle?: boolean) => Marker
  deliveryMarker?: Marker
  driverMarker?: Marker
  camera: ReturnType<typeof createTrackingCamera>
  route: ReturnType<typeof createTrackingRouteLayer>
}

interface Props {
  tilemapKey: string
  deliveryPoint: TrackingPoint | null
  driverPoint: TrackingPoint | null
  vehicleType?: OrderDriver['vehicleType']
  oldPosition: boolean
  routeGeometry: Parameters<ReturnType<typeof createTrackingRouteLayer>['update']>[0]
}

function updateMarker(
  renderer: Renderer,
  marker: Marker | undefined,
  point: TrackingPoint | null,
  color: string,
  label: string,
  vehicle = false,
) {
  if (!point) {
    marker?.remove()
    return undefined
  }

  const position: [number, number] = [point.lng, point.lat]
  if (!marker) return renderer.createMarker(color, label, vehicle).setLngLat(position).addTo(renderer.map)

  const current = marker.getLngLat()
  if (current.lat !== point.lat || current.lng !== point.lng) marker.setLngLat(position)
  return marker
}

const vehiclePaths: Record<OrderDriver['vehicleType'], string> = {
  BICYCLE: 'M7 17a3 3 0 1 1-6 0a3 3 0 0 1 6 0m16 0a3 3 0 1 1-6 0a3 3 0 0 1 6 0M4 17l5-9 5 9H4m10 0l4-11h3M7 8h5',
  MOTORBIKE: 'M7 17a3 3 0 1 1-6 0a3 3 0 0 1 6 0m16 0a3 3 0 1 1-6 0a3 3 0 0 1 6 0M7 17h6l3-6h-5l-2 3H6m14 3L17 6h-3M6 10h5',
  CAR: 'M3 16v-3l2-5h14l2 5v5M3 16h18M3 13h18M5 18v1m14-1v1M6 14h1m10 0h1',
}

function updateVehicleMarker(marker: Marker | undefined, vehicleType: Props['vehicleType'], oldPosition: boolean) {
  if (!marker) return
  const element = marker.getElement()
  element.dataset.stale = String(oldPosition)
  const path = (vehicleType && vehiclePaths[vehicleType]) || 'M12 5a5 5 0 1 0 0 10 5 5 0 0 0 0-10m0 10v5'
  if (element.dataset.shape === path) return
  element.dataset.shape = path
  const ns = 'http://www.w3.org/2000/svg'
  const icon = document.createElementNS(ns, 'svg')
  icon.setAttribute('viewBox', '0 0 24 24')
  icon.setAttribute('width', '21')
  icon.setAttribute('height', '21')
  icon.setAttribute('aria-hidden', 'true')
  const outline = document.createElementNS(ns, 'path')
  outline.setAttribute('d', path)
  outline.setAttribute('fill', 'none')
  outline.setAttribute('stroke', 'currentColor')
  outline.setAttribute('stroke-width', '1.65')
  outline.setAttribute('stroke-linejoin', 'round')
  outline.setAttribute('stroke-linecap', 'round')
  icon.append(outline)
  element.replaceChildren(icon)
}

export default function VietmapStreetMap({ tilemapKey, deliveryPoint, driverPoint, vehicleType, oldPosition, routeGeometry }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const renderer = useRef<Renderer | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [following, setFollowing] = useState(false)
  const isFollowing = following && driverPoint !== null

  useEffect(() => {
    const element = container.current
    if (!element) return
    const mapContainer = element

    let disposed = false
    let instance: Renderer | undefined
    let resizeObserver: ResizeObserver | undefined

    function showError() {
      window.clearTimeout(loadTimeout)
      if (!disposed) setStatus('error')
    }

    const loadTimeout = window.setTimeout(showError, 15_000)

    async function initializeMap() {
      try {
        const { default: sdk } = await import('./vietmapSdk')
        if (disposed) return

        const style = new URL('https://maps.vietmap.vn/maps/styles/tm/style.json')
        style.searchParams.set('apikey', tilemapKey)
        const map = new sdk.Map({
          container: mapContainer,
          style: style.href,
          bounds: VIETNAM_BOUNDS,
          fitBoundsOptions: { padding: 16, duration: 0 },
          maplibreLogo: true,
          dragRotate: false,
          pitchWithRotate: false,
        })
        const camera = createTrackingCamera(map, setFollowing)
        instance = {
          map,
          camera,
          route: createTrackingRouteLayer(map),
          createMarker(color, label, vehicle) {
            const element = vehicle ? document.createElement('div') : undefined
            if (element) {
              element.className = 'grid size-9 place-items-center rounded-full border-2 border-white bg-blue-600 text-white ring-2 ring-blue-300/60 shadow-md data-[stale=true]:grayscale data-[stale=true]:opacity-75'
              element.setAttribute('role', 'img')
            }
            const marker = new sdk.Marker({ color, element })
            marker.getElement().setAttribute('aria-label', label)
            return marker
          },
        }
        renderer.current = instance

        map.on('error', showError)
        map.on('webglcontextlost', showError)
        map.on('movestart', camera.gesture)
        // A user can take over while an animation is already moving the camera.
        map.on('move', camera.gesture)
        // VIETMAP 6.0.1 can omit originalEvent from the zoom animation after a single wheel tick.
        map.on('wheel', camera.gesture)
        map.once('load', () => {
          window.clearTimeout(loadTimeout)
          if (!disposed) setStatus('ready')
        })
        map.addControl(new sdk.NavigationControl({ showCompass: false }), 'top-right')

        resizeObserver = new ResizeObserver(() => map.resize())
        resizeObserver.observe(mapContainer)
      } catch {
        showError()
      }
    }

    void initializeMap()

    return () => {
      disposed = true
      window.clearTimeout(loadTimeout)
      resizeObserver?.disconnect()
      renderer.current = null
      instance?.deliveryMarker?.remove()
      instance?.driverMarker?.remove()
      instance?.route.dispose()
      instance?.map.remove()
    }
  }, [tilemapKey])

  useEffect(() => {
    const current = renderer.current
    if (!current || status !== 'ready') return

    current.deliveryMarker = updateMarker(current, current.deliveryMarker, deliveryPoint, '#ea580c', 'Điểm giao hàng')
    current.driverMarker = updateMarker(current, current.driverMarker, driverPoint, '#2563eb', 'Vị trí tài xế gần nhất', true)
    current.camera.update(deliveryPoint, driverPoint)
  }, [deliveryPoint, driverPoint, status])

  useEffect(() => {
    if (status === 'ready') updateVehicleMarker(renderer.current?.driverMarker, vehicleType, oldPosition)
  }, [driverPoint, oldPosition, status, vehicleType])

  useEffect(() => {
    if (status === 'ready') renderer.current?.route.update(routeGeometry)
  }, [routeGeometry, status])

  return (
    <div className="space-y-2">
      <div className="relative h-64 overflow-hidden rounded-xl border border-slate-200 bg-slate-50 sm:h-[25rem] lg:h-[31rem]">
        <div ref={container} className="h-full w-full" aria-label="Bản đồ VIETMAP Street" />
        {status !== 'ready' && (
          <div role="status" className="absolute inset-0 z-1 grid place-items-center bg-slate-50 px-5 text-center text-sm text-slate-700">
            {status === 'loading' ? 'Đang tải bản đồ VIETMAP…' : 'Bản đồ tạm thời không khả dụng.'}
          </div>
        )}
      </div>
      <div className="grid grid-cols-[minmax(0,1.25fr)_minmax(0,1fr)] gap-2">
        <button
          type="button"
          aria-pressed={isFollowing}
          disabled={status !== 'ready' || !driverPoint}
          onClick={() => renderer.current?.camera.toggleFollow(driverPoint)}
          className="flex min-h-11 flex-wrap items-center justify-center gap-1.5 rounded-lg border border-slate-200 px-2 py-1.5 text-xs font-medium text-slate-800 hover:bg-slate-50 aria-pressed:border-orange-600 aria-pressed:bg-orange-50 aria-pressed:text-orange-800 disabled:opacity-50 sm:text-sm"
        >
          <AimOutlined aria-hidden="true" />
          Bám theo tài xế <span aria-hidden="true">· {isFollowing ? 'Bật' : 'Tắt'}</span>
        </button>
        <button
          type="button"
          disabled={status !== 'ready' || (!deliveryPoint && !driverPoint)}
          onClick={() => renderer.current?.camera.overview(deliveryPoint, driverPoint)}
          className="flex min-h-11 items-center justify-center gap-1.5 rounded-lg border border-slate-200 px-2 text-xs font-medium text-slate-800 hover:bg-slate-50 disabled:opacity-50 sm:text-sm"
        >
          <ExpandOutlined aria-hidden="true" />
          Toàn cảnh
        </button>
      </div>
    </div>
  )
}
