import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router-dom'
import { getApiError } from '../../api/apiError'
import { confirmVnPayReturn, type VnPayReturnResult } from '../../api/payments'

// VNPay chuyen trinh duyet ve day, ket qua nam tren URL (?vnp_...).
// Trang KHONG tu tin tham so tren URL: gui len server kiem tra chu ky (va hoi lai VNPay neu don con cho).

function resultView(result: VnPayReturnResult) {
  if (result.success) {
    return { icon: '✅', title: 'Thanh toán thành công', note: 'Đơn đã được gửi cho quán, đang chờ quán xác nhận.' }
  }
  if (result.paymentStatus === 'FAILED') {
    return { icon: '❌', title: 'Thanh toán không thành công', note: 'Đơn đã được hủy, bạn có thể đặt lại món.' }
  }
  return {
    icon: '⏳',
    title: 'Chưa xác nhận được thanh toán',
    note: 'Hệ thống chưa nhận được xác nhận từ VNPay. Trang đơn hàng sẽ tự cập nhật khi có kết quả.',
  }
}

export default function VnPayReturn() {
  const { search } = useLocation()
  const hasResult = search.includes('vnp_')
  const result = useQuery({
    queryKey: ['vnpay-return', search],
    queryFn: () => confirmVnPayReturn(search),
    enabled: hasResult,
    retry: false,
    staleTime: Infinity, // xac nhan 1 lan la du, khong tu goi lai khi doi tab
  })

  if (!hasResult) {
    return (
      <div className="py-16 text-center text-gray-500">
        <p>Không có kết quả thanh toán.</p>
        <Link to="/orders" className="mt-3 inline-block text-orange-600 hover:underline">
          ← Đơn hàng của tôi
        </Link>
      </div>
    )
  }
  if (result.isLoading) return <p className="py-16 text-center text-gray-500">Đang xác nhận kết quả thanh toán...</p>
  if (result.isError || !result.data) {
    return (
      <div className="mx-auto mt-6 max-w-md rounded-xl border border-gray-200 bg-white p-6 text-center">
        <p className="text-5xl">⚠️</p>
        <h1 className="mt-3 text-xl font-bold text-gray-900">Không xác nhận được kết quả thanh toán</h1>
        <p className="mt-2 text-sm text-gray-600">{getApiError(result.error).message}</p>
        <Link to="/orders" className="mt-6 inline-block text-orange-600 hover:underline">
          ← Đơn hàng của tôi
        </Link>
      </div>
    )
  }

  const data = result.data
  const view = resultView(data)
  return (
    <div className="mx-auto mt-6 max-w-md rounded-xl border border-gray-200 bg-white p-6 text-center">
      <p className="text-5xl">{view.icon}</p>
      <h1 className="mt-3 text-xl font-bold text-gray-900">{view.title}</h1>
      <div className="mt-4 space-y-1 text-sm text-gray-600">
        <p>
          Mã đơn: <span className="font-semibold text-gray-900">{data.orderCode}</span>
        </p>
        {!data.success && <p>{data.message}</p>}
        <p className="pt-2">{view.note}</p>
      </div>
      <div className="mt-6 flex justify-center gap-3">
        <Link
          to={`/orders/${data.orderId}`}
          className="rounded-lg bg-orange-600 px-5 py-2 font-medium text-white hover:bg-orange-700"
        >
          Xem đơn
        </Link>
        <Link to="/restaurants" className="rounded-lg border px-5 py-2 font-medium text-gray-700 hover:bg-gray-50">
          {data.paymentStatus === 'FAILED' ? 'Đặt lại món' : 'Tiếp tục đặt món'}
        </Link>
      </div>
    </div>
  )
}
