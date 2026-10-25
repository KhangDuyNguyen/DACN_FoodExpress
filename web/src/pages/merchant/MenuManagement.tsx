export default function MenuManagement() {
  return (
    <div>
      <h1 className="text-2xl font-semibold">Quản lý Menu</h1>
      <p className="mt-2 text-gray-600">
        Trang này cho phép merchant quản lý các món ăn trong menu của quán.
      </p>
      <div className="mt-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
        <p className="text-sm font-medium text-gray-700">📝 Ghi chú Week 3:</p>
        <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-gray-600">
          <li>Hiển thị danh sách các món ăn của quán (GET /api/menu-items)</li>
          <li>Chức năng thêm món mới (POST /api/menu-items)</li>
          <li>Chức năng sửa món (PUT /api/menu-items/{`{id}`})</li>
          <li>Chức năng xóa món (DELETE /api/menu-items/{`{id}`})</li>
          <li>Form nhập: tên món, mô tả, giá, hình ảnh URL, availability</li>
          <li>Cần kiểm tra authentication (merchant phải đăng nhập)</li>
        </ul>
      </div>
    </div>
  )
}
