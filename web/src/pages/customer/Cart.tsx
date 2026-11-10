import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { getApiError } from '../../api/apiError'
import { cartQueryKey, fetchCart, removeCartItem, updateCartItem, type Cart as CartData, type CartItem } from '../../api/cart'

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

export default function Cart() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: cart, isLoading, isError } = useQuery({ queryKey: cartQueryKey, queryFn: fetchCart })

  // API sua/xoa tra ve gio moi, ghi thang vao cache thay vi tai lai
  const onCartChanged = (newCart: CartData) => queryClient.setQueryData(cartQueryKey, newCart)
  const updateMutation = useMutation({
    mutationFn: (vars: { item: CartItem; quantity: number }) =>
      updateCartItem(vars.item.id, { quantity: vars.quantity, note: vars.item.note }),
    onSuccess: onCartChanged,
  })
  const removeMutation = useMutation({ mutationFn: removeCartItem, onSuccess: onCartChanged })
  const busy = updateMutation.isPending || removeMutation.isPending
  const mutationError = updateMutation.error ?? removeMutation.error

  if (isLoading) return <p className="text-gray-500">Đang tải giỏ hàng...</p>
  if (isError || !cart) return <p className="text-red-600">Không tải được giỏ hàng, vui lòng thử lại.</p>

  if (cart.items.length === 0) {
    return (
      <div className="py-16 text-center text-gray-500">
        <p className="text-4xl">🛒</p>
        <p className="mt-2 font-medium">Giỏ hàng trống</p>
        <Link to="/restaurants" className="mt-4 inline-block text-orange-600 hover:underline">
          Chọn quán để đặt món
        </Link>
      </div>
    )
  }

  const hasSoldOut = cart.items.some((item) => !item.isAvailable)
  const missing = cart.minOrderAmount - cart.subtotal
  const canCheckout = !hasSoldOut && missing <= 0

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-2xl font-bold text-gray-900">Giỏ hàng</h1>
      <p className="mt-1 text-sm text-gray-500">
        Món của quán{' '}
        <Link to={`/restaurants/${cart.restaurantId}`} className="font-medium text-orange-600 hover:underline">
          {cart.restaurantName}
        </Link>
      </p>

      <ul className="mt-4 divide-y rounded-xl border border-gray-200 bg-white">
        {cart.items.map((item) => (
          <li key={item.id} className={`flex gap-3 p-3 ${item.isAvailable ? '' : 'bg-gray-50'}`}>
            <div className="h-16 w-16 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100">
              {item.imageUrl && <img src={item.imageUrl} alt={item.name} className="h-full w-full object-cover" />}
            </div>
            <div className="flex flex-1 flex-col gap-1">
              <div className="flex items-start justify-between gap-2">
                <p className="font-medium text-gray-900">{item.name}</p>
                <p className="font-semibold text-gray-900">{vnd(item.lineTotal)}</p>
              </div>
              {item.note && <p className="text-xs text-gray-500">Ghi chú: {item.note}</p>}
              {!item.isAvailable && (
                <p className="text-xs font-medium text-red-600">Món này đã hết, vui lòng xóa khỏi giỏ để đặt hàng</p>
              )}
              <div className="flex items-center gap-2 text-sm">
                <span className="text-gray-500">{vnd(item.unitPrice)}</span>
                <span className="text-gray-300">|</span>
                <button
                  onClick={() => updateMutation.mutate({ item, quantity: item.quantity - 1 })}
                  disabled={busy || item.quantity <= 1}
                  className="h-7 w-7 rounded border hover:bg-gray-100 disabled:opacity-40"
                  aria-label="Giảm số lượng"
                >
                  −
                </button>
                <span className="w-6 text-center font-medium">{item.quantity}</span>
                <button
                  onClick={() => updateMutation.mutate({ item, quantity: item.quantity + 1 })}
                  disabled={busy || item.quantity >= 99}
                  className="h-7 w-7 rounded border hover:bg-gray-100 disabled:opacity-40"
                  aria-label="Tăng số lượng"
                >
                  +
                </button>
                <button
                  onClick={() => removeMutation.mutate(item.id)}
                  disabled={busy}
                  className="ml-auto text-red-600 hover:underline disabled:opacity-40"
                >
                  Xóa
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>

      {mutationError && <p className="mt-3 text-sm text-red-600">{getApiError(mutationError).message}</p>}

      <div className="mt-4 rounded-xl border border-gray-200 bg-white p-4">
        <div className="flex justify-between">
          <span className="text-gray-600">Tạm tính ({cart.itemCount} phần)</span>
          <span className="font-semibold">{vnd(cart.subtotal)}</span>
        </div>
        <p className="mt-1 text-xs text-gray-500">Phí giao hàng tính ở bước thanh toán, theo địa chỉ giao.</p>
        {missing > 0 && (
          <p className="mt-2 text-sm text-orange-700">
            Đơn tối thiểu của quán là {vnd(cart.minOrderAmount)}, còn thiếu {vnd(missing)}.
          </p>
        )}
        <button
          onClick={() => navigate('/checkout')}
          disabled={!canCheckout}
          className="mt-4 w-full rounded-lg bg-orange-600 py-2 font-medium text-white hover:bg-orange-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Thanh toán
        </button>
      </div>
    </div>
  )
}
