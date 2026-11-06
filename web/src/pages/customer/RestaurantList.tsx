import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchRestaurants, fetchCuisines } from '../../api/restaurants'
import RestaurantCard from '../../components/restaurant/RestaurantCard'

export default function RestaurantList() {
  const [keyword, setKeyword] = useState('')
  const [selectedCuisine, setSelectedCuisine] = useState<number | undefined>()
  const [search, setSearch] = useState('')
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null)
  const [gettingLocation, setGettingLocation] = useState(false)

  // Lấy danh sách cuisines cho filter
  const { data: cuisines = [] } = useQuery({
    queryKey: ['cuisines'],
    queryFn: fetchCuisines,
  })

  // Lấy danh sách quán với filter
  const { data: restaurants = [], isLoading, isError } = useQuery({
    queryKey: ['restaurants', search, selectedCuisine, userLocation],
    queryFn: () => fetchRestaurants({ 
      keyword: search || undefined, 
      cuisine: selectedCuisine,
      lat: userLocation?.lat,
      lng: userLocation?.lng
    }),
  })

  // Xử lý tìm kiếm (nhấn Enter hoặc click Search)
  const handleSearch = () => setSearch(keyword)

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
          onClick={() => setSelectedCuisine(undefined)}
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
            onClick={() => setSelectedCuisine(c.id)}
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
              Tìm thấy <span className="font-semibold text-gray-800">{restaurants.length}</span> quán
              {userLocation && ' (sắp xếp theo khoảng cách)'}
            </p>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {restaurants.map((r) => (
                <RestaurantCard key={r.id} restaurant={r} />
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
