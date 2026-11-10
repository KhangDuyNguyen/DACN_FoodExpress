import { useState } from 'react'
import { useParams, Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchRestaurantById, fetchRestaurantMenu } from '../../api/restaurants'
import type { MenuItemDTO, OptionGroupDTO } from '../../api/restaurants'
import { addCartItem, cartQueryKey, fetchCart } from '../../api/cart'
import { getApiError } from '../../api/apiError'
import { useAuth } from '../../auth/useAuth'

// Tên ngày trong tuần (1=Thứ 2 ... 7=Chủ nhật)
const DAY_NAMES: Record<number, string> = {
  1: 'Thứ 2', 2: 'Thứ 3', 3: 'Thứ 4', 4: 'Thứ 5',
  5: 'Thứ 6', 6: 'Thứ 7', 7: 'Chủ nhật',
}

export default function RestaurantDetail() {
  const { id } = useParams<{ id: string }>()
  const restaurantId = Number(id)
  const [activeCategory, setActiveCategory] = useState<number | null>(null)

  const { data: restaurant, isLoading: loadingInfo, isError: errorInfo } = useQuery({
    queryKey: ['restaurant', restaurantId],
    queryFn: () => fetchRestaurantById(restaurantId),
    enabled: !!restaurantId,
  })

  const { data: menu, isLoading: loadingMenu } = useQuery({
    queryKey: ['menu', restaurantId],
    queryFn: () => fetchRestaurantMenu(restaurantId),
    enabled: !!restaurantId,
  })

  // ── Thêm vào giỏ (Khang, Module C) ──
  const { user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [cartError, setCartError] = useState<string | null>(null)
  const isCustomer = user?.role === 'CUSTOMER'

  const { data: cart } = useQuery({ queryKey: cartQueryKey, queryFn: fetchCart, enabled: isCustomer })

  const addMutation = useMutation({
    mutationFn: addCartItem,
    onSuccess: (newCart) => {
      queryClient.setQueryData(cartQueryKey, newCart)
      setCartError(null)
    },
    onError: (error) => setCartError(getApiError(error).message),
  })

  function handleAdd(item: MenuItemDTO) {
    if (!user) {
      navigate(`/login?redirect=${encodeURIComponent(location.pathname)}`)
      return
    }
    if (!isCustomer) {
      setCartError('Chỉ tài khoản khách hàng mới đặt món được')
      return
    }
    // Giỏ chỉ chứa món của 1 quán: đang có món quán khác thì hỏi trước khi thay giỏ
    let replaceCart = false
    if (cart?.restaurantId && cart.restaurantId !== restaurantId) {
      if (!window.confirm(`Giỏ hàng đang có món của "${cart.restaurantName}". Xóa giỏ cũ để thêm món của quán này?`)) {
        return
      }
      replaceCart = true
    }
    addMutation.mutate({ menuItemId: item.id, quantity: 1, replaceCart })
  }

  if (loadingInfo) return <LoadingSkeleton />
  if (errorInfo || !restaurant) return (
    <div className="flex flex-col items-center py-20 text-gray-500">
      <p className="text-4xl">😕</p>
      <p className="mt-2 font-medium">Không tìm thấy quán</p>
      <Link to="/restaurants" className="mt-4 text-orange-500 hover:underline">← Quay lại danh sách</Link>
    </div>
  )

  const isOpen = restaurant.openStatus === 'OPEN'
  // Chuyển sang chi nhánh khác thì trang không bị tạo lại, activeCategory còn giữ id nhóm món của quán cũ.
  // Id đó không có trong menu quán mới thì quay về nhóm món đầu tiên.
  const currentCatId = menu?.categories.some(cat => cat.id === activeCategory)
    ? activeCategory
    : menu?.categories[0]?.id ?? null

  return (
    <div className="mx-auto max-w-4xl px-4 py-6">

      {/* Back */}
      <Link to="/restaurants" className="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-orange-500">
        ← Quay lại
      </Link>

      {/* Cover image */}
      <div className="relative h-52 w-full overflow-hidden rounded-2xl bg-gray-200">
        {restaurant.coverUrl ? (
          <img src={restaurant.coverUrl} alt={restaurant.name} className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-6xl">🍽️</div>
        )}
      </div>

      {/* Info */}
      <div className="mt-4 flex gap-4">
        {/* Logo */}
        {restaurant.logoUrl && (
          <img
            src={restaurant.logoUrl}
            alt={restaurant.name}
            className="-mt-8 h-16 w-16 flex-shrink-0 rounded-xl border-2 border-white object-cover shadow"
          />
        )}
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold text-gray-900">{restaurant.name}</h1>
            <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${isOpen ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'}`}>
              {isOpen ? 'Đang mở' : 'Đã đóng'}
            </span>
          </div>

          {/* Cuisine tags */}
          <div className="mt-1 flex flex-wrap gap-1">
            {restaurant.cuisines.map(c => (
              <span key={c.id} className="rounded-full bg-orange-50 px-2 py-0.5 text-xs text-orange-600">{c.name}</span>
            ))}
          </div>

          {/* Stats */}
          <div className="mt-2 flex flex-wrap gap-4 text-sm text-gray-600">
            <span>⭐ {restaurant.avgRating.toFixed(1)} ({restaurant.ratingCount} đánh giá)</span>
            <span>🕐 {restaurant.prepTimeMinutes} phút</span>
            <span>💳 Tối thiểu {restaurant.minOrderAmount.toLocaleString('vi-VN')}đ</span>
          </div>

          <p className="mt-1 text-sm text-gray-500">📍 {restaurant.address}</p>
          {restaurant.phone && <p className="text-sm text-gray-500">📞 {restaurant.phone}</p>}
          <p className="text-sm text-gray-500">🏪 {restaurant.merchant.businessName}</p>
        </div>
      </div>

      {/* Mô tả */}
      {restaurant.description && (
        <p className="mt-3 text-sm text-gray-600">{restaurant.description}</p>
      )}

      {/* Giờ mở cửa */}
      {restaurant.hours.length > 0 && (
        <div className="mt-4 rounded-xl border border-gray-200 bg-gray-50 p-4">
          <p className="mb-2 font-medium text-gray-700">🕐 Giờ mở cửa</p>
          <div className="grid grid-cols-2 gap-1 text-sm text-gray-600 sm:grid-cols-3">
            {restaurant.hours.map((h, i) => (
              <div key={i} className="flex justify-between gap-2">
                <span className="font-medium">{DAY_NAMES[h.dayOfWeek]}</span>
                <span>{h.openTime.slice(0, 5)} – {h.closeTime.slice(0, 5)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Chi nhánh khác của cùng chủ quán */}
      {restaurant.otherBranches.length > 0 && (
        <div className="mt-4 rounded-xl border border-gray-200 bg-white p-4">
          <p className="mb-2 font-medium text-gray-700">🏪 Chi nhánh khác của {restaurant.merchant.businessName}</p>
          <ul className="space-y-2 text-sm">
            {restaurant.otherBranches.map(b => (
              <li key={b.id}>
                <Link to={`/restaurants/${b.id}`} className="font-medium text-orange-600 hover:underline">
                  {b.name}
                </Link>
                <span className="text-gray-500"> · {b.address}</span>
                {b.openStatus !== 'OPEN' && <span className="text-gray-400"> · Đã đóng</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Menu */}
      <div className="mt-6">
        <h2 className="text-lg font-bold text-gray-900">Thực đơn</h2>

        {loadingMenu && (
          <div className="mt-4 space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-xl bg-gray-200" />
            ))}
          </div>
        )}

        {!loadingMenu && menu && menu.categories.length === 0 && (
          <p className="mt-4 text-gray-500">Quán chưa có thực đơn.</p>
        )}

        {!loadingMenu && menu && menu.categories.length > 0 && (
          <>
            {/* Category tabs */}
            <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
              {menu.categories.map(cat => (
                <button
                  key={cat.id}
                  onClick={() => setActiveCategory(cat.id)}
                  className={`flex-shrink-0 rounded-full border px-4 py-1.5 text-sm font-medium transition ${
                    currentCatId === cat.id
                      ? 'border-orange-500 bg-orange-500 text-white'
                      : 'border-gray-300 bg-white text-gray-700 hover:border-orange-400'
                  }`}
                >
                  {cat.name} ({cat.items.length})
                </button>
              ))}
            </div>

            {/* Món ăn trong category đang chọn */}
            {menu.categories
              .filter(cat => cat.id === currentCatId)
              .map(cat => (
                <div key={cat.id} className="mt-4 space-y-3">
                  {cat.items.map(item => (
                    <MenuItemCard
                      key={item.id}
                      item={item}
                      adding={addMutation.isPending}
                      onAdd={() => handleAdd(item)}
                    />
                  ))}
                </div>
              ))}
          </>
        )}
      </div>

      {/* Lỗi khi thêm món (món hết, quán đóng...) và thanh giỏ hàng của quán này */}
      {cartError && <p className="mt-4 rounded bg-red-50 p-2 text-sm text-red-700">{cartError}</p>}
      {cart && cart.restaurantId === restaurantId && cart.itemCount > 0 && (
        <div className="sticky bottom-4 mt-6 flex items-center justify-between rounded-xl bg-orange-600 px-4 py-3 text-white shadow-lg">
          <span>
            🛒 {cart.itemCount} phần · {cart.subtotal.toLocaleString('vi-VN')}đ
          </span>
          <Link to="/cart" className="rounded-lg bg-white px-3 py-1 text-sm font-medium text-orange-600 hover:bg-orange-50">
            Xem giỏ hàng
          </Link>
        </div>
      )}
    </div>
  )
}

// ── MenuItemCard ──────────────────────────────────────────────────────────

function MenuItemCard({ item, adding, onAdd }: { item: MenuItemDTO; adding: boolean; onAdd: () => void }) {
  const [expanded, setExpanded] = useState(false)
  const hasOptions = item.optionGroups.length > 0

  return (
    <div className={`flex gap-3 rounded-xl border p-3 ${item.isAvailable ? 'border-gray-200 bg-white' : 'border-gray-100 bg-gray-50 opacity-60'}`}>
      {/* Ảnh món */}
      <div className="h-20 w-20 flex-shrink-0 overflow-hidden rounded-lg bg-gray-100">
        {item.imageUrl ? (
          <img src={item.imageUrl} alt={item.name} className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-3xl">🍴</div>
        )}
      </div>

      {/* Thông tin */}
      <div className="flex flex-1 flex-col justify-between">
        <div>
          <div className="flex items-start justify-between gap-2">
            <h3 className="font-medium text-gray-900">{item.name}</h3>
            {!item.isAvailable && (
              <span className="flex-shrink-0 rounded bg-gray-200 px-1.5 py-0.5 text-xs text-gray-500">Hết món</span>
            )}
          </div>
          {item.description && (
            <p className="mt-0.5 text-xs text-gray-500 line-clamp-2">{item.description}</p>
          )}
          {/* Tùy chọn */}
          {hasOptions && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="mt-1 text-xs text-orange-500 hover:underline"
            >
              {expanded ? '▲ Ẩn tùy chọn' : '▼ Xem tùy chọn'}
            </button>
          )}
          {expanded && (
            <div className="mt-2 space-y-2">
              {item.optionGroups.map(og => (
                <OptionGroupView key={og.id} group={og} />
              ))}
            </div>
          )}
        </div>

        {/* Giá + nút thêm */}
        <div className="mt-2 flex items-center justify-between">
          <span className="font-semibold text-orange-600">
            {item.price.toLocaleString('vi-VN')}đ
          </span>
          <button
            onClick={onAdd}
            disabled={!item.isAvailable || adding}
            className="rounded-full bg-orange-500 px-3 py-1 text-sm font-medium text-white hover:bg-orange-600 disabled:cursor-not-allowed disabled:opacity-40"
          >
            + Thêm
          </button>
        </div>
      </div>
    </div>
  )
}

// ── OptionGroupView ───────────────────────────────────────────────────────

function OptionGroupView({ group }: { group: OptionGroupDTO }) {
  return (
    <div className="rounded-lg bg-gray-50 p-2">
      <p className="text-xs font-medium text-gray-700">
        {group.name}
        {group.isRequired && <span className="ml-1 text-red-500">*</span>}
      </p>
      <div className="mt-1 flex flex-wrap gap-1">
        {group.options.map(opt => (
          <span key={opt.id} className={`rounded-full border px-2 py-0.5 text-xs ${opt.isAvailable ? 'border-gray-300 text-gray-600' : 'border-gray-200 text-gray-400 line-through'}`}>
            {opt.name}
            {opt.extraPrice > 0 && ` +${opt.extraPrice.toLocaleString('vi-VN')}đ`}
          </span>
        ))}
      </div>
    </div>
  )
}

// ── Loading skeleton ──────────────────────────────────────────────────────

function LoadingSkeleton() {
  return (
    <div className="mx-auto max-w-4xl animate-pulse px-4 py-6">
      <div className="h-52 rounded-2xl bg-gray-200" />
      <div className="mt-4 space-y-2">
        <div className="h-6 w-1/2 rounded bg-gray-200" />
        <div className="h-4 w-1/3 rounded bg-gray-200" />
        <div className="h-4 w-2/3 rounded bg-gray-200" />
      </div>
    </div>
  )
}
