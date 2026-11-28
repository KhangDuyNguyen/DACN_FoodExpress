import { InfoCircleOutlined, SyncOutlined } from '@ant-design/icons'
import type { TrackingRouteView } from './trackingRouteSession'

function formatDistance(meters: number) {
  return meters < 1000 ? `${Math.round(meters)} m`
    : `${(meters / 1000).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} km`
}

function formatEta(ms: number) {
  const minutes = Math.round(ms / 60_000)
  if (minutes < 1) return 'Dưới 1 phút'
  if (minutes < 60) return `${minutes} phút`
  const remainder = minutes % 60
  return `${Math.floor(minutes / 60)} giờ${remainder ? ` ${remainder} phút` : ''}`
}

export function TrackingRouteNotice({ state, hadRoute }: TrackingRouteView) {
  if (state.status === 'updating') {
    return (
      <p role="status" className="flex items-center gap-2 rounded-lg bg-blue-50 px-3 py-2 text-sm font-medium text-blue-800">
        <SyncOutlined aria-hidden="true" className="motion-safe:animate-spin" />
        {hadRoute ? 'Đang cập nhật tuyến đường…' : 'Đang tính tuyến đường…'}
      </p>
    )
  }

  const message = state.status === 'arrived' ? 'Tài xế đã đến gần điểm giao.'
    : state.status === 'awaiting-fix' ? 'Đang chờ vị trí mới để xác nhận tuyến.'
      : state.status === 'unavailable' ? unavailableText(state.reason) : null
  if (!message) return null
  return (
    <p role="status" className="flex items-start gap-2 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-700">
      <InfoCircleOutlined aria-hidden="true" className="mt-0.5 shrink-0" />
      {message}
    </p>
  )
}

function unavailableText(reason: string): string {
  switch (reason) {
    case 'ROUTE_PROFILE_UNSUPPORTED': return 'Phương tiện này chưa hỗ trợ tính tuyến.'
    case 'ROUTE_RATE_LIMITED':
    case 'ROUTE_CLIENT_BUDGET_EXHAUSTED': return 'Đã hết giới hạn tính tuyến.'
    case 'ROUTE_NOT_FOUND': return 'Không tìm thấy tuyến đường.'
    default: return 'Dịch vụ chỉ đường tạm thời không khả dụng.'
  }
}

export default function TrackingRouteSummary({ state }: TrackingRouteView) {
  const ready = state.status === 'ready'
  return (
    <dl aria-label="Tuyến đường giao hàng" className="grid grid-cols-2 divide-x divide-slate-200">
      <div className="min-w-0 pr-2 sm:pr-5">
        <dt className="text-xs text-slate-600 sm:text-sm">Thời gian đến dự kiến</dt>
        <dd className="mt-1 text-xl leading-tight font-bold break-words text-orange-700 sm:text-3xl">
          {ready ? formatEta(state.estimatedDurationMs) : '—'}
        </dd>
      </div>
      <div className="min-w-0 pl-3 sm:pl-6">
        <dt className="text-xs text-slate-600 sm:text-sm">Khoảng cách còn lại</dt>
        <dd className="mt-1 text-xl leading-tight font-bold break-words text-slate-950 sm:text-3xl">
          {ready ? formatDistance(state.remainingDistanceMeters) : '—'}
        </dd>
      </div>
    </dl>
  )
}
