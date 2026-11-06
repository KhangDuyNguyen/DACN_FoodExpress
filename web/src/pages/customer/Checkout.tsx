import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm, useWatch } from 'react-hook-form'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { getApiError } from '../../api/apiError'
import { cartQueryKey, fetchCart } from '../../api/cart'
import { createOrder, previewOrder } from '../../api/orders'
import { useAuth } from '../../auth/useAuth'

// TODO(khang): tuan sau thay bang so dia chi cua khach (bang addresses) va chon tren ban do.
// Tuan 3 cho chon vai dia chi mau co san toa do (du lieu gia de demo).
const SAMPLE_ADDRESSES = [
  { label: 'Nhà riêng', address: '10 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM', lat: 10.7756, lng: 106.7004 },
  { label: 'Công ty', address: '200 Nguyễn Thị Minh Khai, Phường 6, Quận 3, TP.HCM', lat: 10.7795, lng: 106.6905 },
  { label: 'Nhà bạn', address: '15 Phan Xích Long, Phường 2, Quận Phú Nhuận, TP.HCM', lat: 10.799, lng: 106.687 },
]

// Quy tac giong backend (CreateOrderRequest.java)
const schema = z.object({
  receiverName: z.string().trim().min(1, 'Vui lòng nhập tên người nhận').max(100, 'Tên người nhận tối đa 100 ký tự'),
  receiverPhone: z.string().trim().regex(/^0\d{9}$/, 'Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0'),
  addressIndex: z.string(),
  note: z.string().max(255, 'Ghi chú tối đa 255 ký tự'),
})

type FormValues = z.infer<typeof schema>

const fieldClass = 'mt-1 w-full rounded border px-3 py-2 focus:border-orange-500 focus:outline-none'

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

export default function Checkout() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: cart, isLoading } = useQuery({ queryKey: cartQueryKey, queryFn: fetchCart })

  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { receiverName: user?.fullName ?? '', receiverPhone: user?.phone ?? '', addressIndex: '0', note: '' },
  })

  // useWatch: render lai khi khach chon dia chi khac
  const addressIndex = useWatch({ control, name: 'addressIndex' })
  const selected = SAMPLE_ADDRESSES[Number(addressIndex)]

  // Doi dia chi thi tinh lai phi giao va tong tien (server tinh, client chi hien thi)
  const preview = useQuery({
    queryKey: ['order-preview', selected.lat, selected.lng],
    queryFn: () => previewOrder({ deliveryLat: selected.lat, deliveryLng: selected.lng }),
    enabled: !!cart && cart.items.length > 0,
  })

  const orderMutation = useMutation({
    mutationFn: createOrder,
    onSuccess: (order) => {
      // Server da xoa gio sau khi dat thanh cong
      queryClient.invalidateQueries({ queryKey: cartQueryKey })
      navigate(`/orders/${order.id}/success`, { replace: true, state: { order } })
    },
    onError: (error) => {
      // Loi 400 tung o: hien duoi dung o nhap; loi khac (409...) hien o cuoi form
      const { fieldErrors } = getApiError(error)
      for (const [field, message] of Object.entries(fieldErrors)) {
        if (field === 'receiverName' || field === 'receiverPhone' || field === 'note') {
          setError(field, { message })
        }
      }
    },
  })

  function onSubmit(values: FormValues) {
    orderMutation.mutate({
      receiverName: values.receiverName,
      receiverPhone: values.receiverPhone,
      deliveryAddress: selected.address,
      deliveryLat: selected.lat,
      deliveryLng: selected.lng,
      note: values.note || undefined,
      paymentMethod: 'COD',
    })
  }

  if (isLoading) return <p className="text-gray-500">Đang tải...</p>
  // Gio trong (vd vua dat xong hoac vao thang link) thi quay ve trang gio
  if (!cart || cart.items.length === 0) return <Navigate to="/cart" replace />

  return (
    <div className="mx-auto grid max-w-5xl gap-6 md:grid-cols-5">
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 md:col-span-3" noValidate>
        <h1 className="text-2xl font-bold text-gray-900">Thanh toán</h1>

        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="font-medium text-gray-800">Người nhận</p>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <div>
              <label className="block text-sm text-gray-700">Họ tên</label>
              <input {...register('receiverName')} className={fieldClass} autoComplete="name" />
              {errors.receiverName && <p className="mt-1 text-sm text-red-600">{errors.receiverName.message}</p>}
            </div>
            <div>
              <label className="block text-sm text-gray-700">Số điện thoại</label>
              <input {...register('receiverPhone')} className={fieldClass} autoComplete="tel" inputMode="numeric" />
              {errors.receiverPhone && <p className="mt-1 text-sm text-red-600">{errors.receiverPhone.message}</p>}
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="font-medium text-gray-800">Địa chỉ giao hàng</p>
          <div className="mt-3 space-y-2">
            {SAMPLE_ADDRESSES.map((a, index) => (
              <label key={a.address} className="flex cursor-pointer items-start gap-2 rounded border p-2 hover:bg-gray-50">
                <input type="radio" value={String(index)} {...register('addressIndex')} className="mt-1" />
                <span>
                  <span className="font-medium text-gray-800">{a.label}</span>
                  <span className="block text-sm text-gray-500">{a.address}</span>
                </span>
              </label>
            ))}
          </div>
          <label className="mt-3 block text-sm text-gray-700">Ghi chú cho tài xế (không bắt buộc)</label>
          <input {...register('note')} className={fieldClass} placeholder="Ví dụ: gọi trước khi tới" />
          {errors.note && <p className="mt-1 text-sm text-red-600">{errors.note.message}</p>}
        </div>

        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="font-medium text-gray-800">Thanh toán</p>
          <p className="mt-2 text-sm text-gray-600">💵 Thanh toán khi nhận hàng (COD)</p>
        </div>
      </form>

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

          {orderMutation.isError && Object.keys(getApiError(orderMutation.error).fieldErrors).length === 0 && (
            <p className="mt-3 rounded bg-red-50 p-2 text-sm text-red-700">{getApiError(orderMutation.error).message}</p>
          )}

          <button
            onClick={handleSubmit(onSubmit)}
            disabled={orderMutation.isPending || !preview.data}
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
