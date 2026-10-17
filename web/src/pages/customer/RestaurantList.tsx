export default function RestaurantList() {
  return (
    <div>
      <h1 className="text-2xl font-semibold">Danh sách quán ăn</h1>
      <p className="mt-2 text-gray-600">
        Trang này sẽ hiển thị danh sách các quán ăn với khả năng lọc theo cuisine.
      </p>
      <div className="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
        <p className="text-sm font-medium text-gray-700">📝 Ghi chú Week 3:</p>
        <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-gray-600">
          <li>Gọi API GET /api/restaurants để lấy danh sách quán</li>
          <li>Gọi API GET /api/cuisines để lấy danh sách loại món</li>
          <li>Hiển thị card cho mỗi quán (tên, địa chỉ, cuisine, giờ mở cửa)</li>
          <li>Thêm bộ lọc theo cuisine</li>
          <li>Thêm link đến trang chi tiết quán</li>
        </ul>
      </div>
    </div>
  )
}
