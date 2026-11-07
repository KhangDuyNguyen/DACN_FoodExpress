import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { addressesQueryKey, fetchMyAddresses } from '../../api/addresses'
import { getApiError } from '../../api/apiError'
import { cartQueryKey, fetchCart } from '../../api/cart'
import { createOrder, previewOrder } from '../../api/orders'

// Trang so dia chi, luu xong quay lai day
const ADDRESS_BOOK_LINK = '/profile/addresses?redirect=/checkout'

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

export default function Checkout() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: cart, isLoading } = useQuery({ queryKey: cartQueryKey, queryFn: fetchCart })
  const { data: addresses = [], isLoading: loadingAddresses } = useQuery({
    queryKey: addressesQueryKey,
    queryFn: fetchMyAddresses,
  })
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [note, setNote] = useState('')

  // Chua chon thi lay dia chi dau tien (backend tra dia chi mac dinh len dau)
  const selected = addresses.find((a) => a.id === selectedId) ?? addresses[0]

  // Doi dia chi thi tinh lai phi giao va tong tien (server tinh, client chi hien thi)
  const preview = useQuery({
    queryKey: ['order-preview', selected?.lat, selected?.lng],
    queryFn: () => previewOrder({ deliveryLat: selected!.lat, deliveryLng: selected!.lng }),
    enabled: !!cart && cart.items.length > 0 && !!selected,
  })

  const orderMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: (order) => {
      // Server da xoa gio sau khi dat thanh cong
      queryClient.invalidateQueries({ queryKey: cartQueryKey })
      navigate(`/orders/${order.id}/success`, { replace: true, state: { order } })
    },
  })

  function placeOrder() {
    if (!selected) return
    orderMutation.mutate({
      receiverName: selected.receiverName,
      receiverPhone: selected.receiverPhone,
      deliveryAddress: selected.addressLine,
      deliveryLat: selected.lat,
      deliveryLng: selected.lng,
      note: note.trim() || undefined,
      paymentMethod: 'COD',
    })
  }

  if (isLoading || loadingAddresses) return <p className="text-gray-500">Đang tải...</p>
  // Gio trong (vd vua dat xong hoac vao thang link) thi quay ve trang gio
  if (!cart || cart.items.length === 0) return <Navigate to="/cart" replace />

  return (
    <div className="mx-auto grid max-w-5xl gap-6 md:grid-cols-5">
      <div className="space-y-4 md:col-span-3">
        <h1 className="text-2xl font-bold text-gray-900">Thanh toán</h1>

        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <div className="flex items-center justify-between gap-2">
            <p className="font-medium text-gray-800">Giao đến</p>
            <Link to={ADDRESS_BOOK_LINK} className="text-sm text-orange-600 hover:underline">
              Quản lý sổ địa chỉ
            </Link>
          </div>

          {addresses.length === 0 ? (
            <div className="mt-3 rounded border border-dashed p-4 text-center text-sm text-gray-600">
              Bạn chưa có địa chỉ giao hàng.{' '}
              <Link to={ADDRESS_BOOK_LINK} className="font-medium text-orange-600 hover:underline">
                Thêm địa chỉ
              </Link>
            </div>
          ) : (
            <div className="mt-3 space-y-2">
              {addresses.map((a) => (
                <label key={a.id} className="flex cursor-pointer items-start gap-2 rounded border p-2 hover:bg-gray-50">
                  <input
                    type="radio"
                    name="address"
                    checked={selected?.id === a.id}
                    onChange={() => setSelectedId(a.id)}
                    className="mt-1"
                  />
                  <span>
                    <span className="font-medium text-gray-800">
                      {a.label ?? 'Địa chỉ'}
                      {a.isDefault && <span className="ml-2 text-xs text-orange-600">(mặc định)</span>}
                    </span>
                    <span className="block text-sm text-gray-600">
                      {a.receiverName} · {a.receiverPhone}
                    </span>
                    <span className="block text-sm text-gray-500">{a.addressLine}</span>
                  </span>
                </label>
              ))}
            </div>
          )}

          <label className="mt-3 block text-sm text-gray-700">Ghi chú cho tài xế (không bắt buộc)</label>
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={255}
            className="mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none"
            placeholder="Ví dụ: gọi trước khi tới"
          />
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="font-medium text-gray-800">Thanh toán</p>
          <p className="mt-2 text-sm text-gray-600">💵 Thanh toán khi nhận hàng (COD)</p>
        </div>
      </div>

      <div className="md:col-span-2">
        <div className="rounded-xl border border-gray-200 bg-white p-4 md:sticky md:top-4">
          <p className="font-medium text-gray-800">{cart.restaurantName}</p>
          <ul className="mt-3 space-y-1 text-sm">
            {cart.items.map((item) => (
              <li key={item.id} className="flex justify-between gap-2">
                <span>
                  {item.quantity} × {item.name}
                </span>
                <span>{vnd(item.lineTotal)}</span>
              </li>
            ))}
          </ul>

          <div className="mt-3 space-y-1 border-t pt-3 text-sm">
            {!selected && <p className="text-gray-500">Chọn địa chỉ giao để tính phí ship.</p>}
            {preview.isLoading && <p className="text-gray-500">Đang tính phí giao...</p>}
            {preview.isError && <p className="text-red-600">{getApiError(preview.error).message}</p>}
            {preview.data && (
              <>
                <div className="flex justify-between">
                  <span className="text-gray-600">Tạm tính</span>
                  <span>{vnd(preview.data.subtotal)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-gray-600">Phí giao ({preview.data.distanceKm.toLocaleString('vi-VN')} km)</span>
                  <span>{vnd(preview.data.deliveryFee)}</span>
                </div>
                {preview.data.discountAmount > 0 && (
                  <div className="flex justify-between">
                    <span className="text-gray-600">Giảm giá</span>
                    <span>−{vnd(preview.data.discountAmount)}</span>
                  </div>
                )}
                <div className="flex justify-between pt-1 text-base font-semibold">
                  <span>Tổng cộng</span>
                  <span className="text-orange-600">{vnd(preview.data.totalAmount)}</span>
                </div>
              </>
            )}
          </div>

          {orderMutation.isError && (
            <p className="mt-3 rounded bg-red-50 p-2 text-sm text-red-700">{getApiError(orderMutation.error).message}</p>
          )}

          <button
            onClick={placeOrder}
            disabled={orderMutation.isPending || !preview.data || !selected}
            className="mt-4 w-full rounded-lg bg-orange-600 py-2 font-medium text-white hover:bg-orange-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {orderMutation.isPending ? 'Đang đặt hàng...' : 'Đặt hàng'}
          </button>
          <Link to="/cart" className="mt-2 block text-center text-sm text-gray-500 hover:text-orange-600">
            ← Sửa giỏ hàng
          </Link>
        </div>
      </div>
    </div>
  )
}
