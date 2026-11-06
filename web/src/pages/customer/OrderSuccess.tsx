import { Link, useLocation, useParams } from 'react-router-dom'
import type { OrderDetail } from '../../api/orders'

// Trang Checkout chuyen sang day kem don vua tao trong state cua router
// (state van con khi tai lai trang trong cung tab).
export default function OrderSuccess() {
  const { id } = useParams<{ id: string }>()
  const order = (useLocation().state as { order?: OrderDetail } | null)?.order

  return (
    <div className="mx-auto mt-6 max-w-md rounded-xl border border-gray-200 bg-white p-6 text-center">
      <p className="text-5xl">✅</p>
      <h1 className="mt-3 text-xl font-bold text-gray-900">Đặt hàng thành công</h1>

      {order ? (
        <div className="mt-4 space-y-1 text-sm text-gray-600">
          <p>
            Mã đơn: <span className="font-semibold text-gray-900">{order.code}</span>
          </p>
          <p>Quán: {order.restaurant.name}</p>
          <p>Giao đến: {order.deliveryAddress}</p>
          <p>
            Tổng tiền:{' '}
            <span className="font-semibold text-orange-600">{order.totalAmount.toLocaleString('vi-VN')}đ</span>{' '}
            (thanh toán khi nhận hàng)
          </p>
          <p className="pt-2">Đơn đang chờ quán xác nhận.</p>
        </div>
      ) : (
        <p className="mt-4 text-sm text-gray-600">Đơn #{id} đã được ghi nhận, đang chờ quán xác nhận.</p>
      )}

      <div className="mt-6 flex justify-center gap-3">
        <Link
          to={`/orders/${id}`}
          className="rounded-lg bg-orange-600 px-5 py-2 font-medium text-white hover:bg-orange-700"
        >
          Theo dõi đơn
        </Link>
        <Link to="/restaurants" className="rounded-lg border px-5 py-2 font-medium text-gray-700 hover:bg-gray-50">
          Tiếp tục đặt món
        </Link>
      </div>
    </div>
  )
}
