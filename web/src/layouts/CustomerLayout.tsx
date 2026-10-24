import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export default function CustomerLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-orange-600">
            Food Delivery
          </Link>
          <nav className="flex items-center gap-4 text-sm text-gray-600">
            <Link to="/merchant">Cửa hàng</Link>
            <Link to="/admin">Admin</Link>
            {user ? (
              <>
                <span className="font-medium text-gray-800">{user.fullName}</span>
                <button onClick={handleLogout} className="rounded border px-3 py-1 hover:bg-gray-100">
                  Đăng xuất
                </button>
              </>
            ) : (
              <Link to="/login" className="rounded bg-orange-600 px-3 py-1 text-white hover:bg-orange-700">
                Đăng nhập
              </Link>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
