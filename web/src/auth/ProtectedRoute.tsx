import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/auth'
import Forbidden from './Forbidden'
import { useAuth } from './useAuth'

interface Props {
  // Bo trong = chi can dang nhap, vai tro nao cung duoc
  roles?: Role[]
  children?: ReactNode
}

// Chan trang can dang nhap. Dung boc route: <Route element={<ProtectedRoute roles={['ADMIN']} />}>...</Route>
// Luu y: day chi la an giao dien, backend van tu kiem tra quyen o moi API.
export default function ProtectedRoute({ roles, children }: Props) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <p className="p-6 text-center text-gray-500">Đang tải...</p>
  }
  if (!user) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  if (roles && !roles.includes(user.role)) {
    return <Forbidden />
  }
  return children ?? <Outlet />
}
