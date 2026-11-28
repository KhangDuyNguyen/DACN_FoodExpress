import { ArrowLeftOutlined, CheckOutlined, EnvironmentOutlined, PhoneOutlined, ShopOutlined, UserOutlined } from '@ant-design/icons'
import { useId, useMemo, useState, useSyncExternalStore } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getApiError } from '../../api/apiError'
import {
  isFinished,
  statusLabel,
  type OrderDetail,
  type OrderStatus,
} from '../../api/orders'
import { useAuth } from '../../auth/useAuth'
import { STATUS_BADGE } from './orderStatusStyle'
import OrderTrackingPanel from './tracking/OrderTrackingPanel'
import { createTrackingRequests, trackingAccessError } from './tracking/trackingRequests'

// Cac buoc cua 1 don binh thuong, theo thu tu (tuan 4 them "Cho lay mon")
const STEPS: OrderStatus[] = ['PLACED', 'PREPARING', 'READY_FOR_PICKUP', 'DELIVERING', 'DELIVERED']

const VEHICLE_LABEL = { MOTORBIKE: 'Xe máy', BICYCLE: 'Xe đạp', CAR: 'Ô tô' }

// Don chua co tai xe: dang giao hoac da giao ma khong co tai xe nghia la quan tu giao
function noDriverText(status: OrderStatus): string {
  if (status === 'CANCELLED') return 'Không có'
  if (status === 'DELIVERING' || status === 'DELIVERED') return 'Quán tự giao'
  return 'Chưa có tài xế nhận'
}

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

// Cach thanh toan kem tinh trang tra tien. Don VNPay chi co buoc "Cho xac nhan" (PLACED) khi da tra tien.
function paymentText(order: OrderDetail): string {
  if (order.paymentMethod === 'COD') return 'Thanh toán khi nhận hàng (COD)'
  const paid = order.history.some((h) => h.status === 'PLACED')
  const provider = order.paymentMethod === 'MOMO' ? 'MoMo' : 'VNPay'
  return paid ? `${provider} · đã thanh toán` : `${provider} · chưa thanh toán`
}

function subscribeToSessionChange(notify: () => void) {
  function handleStorage(event: StorageEvent) {
    if (event.storageArea === localStorage && (event.key === 'token' || event.key === null)) notify()
  }
  window.addEventListener('storage', handleStorage)
  return () => window.removeEventListener('storage', handleStorage)
}

function readStoredToken() {
  return localStorage.getItem('token')
}

function OrderUnavailable({ message = 'Không tìm thấy đơn hàng' }: { message?: string }) {
  return (
    <div className="py-16 text-center text-gray-500">
      <p>{message}</p>
      <Link to="/orders" className="mt-3 inline-block text-orange-600 hover:underline">
        ← Đơn hàng của tôi
      </Link>
    </div>
  )
}

export default function OrderTracking() {
  const { id } = useParams<{ id: string }>()
  const { user, token } = useAuth()
  const storedToken = useSyncExternalStore(subscribeToSessionChange, readStoredToken)
  const [session, setSession] = useState({ token, revision: 0 })

  if (session.token !== token) {
    setSession({ token, revision: session.revision + 1 })
    return null
  }

  if (!user || !token || storedToken !== token) {
    return (
      <div className="py-16 text-center text-gray-600">
        <p>Phiên đăng nhập đã thay đổi. Tải lại trang để tiếp tục.</p>
        <button type="button" className="mt-3 text-orange-700 underline" onClick={() => window.location.reload()}>
          Tải lại trang
        </button>
      </div>
    )
  }

  const orderId = Number(id)
  if (!/^[1-9]\d*$/.test(id ?? '') || !Number.isSafeInteger(orderId)) return <OrderUnavailable />

  return <OrderTrackingDetails key={`${user.id}:${session.revision}:${orderId}`} orderId={orderId} token={token} />
}

function OrderTrackingDetails({ orderId, token }: { orderId: number; token: string }) {
  const requests = useMemo(() => createTrackingRequests(import.meta.env.VITE_API_URL ?? '/api', token), [token])
  // Keep the JWT out of the query cache key.
  const scope = useId()
  const queryClient = useQueryClient()
  const orderQueryKey = ['order', scope, orderId] as const

  const { data: order, isLoading, error } = useQuery({
    queryKey: orderQueryKey,
    queryFn: ({ signal }) => requests.order(orderId, signal),
    gcTime: 0,
    retry: (count, failure) => !trackingAccessError(failure) && count < 3,
    // Don chua xong thi 10 giay tai lai 1 lan de thay quan doi trang thai; xong roi thi thoi
    refetchInterval: (query) => (
      isFinished(query.state.data?.status) || trackingAccessError(query.state.error) ? false : 10_000
    ),
  })

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => requests.cancel(orderId, reason),
    onSuccess: async (updated) => {
      if (readStoredToken() !== token) return
      await queryClient.cancelQueries({ queryKey: orderQueryKey, exact: true })
      if (readStoredToken() !== token) return
      queryClient.setQueryData(orderQueryKey, updated)
      queryClient.invalidateQueries({ queryKey: ['my-orders'] })
    },
  })

  function handleCancel() {
    const reason = window.prompt('Lý do hủy đơn?')
    if (reason && reason.trim()) {
      cancelMutation.mutate(reason.trim())
    }
  }

  if (isLoading) return <p className="text-gray-500">Đang tải đơn hàng...</p>
  const accessError = trackingAccessError(error)
  if (accessError === 401) return <OrderUnavailable message="Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại." />
  if (accessError || order?.id !== orderId) return <OrderUnavailable message={error ? getApiError(error).message : undefined} />

  const currentStep = STEPS.indexOf(order.status)
  const driver = order.driver
  const driverPhone = driver?.phone?.trim().replace(/[\s().-]/g, '') ?? ''
  const callNumber = /^\+?\d{8,15}$/.test(driverPhone) ? driverPhone : null
  const completed = order.status === 'DELIVERED'
  const title = order.status === 'DELIVERING' ? 'Đơn hàng đang đến!'
    : completed ? 'Đã giao hàng!' : statusLabel(order.status)
  const badge = order.status === 'DELIVERING' || completed ? 'bg-green-100 text-green-800' : STATUS_BADGE[order.status]
  const card = 'min-w-0 rounded-2xl border border-slate-200 bg-white p-4'

  return (
    <div className="mx-auto max-w-7xl min-w-0 rounded-2xl bg-slate-50 px-2 py-3 text-slate-900 sm:px-4 sm:py-5" aria-label="Theo dõi đơn hàng">
      <header className="mb-4">
        <Link to="/orders" className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-slate-700 hover:text-orange-700">
          <ArrowLeftOutlined /> Đơn hàng của tôi
        </Link>
        <div className="flex min-w-0 flex-wrap items-start justify-between gap-x-3 gap-y-2">
          <div className="min-w-0">
            <h1 className="text-xl font-bold tracking-tight sm:text-3xl">{title}</h1>
            <p className="mt-1 break-all text-sm font-medium text-slate-600">{order.code}</p>
          </div>
          <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-semibold sm:text-sm ${badge ?? ''}`}>{statusLabel(order.status)}</span>
        </div>
      </header>

      <div className="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
        <OrderTrackingPanel order={order} token={token} requests={requests} />

        <aside className="grid min-w-0 gap-4">
          <section className={card} aria-labelledby="tracking-driver-title">
            <h2 id="tracking-driver-title" className="font-semibold">Tài xế giao hàng</h2>
            <div className="mt-3 flex min-w-0 items-center gap-3 text-sm">
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-orange-50 text-lg text-orange-700" aria-hidden="true"><UserOutlined /></span>
              <div className="min-w-0 flex-1 wrap-anywhere">
                <p className="font-semibold">{driver?.name || (driver ? 'Tài xế giao hàng' : noDriverText(order.status))}</p>
                {driver && (
                  <p className="text-slate-600">
                    {VEHICLE_LABEL[driver.vehicleType] ?? 'Chưa có thông tin phương tiện'}
                    {driver.licensePlate && ` · ${driver.licensePlate}`}
                  </p>
                )}
                {callNumber && <p className="text-slate-600">{driver?.phone}</p>}
              </div>
              {callNumber && (
                <a href={`tel:${callNumber}`} aria-label="Gọi tài xế"
                  className="grid size-11 shrink-0 place-items-center rounded-full bg-green-50 text-lg text-green-800 hover:bg-green-100">
                  <PhoneOutlined />
                </a>
              )}
            </div>
            <dl className="mt-3 grid gap-3 border-t border-slate-200 pt-3 text-sm">
              <div className="flex min-w-0 gap-3">
                <EnvironmentOutlined className="mt-1 shrink-0 text-orange-700" aria-hidden="true" />
                <div className="min-w-0 wrap-anywhere">
                  <dt className="font-semibold">Giao đến</dt>
                  <dd className="text-slate-700">{order.deliveryAddress || 'Chưa có địa chỉ giao hàng'}</dd>
                  {(order.receiverName || order.receiverPhone) && <dd className="text-slate-600">{[order.receiverName, order.receiverPhone].filter(Boolean).join(' · ')}</dd>}
                  {order.note && <dd className="text-slate-600">Ghi chú: {order.note}</dd>}
                </div>
              </div>
              <div className="flex min-w-0 gap-3">
                <ShopOutlined className="mt-1 shrink-0 text-orange-700" aria-hidden="true" />
                <div className="min-w-0 wrap-anywhere">
                  <dt className="font-semibold">{order.restaurant?.name || 'Cửa hàng'}</dt>
                  {order.restaurant?.address && <dd className="text-slate-600">{order.restaurant.address}</dd>}
                  {order.restaurant?.phone && <dd className="text-slate-600">{order.restaurant.phone}</dd>}
                </div>
              </div>
            </dl>
          </section>

          <section className={card} aria-labelledby="tracking-progress-title">
            <h2 id="tracking-progress-title" className="font-semibold">Tiến trình đơn hàng</h2>
            {currentStep >= 0 ? (
              <ol className="mt-3 grid gap-2.5 text-sm">
                {STEPS.map((step, index) => {
                  const past = index < currentStep || completed
                  const current = index === currentStep
                  const label = step === 'PLACED' && past ? 'Đã tiếp nhận đơn'
                    : step === 'PREPARING' && past ? 'Đã chuẩn bị món'
                      : step === 'READY_FOR_PICKUP' ? (past && driver ? 'Đã lấy món' : 'Món đã sẵn sàng')
                        : step === 'DELIVERING' ? 'Đang giao hàng' : statusLabel(step)

                  return (
                    <li key={step} aria-current={current ? 'step' : undefined}
                      className={`flex items-center gap-2.5 ${current && !completed ? 'font-semibold text-orange-700' : 'text-slate-700'}`}>
                      <span aria-hidden="true" className={`grid size-5 shrink-0 place-items-center rounded-full text-xs ${past
                        ? 'bg-green-700 text-white' : current ? 'bg-orange-100 text-orange-800' : 'bg-slate-200 text-slate-600'}`}>
                        {past ? <CheckOutlined /> : <span className="size-1.5 rounded-full bg-current" />}
                      </span>
                      {label}
                    </li>
                  )
                })}
              </ol>
            ) : (
              <p className="mt-2 text-sm text-slate-700">
                {order.status === 'PENDING_PAYMENT' ? 'Đơn đang chờ thanh toán. Quán chỉ nhận đơn sau khi thanh toán xong.'
                  : order.status === 'CANCELLED' && order.cancelReason ? `Đơn đã hủy · ${order.cancelReason}` : statusLabel(order.status)}
              </p>
            )}
            <p className="mt-3 text-xs text-slate-600">Đặt lúc {new Date(order.createdAt).toLocaleString('vi-VN')}</p>
            {!isFinished(order.status) && <p className="mt-1 text-xs text-slate-500">Trang tự cập nhật mỗi 10 giây.</p>}
            {order.status === 'PLACED' && driver && (
              <p className="mt-2 text-xs text-slate-600">Tài xế đã nhận giao đơn nên không thể hủy.</p>
            )}
            {order.status === 'PLACED' && !driver && (
              <button type="button" onClick={handleCancel} disabled={cancelMutation.isPending}
                className="mt-3 min-h-11 rounded-lg border border-red-200 px-3 text-sm text-red-700 hover:bg-red-50 disabled:opacity-50">
                Hủy đơn
              </button>
            )}
            {cancelMutation.isError && <p role="alert" className="mt-2 text-sm text-red-700">{getApiError(cancelMutation.error).message}</p>}
          </section>

          <section className={card} aria-labelledby="tracking-order-items">
            <h2 id="tracking-order-items" className="font-semibold">Chi tiết đơn hàng</h2>
            <ul className="mt-2 space-y-2 text-sm">
              {order.items.map((item, index) => (
                <li key={index} className="grid grid-cols-[minmax(0,1fr)_auto] gap-2">
                  <span className="wrap-anywhere">{item.quantity} × {item.name}
                    {item.note && <span className="text-slate-600"> ({item.note})</span>}
                  </span>
                  <span>{vnd(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <dl className="mt-3 space-y-1.5 border-t border-slate-200 pt-3 text-sm">
              <div className="flex justify-between gap-2"><dt className="text-slate-600">Tạm tính</dt><dd>{vnd(order.subtotal)}</dd></div>
              <div className="flex justify-between gap-2"><dt className="text-slate-600">Phí giao hàng</dt><dd>{vnd(order.deliveryFee)}</dd></div>
              {order.discountAmount > 0 && (
                <div className="flex justify-between gap-2 text-green-800"><dt>Giảm giá</dt><dd>−{vnd(order.discountAmount)}</dd></div>
              )}
              <div className="border-t border-slate-200 pt-2">
                <dt className="text-slate-600">{paymentText(order)}</dt>
                <dd className="mt-0.5 text-xl font-bold text-orange-700">{vnd(order.totalAmount)}</dd>
              </div>
            </dl>
          </section>

          <details className={card}>
            <summary className="min-h-6 cursor-pointer font-semibold">Lịch sử trạng thái</summary>
            {order.history.length ? (
              <ul className="mt-3 grid gap-3 text-sm">
                {order.history.map((entry, index) => (
                  <li key={index} className="min-w-0 wrap-anywhere">
                    <p>{statusLabel(entry.status)}{entry.note && ` · ${entry.note}`}</p>
                    <p className="text-xs text-slate-600">{new Date(entry.changedAt).toLocaleString('vi-VN')}</p>
                  </li>
                ))}
              </ul>
            ) : <p className="mt-2 text-sm text-slate-600">Chưa có cập nhật trạng thái.</p>}
          </details>
        </aside>
      </div>
    </div>
  )
}
