import { Link } from 'react-router-dom'

export default function Forbidden() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
      <h1 className="text-2xl font-semibold text-gray-800">Không có quyền truy cập</h1>
      <p className="text-gray-600">Tài khoản của bạn không được phép xem trang này.</p>
      <Link to="/" className="text-orange-600 hover:underline">
        Về trang chủ
      </Link>
    </div>
  )
}
