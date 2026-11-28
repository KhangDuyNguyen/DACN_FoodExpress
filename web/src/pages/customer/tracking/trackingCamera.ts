import type { Map } from '@vietmap/vietmap-gl-js'
import type { TrackingPoint } from './trackingSnapshot.ts'

const OVERVIEW_PADDING = 55
const SINGLE_POINT_ZOOM = 15
const OVERVIEW_MAX_ZOOM = 16
const CAMERA_DURATION = 350

function position(point: TrackingPoint): [number, number] {
  return [point.lng, point.lat]
}

function samePosition(first: TrackingPoint | null, second: TrackingPoint) {
  return first?.lat === second.lat && first.lng === second.lng
}

export function createTrackingCamera(
  map: Pick<Map, 'easeTo' | 'fitBounds' | 'stop'>,
  onFollowChange: (following: boolean) => void,
) {
  let following = false
  let lastFollowPoint: TrackingPoint | null = null
  let autoFrame = true
  let framedPoints = 0

  function stopFollowing(stopAnimation: boolean) {
    if (!following) return
    following = false
    lastFollowPoint = null
    if (stopAnimation) map.stop()
    onFollowChange(false)
  }

  function follow(point: TrackingPoint) {
    if (samePosition(lastFollowPoint, point)) return
    lastFollowPoint = point
    // An equal easeId replaces the previous follow animation. Omitting zoom preserves the current zoom.
    map.easeTo({ center: position(point), duration: CAMERA_DURATION, easeId: 'tracking-driver-follow' })
  }

  function frame(delivery: TrackingPoint | null, driver: TrackingPoint | null, duration: number) {
    const first = delivery ?? driver
    if (!first) return
    if (delivery && driver && !samePosition(delivery, driver)) {
      map.fitBounds([position(delivery), position(driver)], {
        padding: OVERVIEW_PADDING, maxZoom: OVERVIEW_MAX_ZOOM, duration,
      })
    } else {
      map.easeTo({ center: position(first), zoom: SINGLE_POINT_ZOOM, duration })
    }
  }

  return {
    update(delivery: TrackingPoint | null, driver: TrackingPoint | null) {
      if (following) {
        if (driver) follow(driver)
        else stopFollowing(true)
        return
      }
      const pointCount = Number(delivery !== null) + Number(driver !== null)
      if (!autoFrame || pointCount <= framedPoints) return
      framedPoints = pointCount
      frame(delivery, driver, 0)
    },
    toggleFollow(driver: TrackingPoint | null) {
      if (!driver) return
      autoFrame = false
      if (following) {
        stopFollowing(true)
        return
      }
      following = true
      onFollowChange(true)
      follow(driver)
    },
    gesture(event: { originalEvent?: Event }) {
      if (!event.originalEvent) return
      autoFrame = false
      // The SDK owns the active gesture and its inertia.
      stopFollowing(false)
    },
    overview(delivery: TrackingPoint | null, driver: TrackingPoint | null) {
      if (!delivery && !driver) return
      autoFrame = false
      stopFollowing(false)
      frame(delivery, driver, CAMERA_DURATION)
    },
  }
}
