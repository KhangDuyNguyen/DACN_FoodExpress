import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchRestaurants, fetchCuisines } from '../../api/restaurants'
import RestaurantCard from '../../components/restaurant/RestaurantCard'

// Số quán mỗi trang (backend cho tối đa 50)
const PAGE_SIZE = 12

export default function RestaurantList() {
  const [keyword, setKeyword] = useState('')
  const [selectedCuisine, setSelectedCuisine] = useState<number | undefined>()
  const [search, setSearch] = useState('')
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null)
  const [gettingLocation, setGettingLocation] = useState(false)
  const [page, setPage] = useState(0)

  // Lấy danh sách cuisines cho filter
  const { data: cuisines = [] } = useQuery({
    queryKey: ['cuisines'],
    queryFn: fetchCuisines,
  })

  // Lấy 1 trang quán với filter. keepPreviousData: khi sang trang mới vẫn hiện trang cũ cho đến khi tải xong
  const { data, isLoading, isError } = useQuery({
    queryKey: ['restaurants', search, selectedCuisine, userLocation, page],
    queryFn: () => fetchRestaurants({
      keyword: search || undefined,
      cuisineId: selectedCuisine,
      lat: userLocation?.lat,
      lng: userLocation?.lng,
      page,
      size: PAGE_SIZE,
    }),
    placeholderData: keepPreviousData,
  })
  const restaurants = data?.items ?? []
  const totalItems = data?.totalItems ?? 0
  const totalPages = data?.totalPages ?? 0

  // Đổi bộ lọc thì quay về trang đầu
  const changeCuisine = (cuisineId: number | undefined) => {
    setSelectedCuisine(cuisineId)
    setPage(0)
  }

  // Xử lý tìm kiếm (nhấn Enter hoặc click Search)
  const handleSearch = () => {
    setSearch(keyword)
    setPage(0)
  }

  // Lấy vị trí người dùng
  const handleNearMe = () => {
    setGettingLocation(true)
    if (!navigator.geolocation) {
      alert('Trình duyệt không hỗ trợ định vị')
      setGettingLocation(false)
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setUserLocation({
          lat: position.coords.latitude,
          lng: position.coords.longitude
        })
        setPage(0)
        setGettingLocation(false)
      },
      (error) => {
        console.error('Lỗi lấy vị trí:', error)
        alert('Không thể lấy vị trí của bạn. Vui lòng cho phép truy cập vị trí.')
        setGettingLocation(false)
      }
    )
  }

  // Xóa filter vị trí
  const handleClearLocation = () => {
    setUserLocation(null)
    setPage(0)
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-6">

      {/* Tiêu đề */}
      <h1 className="text-2xl font-bold text-gray-900">Đặt đồ ăn ngon</h1>
      <p className="mt-1 text-gray-500">Tìm quán ngon gần bạn</p>

      {/* Search bar + Quán gần tôi */}
      <div className="mt-4 flex gap-2">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          placeholder="Tìm tên quán..."
          className="flex-1 rounded-lg border border-gray-300 px-4 py-2 text-sm outline-none focus:border-orange-400 focus:ring-1 focus:ring-orange-400"
        />
        <button
          onClick={handleSearch}
          className="rounded-lg bg-orange-500 px-5 py-2 text-sm font-medium text-white hover:bg-orange-600"
        >
          Tìm kiếm
        </button>
        {!userLocation ? (
          <button
            onClick={handleNearMe}
            disabled={gettingLocation}
            className="flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-wait disabled:opacity-50"
          >
            {gettingLocation ? '⏳' : '📍'} {gettingLocation ? 'Đang lấy...' : 'Gần tôi'}
          </button>
        ) : (
          <button
            onClick={handleClearLocation}
            className="flex items-center gap-1.5 rounded-lg border border-orange-500 bg-orange-50 px-4 py-2 text-sm font-medium text-orange-600 hover:bg-orange-100"
          >
            ✓ Đang xem gần đây ✕
          </button>
        )}
      </div>

      {/* Filter cuisines */}
      <div className="mt-4 flex flex-wrap gap-2">
        <button
          onClick={() => changeCuisine(undefined)}
          className={`rounded-full border px-4 py-1.5 text-sm font-medium transition ${
            selectedCuisine === undefined
              ? 'border-orange-500 bg-orange-500 text-white'
              : 'border-gray-300 bg-white text-gray-700 hover:border-orange-400'
          }`}
        >
          Tất cả
        </button>
        {cuisines.map((c) => (
          <button
            key={c.id}
            onClick={() => changeCuisine(c.id)}
            className={`rounded-full border px-4 py-1.5 text-sm font-medium transition ${
              selectedCuisine === c.id
                ? 'border-orange-500 bg-orange-500 text-white'
                : 'border-gray-300 bg-white text-gray-700 hover:border-orange-400'
            }`}
          >
            {c.name}
          </button>
        ))}
      </div>

      {/* Kết quả */}
      <div className="mt-6">
        {isLoading && (
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="h-64 animate-pulse rounded-xl bg-gray-200" />
            ))}
          </div>
        )}

        {isError && (
          <div className="rounded-lg bg-red-50 p-4 text-center text-red-600">
            Không thể tải danh sách quán. Vui lòng thử lại.
          </div>
        )}

        {!isLoading && !isError && restaurants.length === 0 && (
          <div className="py-16 text-center text-gray-500">
            <p className="text-4xl">🍽️</p>
            <p className="mt-2 font-medium">Không tìm thấy quán nào</p>
            <p className="text-sm">Thử tìm kiếm với từ khóa khác</p>
          </div>
        )}

        {!isLoading && restaurants.length > 0 && (
          <>
            <p className="mb-3 text-sm text-gray-500">
              Tìm thấy <span className="font-semibold text-gray-800">{totalItems}</span> quán
              {userLocation && ' (sắp xếp theo khoảng cách)'}
            </p>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {restaurants.map((r) => (
                <RestaurantCard key={r.id} restaurant={r} />
              ))}
            </div>

            {/* Phân trang: chỉ hiện khi có từ 2 trang trở lên */}
            {totalPages > 1 && (
              <div className="mt-6 flex items-center justify-center gap-3 text-sm">
                <button
                  onClick={() => setPage(page - 1)}
                  disabled={page === 0}
                  className="rounded-lg border border-gray-300 bg-white px-4 py-2 font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  ← Trang trước
                </button>
                <span className="text-gray-600">
                  Trang {page + 1} / {totalPages}
                </span>
                <button
                  onClick={() => setPage(page + 1)}
                  disabled={page + 1 >= totalPages}
                  className="rounded-lg border border-gray-300 bg-white px-4 py-2 font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-40"
                >
                  Trang sau →
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
