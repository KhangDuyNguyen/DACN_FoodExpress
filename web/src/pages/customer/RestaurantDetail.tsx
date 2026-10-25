import { useParams } from 'react-router-dom'

export default function RestaurantDetail() {
  const { id } = useParams<{ id: string }>()

  return (
    <div>
      <h1 className="text-2xl font-semibold">Chi tiết quán ăn</h1>
      <p className="mt-2 text-gray-600">
        Trang này sẽ hiển thị thông tin chi tiết của quán (ID: {id}).
      </p>
      <div className="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
        <p className="text-sm font-medium text-gray-700">📝 Ghi chú Week 3:</p>
        <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-gray-600">
          <li>Gọi API GET /api/restaurants/{`{id}`} để lấy thông tin quán</li>
          <li>Hiển thị tên, địa chỉ, cuisine, giờ mở cửa</li>
          <li>Hiển thị danh sách món ăn (menu items) của quán</li>
          <li>Mỗi món có: tên, mô tả, giá, hình ảnh</li>
          <li>Nút "Thêm vào giỏ hàng" cho mỗi món (chức năng cart ở Week sau)</li>
        </ul>
      </div>
    </div>
  )
}
