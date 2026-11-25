import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { fetchRecommendations, trackInteraction, type RecommendedItemDTO } from '../../api/ai'
import { getApiError } from '../../api/apiError'
import { addCartItem, cartQueryKey, fetchCart } from '../../api/cart'
import { useAuth } from '../../auth/useAuth'

interface Props {
  limit?: number
  restaurantId?: number
}

function buildRecViewEventId(userId: number, itemId: number): string {
  return `rec-view-${userId}-${itemId}-${Math.floor(Date.now() / 10000)}`
}

export default function RecommendedDishesSection({ limit = 8, restaurantId }: Props) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const isCustomer = user?.role === 'CUSTOMER'

  const [addedItemId, setAddedItemId] = useState<number | null>(null)
  const [cartError, setCartError] = useState<string | null>(null)

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['ai-recommendations', user?.id ?? 'guest', restaurantId ?? 'all', limit],
    queryFn: () => fetchRecommendations({ limit, restaurantId }),
    staleTime: 60_000,
    retry: 1,
  })

  const { data: cart } = useQuery({
    queryKey: cartQueryKey,
    queryFn: fetchCart,
    enabled: isCustomer,
  })

  const addMutation = useMutation({
    mutationFn: addCartItem,
    onSuccess: (newCart, variables) => {
      queryClient.setQueryData(cartQueryKey, newCart)
      setCartError(null)
      setAddedItemId(variables.menuItemId)
      setTimeout(() => {
        setAddedItemId((prev) => (prev === variables.menuItemId ? null : prev))
      }, 2000)

      if (isCustomer) {
        void trackInteraction({
          menuItemId: variables.menuItemId,
          type: 'ADD_TO_CART',
          quantity: variables.quantity,
        })
        void queryClient.invalidateQueries({ queryKey: ['ai-recommendations'] })
      }
    },
    onError: (err) => setCartError(getApiError(err).message),
  })

  function handleViewItem(item: RecommendedItemDTO) {
    if (user) {
      void trackInteraction({
        menuItemId: item.id,
        type: 'VIEW',
        eventId: buildRecViewEventId(user.id, item.id),
      })
    }
  }

  function handleAddToCart(item: RecommendedItemDTO) {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`)
      return
    }
    if (!isCustomer) {
      setCartError('Chỉ tài khoản khách hàng mới thêm món vào giỏ được')
      return
    }
    let replaceCart = false
    if (cart?.restaurantId && cart.restaurantId !== item.restaurantId) {
      if (
        !window.confirm(
          `Giỏ hàng đang có món của "${cart.restaurantName}". Xóa giỏ cũ để thêm món của "${item.restaurantName}"?`,
        )
      ) {
        return
      }
      replaceCart = true
    }
    addMutation.mutate({ menuItemId: item.id, quantity: 1, replaceCart })
  }

  const isPersonalized = data?.strategy === 'PERSONALIZED'
  const items = data?.items ?? []

  return (
    <section className="mt-6 rounded-2xl border border-orange-100 bg-gradient-to-br from-orange-50/70 via-white to-amber-50/50 p-4 shadow-sm sm:p-5">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2.5">
          <h2 className="text-lg font-bold text-gray-900 sm:text-xl">Gợi ý cho bạn</h2>
          {!isLoading && !isError && items.length > 0 && (
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                isPersonalized
                  ? 'bg-orange-500 text-white'
                  : 'bg-amber-100 text-amber-800'
              }`}
            >
              {isPersonalized ? '✨ Cá nhân hóa cho bạn' : '🔥 Món bán chạy'}
            </span>
          )}
        </div>
        {data?.message && !isError && items.length > 0 && (
          <p className="text-xs text-gray-500">{data.message}</p>
        )}
      </div>

      {cartError && (
        <div className="mt-3 flex items-center justify-between rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">
          <span>{cartError}</span>
          <button onClick={() => setCartError(null)} className="ml-2 font-medium underline">
            Đóng
          </button>
        </div>
      )}

      {/* Loading skeleton */}
      {isLoading && (
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, idx) => (
            <div key={idx} className="overflow-hidden rounded-xl border border-gray-100 bg-white p-3">
              <div className="h-32 w-full animate-pulse rounded-lg bg-gray-200" />
              <div className="mt-3 h-4 w-3/4 animate-pulse rounded bg-gray-200" />
              <div className="mt-2 h-3 w-1/2 animate-pulse rounded bg-gray-200" />
              <div className="mt-3 flex justify-between">
                <div className="h-4 w-16 animate-pulse rounded bg-gray-200" />
                <div className="h-7 w-16 animate-pulse rounded-full bg-gray-200" />
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Error state */}
      {isError && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-red-100 bg-white p-4 text-sm text-gray-600">
          <span>Không thể tải danh sách món gợi ý lúc này.</span>
          <button
            onClick={() => refetch()}
            className="rounded-lg border border-orange-500 px-3 py-1 text-xs font-medium text-orange-600 hover:bg-orange-50"
          >
            Thử lại
          </button>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !isError && items.length === 0 && (
        <div className="mt-4 rounded-xl border border-dashed border-orange-200 bg-white/80 py-8 text-center text-sm text-gray-500">
          Hiện chưa có món gợi ý phù hợp. Hãy khám phá thực đơn các quán bên dưới nhé!
        </div>
      )}

      {/* Items grid */}
      {!isLoading && !isError && items.length > 0 && (
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {items.map((item) => {
            const isOpen = item.restaurantOpenStatus === 'OPEN'
            const canOrder = item.isAvailable && isOpen
            const justAdded = addedItemId === item.id

            return (
              <div
                key={item.id}
                className="group flex flex-col justify-between overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition hover:border-orange-300 hover:shadow-md"
              >
                <Link
                  to={`/restaurants/${item.restaurantId}?highlightItem=${item.id}`}
                  onClick={() => handleViewItem(item)}
                  className="block"
                >
                  <div className="relative h-32 w-full overflow-hidden bg-gray-100">
                    {item.imageUrl ? (
                      <img
                        src={item.imageUrl}
                        alt={item.name}
                        className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
                      />
                    ) : (
                      <div className="flex h-full items-center justify-center text-4xl">🍴</div>
                    )}
                    <span
                      className={`absolute right-2 top-2 rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                        isOpen ? 'bg-green-500 text-white' : 'bg-gray-500 text-white'
                      }`}
                    >
                      {isOpen ? 'Đang mở' : 'Đã đóng'}
                    </span>
                  </div>

                  <div className="p-3 pb-1">
                    <h3 className="truncate text-sm font-semibold text-gray-900 group-hover:text-orange-600">
                      {item.name}
                    </h3>
                    <p className="mt-0.5 truncate text-xs text-gray-500">🏪 {item.restaurantName}</p>
                    {item.reason && (
                      <p className="mt-1 line-clamp-1 text-[11px] text-orange-600">{item.reason}</p>
                    )}
                  </div>
                </Link>

                <div className="flex items-center justify-between gap-2 px-3 pb-3 pt-2">
                  <span className="text-sm font-bold text-orange-600">
                    {item.price.toLocaleString('vi-VN')}đ
                  </span>
                  <button
                    type="button"
                    onClick={() => handleAddToCart(item)}
                    disabled={!canOrder || addMutation.isPending}
                    className={`rounded-full px-2.5 py-1 text-xs font-medium transition ${
                      justAdded
                        ? 'bg-green-600 text-white'
                        : 'bg-orange-500 text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-40'
                    }`}
                  >
                    {justAdded ? '✓ Đã thêm' : '+ Thêm'}
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </section>
  )
}
