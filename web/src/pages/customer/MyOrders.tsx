import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { fetchMyOrders, ORDER_STATUS_LABEL, statusLabel, type OrderStatus } from '../../api/orders'
import { STATUS_BADGE } from './orderStatusStyle'

// TODO(duong): them nut "Danh gia" cho don da hoan thanh (phan danh gia cua Duong)

const PAGE_SIZE = 10

export default function MyOrders() {
  const [status, setStatus] = useState<OrderStatus | undefined>()
  const [page, setPage] = useState(0)

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
              <Link
                to={`/orders/${o.id}`}
                className="block rounded-xl border border-gray-200 bg-white p-4 hover:border-orange-300 hover:shadow-sm"
              >
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
            <span className="text-gray-600">
              Trang {page + 1} / {totalPages}
            </span>
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
    </div>
  )
}
