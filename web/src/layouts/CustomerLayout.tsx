import { Link, Outlet } from 'react-router-dom'

export default function CustomerLayout() {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-orange-600">
            Food Delivery
          </Link>
          <nav className="flex gap-4 text-sm text-gray-600">
            <Link to="/merchant">Cửa hàng</Link>
            <Link to="/admin">Admin</Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
