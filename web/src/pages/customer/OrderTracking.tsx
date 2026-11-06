import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getApiError } from '../../api/apiError'
import { cancelMyOrder, fetchMyOrder, isFinished, statusLabel, type OrderStatus } from '../../api/orders'
import { STATUS_BADGE } from './orderStatusStyle'

// TODO(an): them ban do vi tri tai xe khi don dang giao (phan theo doi don cua An)
// TODO(diep/an): thay viec tu tai lai 10 giay bang WebSocket khi co realtime

// Cac buoc cua 1 don binh thuong, theo thu tu
const STEPS: OrderStatus[] = ['PLACED', 'PREPARING', 'DELIVERING', 'DELIVERED']

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

export default function OrderTracking() {
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const queryClient = useQueryClient()

  const { data: order, isLoading, isError, error } = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => fetchMyOrder(orderId),
    enabled: !!orderId,
    // Don chua xong thi 10 giay tai lai 1 lan de thay quan doi trang thai; xong roi thi thoi
    refetchInterval: (query) => (isFinished(query.state.data?.status) ? false : 10_000),
  })

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => cancelMyOrder(orderId, reason),
    onSuccess: (updated) => {
      queryClient.setQueryData(['order', orderId], updated)
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
  if (isError || !order) {
    return (
      <div className="py-16 text-center text-gray-500">
        <p>{error ? getApiError(error).message : 'Không tìm thấy đơn hàng'}</p>
        <Link to="/orders" className="mt-3 inline-block text-orange-600 hover:underline">
          ← Đơn hàng của tôi
        </Link>
      </div>
    )
  }

  const currentStep = STEPS.indexOf(order.status)

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <Link to="/orders" className="text-sm text-gray-500 hover:text-orange-600">
        ← Đơn hàng của tôi
      </Link>

      <div className="rounded-xl border border-gray-200 bg-white p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Đơn {order.code}</h1>
            <p className="text-sm text-gray-500">Đặt lúc {new Date(order.createdAt).toLocaleString('vi-VN')}</p>
          </div>
          <span className={`rounded-full px-3 py-1 text-sm font-semibold ${STATUS_BADGE[order.status] ?? ''}`}>
            {statusLabel(order.status)}
          </span>
        </div>

        {/* Thanh tien trinh 4 buoc, hoac thong bao da huy */}
        {order.status === 'CANCELLED' ? (
          <p className="mt-4 rounded bg-gray-100 p-3 text-sm text-gray-700">
            Đơn đã hủy{order.cancelReason ? `: ${order.cancelReason}` : ''}
          </p>
        ) : (
          <ol className="mt-4 grid grid-cols-4 gap-2 text-center text-xs">
            {STEPS.map((step, index) => (
              <li key={step}>
                <div className={`h-2 rounded-full ${index <= currentStep ? 'bg-orange-500' : 'bg-gray-200'}`} />
                <p className={`mt-1 ${index <= currentStep ? 'font-semibold text-orange-600' : 'text-gray-400'}`}>
                  {statusLabel(step)}
                </p>
              </li>
            ))}
          </ol>
        )}

        {!isFinished(order.status) && (
          <p className="mt-3 text-xs text-gray-400">Trang tự cập nhật mỗi 10 giây.</p>
        )}

        {order.status === 'PLACED' && (
          <button
            onClick={handleCancel}
            disabled={cancelMutation.isPending}
            className="mt-3 rounded-lg border border-red-300 px-4 py-1.5 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            Hủy đơn
          </button>
        )}
        {cancelMutation.isError && (
          <p className="mt-2 text-sm text-red-600">{getApiError(cancelMutation.error).message}</p>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm">
          <p className="font-medium text-gray-800">{order.restaurant.name}</p>
          <p className="text-gray-500">{order.restaurant.address}</p>
          {order.restaurant.phone && <p className="text-gray-500">📞 {order.restaurant.phone}</p>}
          <p className="mt-3 font-medium text-gray-800">Giao đến</p>
          <p className="text-gray-600">
            {order.receiverName} · {order.receiverPhone}
          </p>
          <p className="text-gray-600">{order.deliveryAddress}</p>
          {order.note && <p className="text-gray-500">Ghi chú: {order.note}</p>}
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm">
          <ul className="space-y-1">
            {order.items.map((item, index) => (
              <li key={index} className="flex justify-between gap-2">
                <span>
                  {item.quantity} × {item.name}
                  {item.note && <span className="text-gray-400"> ({item.note})</span>}
                </span>
                <span>{vnd(item.lineTotal)}</span>
              </li>
            ))}
          </ul>
          <div className="mt-3 space-y-1 border-t pt-3">
            <div className="flex justify-between">
              <span className="text-gray-600">Tạm tính</span>
              <span>{vnd(order.subtotal)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600">Phí giao</span>
              <span>{vnd(order.deliveryFee)}</span>
            </div>
            <div className="flex justify-between font-semibold">
              <span>Tổng cộng (COD)</span>
              <span className="text-orange-600">{vnd(order.totalAmount)}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm">
        <p className="font-medium text-gray-800">Lịch sử trạng thái</p>
        <ul className="mt-2 space-y-1">
          {order.history.map((h, index) => (
            <li key={index} className="flex gap-3">
              <span className="w-40 flex-shrink-0 text-gray-400">{new Date(h.changedAt).toLocaleString('vi-VN')}</span>
              <span className="text-gray-700">
                {statusLabel(h.status)}
                {h.note && <span className="text-gray-500"> · {h.note}</span>}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
