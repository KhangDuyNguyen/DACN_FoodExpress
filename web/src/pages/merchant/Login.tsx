export default function Login() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-100">
      <div className="w-full max-w-md rounded-lg bg-white p-8 shadow-md">
        <h1 className="text-2xl font-semibold text-center">Đăng nhập Merchant</h1>
        <p className="mt-2 text-center text-gray-600 text-sm">
          Dành cho chủ quán / nhân viên quản lý menu
        </p>
        <div className="mt-6 rounded-lg border border-gray-200 bg-gray-50 p-4">
          <p className="text-sm font-medium text-gray-700">📝 Ghi chú Week 3:</p>
          <ul className="mt-2 list-inside list-disc space-y-1 text-sm text-gray-600">
            <li>Form đăng nhập với username/email và password</li>
            <li>Gọi API POST /api/auth/login</li>
            <li>Lưu JWT token vào localStorage hoặc cookie</li>
            <li>Redirect đến /merchant sau khi login thành công</li>
            <li>Hiển thị lỗi nếu đăng nhập thất bại</li>
          </ul>
        </div>
      </div>
    </div>
  )
}
