import { Link } from 'react-router-dom'
import type { RestaurantSummaryDTO } from '../../api/restaurants'

interface Props {
  restaurant: RestaurantSummaryDTO
}

export default function RestaurantCard({ restaurant }: Props) {
  const isOpen = restaurant.openStatus === 'OPEN'

  return (
    <Link
      to={`/restaurants/${restaurant.id}`}
      className="group block overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition hover:shadow-md"
    >
      {/* Ảnh quán */}
      <div className="relative h-44 w-full overflow-hidden bg-gray-100">
        {restaurant.logoUrl ? (
          <img
            src={restaurant.logoUrl}
            alt={restaurant.name}
            className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-5xl">🍽️</div>
        )}
        {/* Badge OPEN / CLOSED */}
        <span
          className={`absolute right-2 top-2 rounded-full px-2 py-0.5 text-xs font-semibold ${
            isOpen ? 'bg-green-500 text-white' : 'bg-gray-500 text-white'
          }`}
        >
          {isOpen ? 'Đang mở' : 'Đã đóng'}
        </span>
      </div>

      {/* Thông tin */}
      <div className="p-3">
        <h3 className="truncate font-semibold text-gray-900">{restaurant.name}</h3>

        {/* Cuisine tag */}
        {restaurant.cuisineName && (
          <span className="mt-1 inline-block rounded-full bg-orange-50 px-2 py-0.5 text-xs text-orange-600">
            {restaurant.cuisineName}
          </span>
        )}

        {/* Rating + thời gian */}
        <div className="mt-2 flex items-center gap-3 text-sm text-gray-600">
          <span className="flex items-center gap-1">
            ⭐ <span className="font-medium text-gray-800">{restaurant.avgRating.toFixed(1)}</span>
            <span className="text-gray-400">({restaurant.ratingCount})</span>
          </span>
          <span>·</span>
          <span>🕐 {restaurant.prepTimeMinutes} phút</span>
        </div>

        {/* Địa chỉ */}
        <p className="mt-1 truncate text-xs text-gray-400">{restaurant.address}</p>

        {/* Đơn tối thiểu */}
        <p className="mt-1 text-xs text-gray-500">
          Đơn tối thiểu:{' '}
          <span className="font-medium">
            {restaurant.minOrderAmount.toLocaleString('vi-VN')}đ
          </span>
        </p>
      </div>
    </Link>
  )
}
