import { CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { isAxiosError } from 'axios'
import type { OrderDetail } from '../../../api/orders'
import type { createTrackingRequests } from './trackingRequests'
import { isTrackingFinished, type TrackingState } from './trackingRealtimeSession'
import { useOrderTrackingRealtime } from './useOrderTrackingRealtime'
import { useOrderTrackingRoute } from './useOrderTrackingRoute'
import TrackingRouteSummary, { TrackingRouteNotice } from './TrackingRouteSummary'
import VietmapStreetMap from './VietmapStreetMap'

const configuredKey = import.meta.env.VITE_VIETMAP_TILEMAP_KEY?.trim() ?? ''
const tilemapKey = configuredKey === 'your_tilemap_key' ? '' : configuredKey
const routeEnabled = import.meta.env.VITE_TRACKING_ROUTE_ENABLED === 'true'

interface Props {
  order: OrderDetail
  token: string
  requests: ReturnType<typeof createTrackingRequests>
}

function trackingErrorMessage(error: unknown, status: TrackingState['denied']) {
  switch (status) {
    case 401: return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
    case 403: return 'Bạn không có quyền xem vị trí của đơn hàng này.'
    case 404: return 'Không tìm thấy dữ liệu theo dõi của đơn hàng.'
    default: return isAxiosError(error)
      ? 'Không tải được vị trí đơn hàng. Vui lòng thử lại.'
      : 'Dữ liệu theo dõi đơn hàng không hợp lệ.'
  }
}

export default function OrderTrackingPanel({ order, token, requests }: Props) {
  const { state, refresh } = useOrderTrackingRealtime(order.id, order.status, token, requests)
  const { deliveryPoint, hasSnapshot, loading, error, denied, stale, connection } = state
  const finished = state.finished || isTrackingFinished(order.status)
  const driver = finished || denied ? null : state.location
  const recordedAt = driver?.recordedAt
  const delivering = order.status === 'DELIVERING' && !finished && !denied
  const selfDelivery = delivering && !order.driver
  const route = useOrderTrackingRoute(routeEnabled, order.id, selfDelivery ? 'PREPARING' : order.status, token, state, requests)
  const showMap = !denied && (hasSnapshot || driver !== null)
  const oldPosition = Boolean(driver && (stale || connection === 'offline' || connection === 'reconnecting'))
  const offline = delivering && connection === 'offline'
  const reconnecting = delivering && connection === 'reconnecting'
  const staleWarning = delivering && stale && !offline && !reconnecting
  const live = delivering && connection === 'live' && !stale
  const routeNotice = delivering && !selfDelivery && routeEnabled && live && driver && deliveryPoint
  const waitingForDriver = delivering && !driver && !selfDelivery && !offline && !reconnecting

  return (
    <section className="min-w-0 self-start space-y-3 rounded-2xl border border-slate-200 bg-white p-3 sm:p-4"
      aria-labelledby="tracking-map-title">
      <h2 id="tracking-map-title" className={delivering ? 'sr-only' : 'font-semibold text-slate-900'}>Theo dõi giao hàng</h2>

      {error != null && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-800">{trackingErrorMessage(error, denied)}</p>
      )}
      {finished && !denied && (
        <p role="status" className={`flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium ${order.status === 'DELIVERED'
          ? 'bg-green-100 text-green-900' : 'bg-slate-100 text-slate-700'}`}>
          {order.status === 'DELIVERED' && <CheckCircleOutlined aria-hidden="true" />}
          {order.status === 'DELIVERED' ? 'Đơn hàng đã được giao thành công.' : 'Đã kết thúc theo dõi đơn hàng.'}
        </p>
      )}
      {(offline || reconnecting || staleWarning) && (
        <p role="status" className="flex items-start gap-2 rounded-lg bg-amber-100 px-3 py-2 text-sm font-medium text-amber-950">
          <ExclamationCircleOutlined aria-hidden="true" className="mt-0.5 shrink-0" />
          {offline ? 'Mất kết nối tạm thời.' : reconnecting ? 'Đang kết nối lại.' : 'Chưa nhận được vị trí mới từ tài xế.'}
          {oldPosition && ' Vẫn hiển thị vị trí gần nhất.'}
        </p>
      )}
      {routeNotice && <TrackingRouteNotice {...route} />}
      {delivering && <TrackingRouteSummary {...route} />}
      {!finished && !denied && (
        <div className="flex min-w-0 flex-wrap items-center justify-between gap-x-3 gap-y-1 text-xs sm:text-sm">
          <div className="min-w-0 space-y-1 text-slate-700">
            {live && (
              <p role="status" className="flex items-center gap-1.5 font-semibold text-green-800">
                <span className="size-2 shrink-0 rounded-full bg-green-700" aria-hidden="true" />
                Đang theo dõi trực tiếp
              </p>
            )}
            {delivering && connection === 'connecting' && <p role="status">Đang kết nối…</p>}
            {waitingForDriver && <p role="status">Đang chờ vị trí tài xế.</p>}
            {selfDelivery && <p>Đơn hàng do quán tự giao.</p>}
            {delivering && !selfDelivery && live && driver && !routeEnabled && <p>Chỉ đường chưa bật.</p>}
            {recordedAt && (
              <p className="text-slate-600">
                GPS{oldPosition ? ' gần nhất' : ''}{' '}
                <time dateTime={recordedAt}>{recordedAt.slice(11)} · {recordedAt.slice(8, 10)}/{recordedAt.slice(5, 7)}</time>
              </p>
            )}
          </div>
          <button type="button" disabled={loading} onClick={() => { void refresh() }}
            className="ml-auto inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 font-medium text-slate-700 hover:border-orange-300 hover:text-orange-800 disabled:opacity-50">
            <ReloadOutlined aria-hidden="true" />
            {loading ? 'Đang tải vị trí…' : 'Tải lại vị trí'}
          </button>
        </div>
      )}

      {loading && !showMap && (
        <p role="status" className="rounded-lg bg-slate-50 p-4 text-sm text-slate-700">Đang tải vị trí đơn hàng…</p>
      )}
      {showMap && (tilemapKey ? (
        <VietmapStreetMap
          tilemapKey={tilemapKey} deliveryPoint={deliveryPoint} driverPoint={driver}
          vehicleType={order.driver?.vehicleType} oldPosition={oldPosition}
          routeGeometry={route.state.status === 'ready' ? route.state.geometry : null}
        />
      ) : (
        <p role="status" className="rounded-lg bg-slate-50 p-4 text-sm text-slate-700">
          Bản đồ chưa được cấu hình Tilemap key VIETMAP.
        </p>
      ))}
      {showMap && !deliveryPoint && <p className="text-xs text-slate-600">Chưa có tọa độ giao hàng hợp lệ.</p>}
    </section>
  )
}
