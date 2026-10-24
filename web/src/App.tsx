import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import AdminLayout from './layouts/AdminLayout'
import CustomerLayout from './layouts/CustomerLayout'
import MerchantLayout from './layouts/MerchantLayout'
import AdminHome from './pages/admin/AdminHome'
import CustomerHome from './pages/customer/CustomerHome'
import Login from './pages/customer/Login'
import Register from './pages/customer/Register'
import RestaurantDetail from './pages/customer/RestaurantDetail'
import RestaurantList from './pages/customer/RestaurantList'
import MenuManagement from './pages/merchant/MenuManagement'
import MerchantHome from './pages/merchant/MerchantHome'

// Web gom 3 khu trong 1 project:
//   /          -> khach hang
//   /merchant  -> dashboard cua hang (chi MERCHANT, ADMIN)
//   /admin     -> quan tri (chi ADMIN)
// Moi nguoi them route cua minh vao dung khu (xem README trong tung thu muc pages/).
export default function App() {
  return (
    <Routes>
      <Route element={<CustomerLayout />}>
        <Route index element={<CustomerHome />} />
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />
        <Route path="restaurants" element={<RestaurantList />} />
        <Route path="restaurants/:id" element={<RestaurantDetail />} />
      </Route>
      {/* TODO(khang): bao Duong dung trang /login chung, file pages/merchant/Login.tsx khong con dung nua */}
      <Route path="merchant/login" element={<Navigate to="/login" replace />} />
      <Route element={<ProtectedRoute roles={['MERCHANT', 'ADMIN']} />}>
        <Route path="merchant" element={<MerchantLayout />}>
          <Route index element={<MerchantHome />} />
          <Route path="menu" element={<MenuManagement />} />
        </Route>
      </Route>
      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route path="admin" element={<AdminLayout />}>
          <Route index element={<AdminHome />} />
        </Route>
      </Route>
    </Routes>
  )
}
