import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchMyOrders, ORDER_STATUS_LABEL, statusLabel, type OrderStatus } from '../../api/orders'
import { createReview, getOrderReview, type CreateReviewRequest } from '../../api/reviews'
import { STATUS_BADGE } from './orderStatusStyle'

const PAGE_SIZE = 10

// ── Star rating component ─────────────────────────────────────────────────

function StarRating({
  value,
  onChange,
  label,
}: {
  value: number
  onChange: (v: number) => void
  label: string
}) {
  return (
    <div>
      <p className="mb-1 text-sm font-medium text-gray-600">{label}</p>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((star) => (
          <button
            key={star}
            type="button"
            onClick={() => onChange(star)}
            className={`text-2xl transition ${star <= value ? 'text-yellow-400' : 'text-gray-300 hover:text-yellow-300'}`}
          >
            ★
          </button>
        ))}
      </div>
    </div>
  )
}

// ── Review modal ──────────────────────────────────────────────────────────

function ReviewModal({
  orderId,
  restaurantName,
  onClose,
}: {
  orderId: number
  restaurantName: string
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [restaurantRating, setRestaurantRating] = useState(5)
  const [driverRating, setDriverRating] = useState(5)
  const [comment, setComment] = useState('')

  // Kiểm tra đã review chưa
  const { data: existing, isLoading: loadingExisting } = useQuery({
    queryKey: ['order-review', orderId],
    queryFn: () => getOrderReview(orderId),
    retry: false,
  })

  const createMut = useMutation({
    mutationFn: (data: CreateReviewRequest) => createReview(orderId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['order-review', orderId] })
      qc.invalidateQueries({ queryKey: ['my-orders'] })
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">Đánh giá đơn hàng</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
        </div>
        <p className="mb-4 text-sm text-gray-500">🏪 {restaurantName}</p>

        {loadingExisting ? (
          <p className="text-center text-gray-500 py-4">Đang tải...</p>
        ) : existing ? (
          // Đã review rồi — hiển thị review cũ
          <div className="space-y-3">
            <div className="rounded-lg bg-green-50 p-3 text-sm text-green-700 font-medium">
              ✅ Bạn đã đánh giá đơn này
            </div>
            <div className="text-sm text-gray-600">
              <p>⭐ Quán: {'★'.repeat(existing.restaurantRating)}{'☆'.repeat(5 - existing.restaurantRating)}</p>
              {existing.driverRating && (
                <p>🛵 Tài xế: {'★'.repeat(existing.driverRating)}{'☆'.repeat(5 - existing.driverRating)}</p>
              )}
              {existing.comment && <p className="mt-1 italic">"{existing.comment}"</p>}
            </div>
            {existing.reply && (
              <div className="rounded-lg bg-gray-50 p-3 text-sm">
                <p className="font-medium text-gray-700">💬 Phản hồi từ quán:</p>
                <p className="mt-1 text-gray-600">{existing.reply.content}</p>
              </div>
            )}
            <button onClick={onClose}
              className="w-full rounded-lg bg-gray-100 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200">
              Đóng
            </button>
          </div>
        ) : createMut.isSuccess ? (
          // Vừa gửi thành công
          <div className="text-center py-4 space-y-3">
            <p className="text-4xl">🎉</p>
            <p className="font-semibold text-gray-800">Cảm ơn bạn đã đánh giá!</p>
            <button onClick={onClose}
              className="rounded-lg bg-orange-500 px-6 py-2 text-sm font-medium text-white hover:bg-orange-600">
              Đóng
            </button>
          </div>
        ) : (
          // Form đánh giá
          <div className="space-y-4">
            <StarRating value={restaurantRating} onChange={setRestaurantRating} label="Chất lượng quán *" />
            <StarRating value={driverRating} onChange={setDriverRating} label="Tài xế giao hàng" />
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-600">Nhận xét (không bắt buộc)</label>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={3}
                maxLength={2000}
                placeholder="Chia sẻ trải nghiệm của bạn..."
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm outline-none focus:border-orange-400"
              />
            </div>
            {createMut.isError && (
              <p className="text-sm text-red-500">Có lỗi xảy ra. Vui lòng thử lại.</p>
            )}
            <div className="flex gap-3">
              <button
                onClick={() => createMut.mutate({
                  restaurantRating,
                  driverRating: driverRating || null,
                  comment: comment.trim() || undefined,
                })}
                disabled={createMut.isPending}
                className="flex-1 rounded-lg bg-orange-500 py-2.5 text-sm font-semibold text-white hover:bg-orange-600 disabled:opacity-50"
              >
                {createMut.isPending ? 'Đang gửi...' : 'Gửi đánh giá'}
              </button>
              <button onClick={onClose}
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
                Hủy
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

// ── Main component ────────────────────────────────────────────────────────

export default function MyOrders() {
  const [status, setStatus] = useState<OrderStatus | undefined>()
  const [page, setPage] = useState(0)
  const [reviewingOrder, setReviewingOrder] = useState<{ id: number; restaurantName: string } | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['my-orders', status, page],
    queryFn: () => fetchMyOrders({ status, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })
  const orders = data?.items ?? []
  const totalPages = data?.totalPages ?? 0

  const changeStatus = (next: OrderStatus | undefined) => {
    setStatus(next)
    setPage(0)
  }

  const filters: { value: OrderStatus | undefined; label: string }[] = [
    { value: undefined, label: 'Tất cả' },
    ...(Object.keys(ORDER_STATUS_LABEL) as OrderStatus[]).map((s) => ({ value: s, label: statusLabel(s) })),
  ]

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold text-gray-900">Đơn hàng của tôi</h1>

      <div className="mt-4 flex flex-wrap gap-2">
        {filters.map((f) => (
          <button
            key={f.label}
            onClick={() => changeStatus(f.value)}
            className={`rounded-full border px-4 py-1.5 text-sm font-medium transition ${
              status === f.value
                ? 'border-orange-500 bg-orange-500 text-white'
                : 'border-gray-300 bg-white text-gray-700 hover:border-orange-400'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      <div className="mt-4">
        {isLoading && <p className="text-gray-500">Đang tải đơn hàng...</p>}
        {isError && <p className="text-red-600">Không tải được đơn hàng, vui lòng thử lại.</p>}
        {!isLoading && !isError && orders.length === 0 && (
          <div className="py-12 text-center text-gray-500">
            <p className="text-4xl">🧾</p>
            <p className="mt-2">Chưa có đơn hàng nào</p>
            <Link to="/restaurants" className="mt-3 inline-block text-orange-600 hover:underline">
              Đặt món ngay
            </Link>
          </div>
        )}

        <ul className="space-y-3">
          {orders.map((o) => (
            <li key={o.id}>
              <div className="rounded-xl border border-gray-200 bg-white p-4">
                <Link to={`/orders/${o.id}`} className="block hover:opacity-90">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold text-gray-900">{o.restaurantName}</p>
                      <p className="text-xs text-gray-500">
                        {o.code} · {new Date(o.createdAt).toLocaleString('vi-VN')}
                      </p>
                    </div>
                    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${STATUS_BADGE[o.status] ?? ''}`}>
                      {statusLabel(o.status)}
                    </span>
                  </div>
                  <p className="mt-2 text-sm text-gray-600">
                    {o.itemCount} phần · <span className="font-medium text-gray-900">{o.totalAmount.toLocaleString('vi-VN')}đ</span>
                  </p>
                </Link>

                {/* Nút Đánh giá - chỉ hiển thị cho đơn DELIVERED */}
                {o.status === 'DELIVERED' && (
                  <div className="mt-3 border-t border-gray-100 pt-3">
                    <button
                      onClick={() => setReviewingOrder({ id: o.id, restaurantName: o.restaurantName })}
                      className="rounded-lg border border-orange-400 bg-orange-50 px-4 py-1.5 text-sm font-medium text-orange-600 hover:bg-orange-100 transition"
                    >
                      ⭐ Đánh giá
                    </button>
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>

        {totalPages > 1 && (
          <div className="mt-6 flex items-center justify-center gap-3 text-sm">
            <button
              onClick={() => setPage(page - 1)}
              disabled={page === 0}
              className="rounded-lg border border-gray-300 bg-white px-4 py-2 hover:bg-gray-50 disabled:opacity-40"
            >
              ← Trang trước
            </button>
            <span className="text-gray-600">Trang {page + 1} / {totalPages}</span>
            <button
              onClick={() => setPage(page + 1)}
              disabled={page + 1 >= totalPages}
              className="rounded-lg border border-gray-300 bg-white px-4 py-2 hover:bg-gray-50 disabled:opacity-40"
            >
              Trang sau →
            </button>
          </div>
        )}
      </div>

      {/* Review modal */}
      {reviewingOrder && (
        <ReviewModal
          orderId={reviewingOrder.id}
          restaurantName={reviewingOrder.restaurantName}
          onClose={() => setReviewingOrder(null)}
        />
      )}
    </div>
  )
}
