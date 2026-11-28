export interface TrackingPoint {
  lat: number
  lng: number
}

export interface TrackingLocation extends TrackingPoint {
  recordedAt: string
}

export interface TrackingSnapshot {
  status: string
  deliveryPoint: TrackingPoint | null
  latestLocation: TrackingLocation | null
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function readPoint(value: unknown): TrackingPoint | null {
  if (!isRecord(value)) return null

  const { lat, lng } = value
  if (typeof lat !== 'number' || !Number.isFinite(lat) || Math.abs(lat) > 90 ||
      typeof lng !== 'number' || !Number.isFinite(lng) || Math.abs(lng) > 180) {
    return null
  }

  return { lat, lng }
}

export function readTrackingLocation(value: unknown): TrackingLocation | null {
  const point = readPoint(value)
  if (!point || !isRecord(value)) return null

  const { recordedAt } = value
  if (typeof recordedAt !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(recordedAt)) return null

  // Validate the calendar fields without interpreting the server's local time in the browser's timezone.
  const calendar = new Date(`${recordedAt}Z`)
  if (!Number.isFinite(calendar.getTime()) || calendar.toISOString().slice(0, 19) !== recordedAt) return null
  return { ...point, recordedAt }
}

export function readTrackingSnapshot(value: unknown, expectedOrderId: number): TrackingSnapshot {
  if (!isRecord(value) || value.orderId !== expectedOrderId || typeof value.status !== 'string') {
    throw new Error('Dữ liệu theo dõi đơn hàng không hợp lệ.')
  }

  const { deliveryPoint, latestLocation } = value
  return {
    status: value.status,
    deliveryPoint: readPoint(deliveryPoint),
    latestLocation: readTrackingLocation(latestLocation),
  }
}
