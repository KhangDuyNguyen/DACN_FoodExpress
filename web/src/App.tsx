import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import AdminLayout from './layouts/AdminLayout'
import CustomerLayout from './layouts/CustomerLayout'
import MerchantLayout from './layouts/MerchantLayout'

// Khách hàng
import Cart from './pages/customer/Cart'
import Checkout from './pages/customer/Checkout'
import CustomerHome from './pages/customer/CustomerHome'
import Login from './pages/customer/Login'
import OrderSuccess from './pages/customer/OrderSuccess'
import Register from './pages/customer/Register'
import RestaurantDetail from './pages/customer/RestaurantDetail'
import RestaurantList from './pages/customer/RestaurantList'

// Quản trị viên (Admin - Sầm Xuân Điệp)
import AdminDriverApprovalPage from './pages/admin/AdminDriverApprovalPage'
import AdminMerchantApprovalPage from './pages/admin/AdminMerchantApprovalPage'
import AdminOverviewPage from './pages/admin/AdminOverviewPage'
import AdminSupportPage from './pages/admin/AdminSupportPage'
import AdminVoucherPage from './pages/admin/AdminVoucherPage'

// Cửa hàng (Merchant - Sầm Xuân Điệp phụ trách các trang đơn realtime, voucher, thống kê; Dưỡng phụ trách menu)
import MenuManagement from './pages/merchant/MenuManagement'
import MerchantOrdersPage from './pages/merchant/MerchantOrdersPage'
import MerchantOverviewPage from './pages/merchant/MerchantOverviewPage'
import MerchantRevenuePage from './pages/merchant/MerchantRevenuePage'
import MerchantSupportPage from './pages/merchant/MerchantSupportPage'
import MerchantTopDishesPage from './pages/merchant/MerchantTopDishesPage'
import MerchantVouchersPage from './pages/merchant/MerchantVouchersPage'

// Web gom 3 khu trong 1 project:
//   /          -> khach hang
//   /merchant  -> dashboard cua hang (chi MERCHANT, vi backend chi cho MERCHANT goi /api/merchant/**)
//   /admin     -> quan tri (chi ADMIN)
// Moi nguoi them route cua minh vao dung khu (xem README trong tung thu muc pages/).
export default function App() {
  return (
    <Routes>
      {/* Khu vực Khách hàng */}
      <Route element={<CustomerLayout />}>
        <Route index element={<CustomerHome />} />
        <Route path="login" element={<Login />} />
        <Route path="register" element={<Register />} />
        <Route path="restaurants" element={<RestaurantList />} />
        <Route path="restaurants/:id" element={<RestaurantDetail />} />
        {/* Giỏ hàng, thanh toán: chỉ khách hàng (Khang) */}
        <Route element={<ProtectedRoute roles={['CUSTOMER']} />}>
          <Route path="cart" element={<Cart />} />
          <Route path="checkout" element={<Checkout />} />
          <Route path="orders/:id/success" element={<OrderSuccess />} />
        </Route>
      </Route>

      {/* Điều hướng đăng nhập cũ của Merchant về trang đăng nhập chung */}
      <Route path="merchant/login" element={<Navigate to="/login" replace />} />

      {/* Khu vực Dashboard Cửa hàng (Được bảo vệ bởi JWT: Chỉ MERCHANT) */}
      <Route element={<ProtectedRoute roles={['MERCHANT']} />}>
        <Route path="merchant" element={<MerchantLayout />}>
          <Route index element={<MerchantOverviewPage />} />
          <Route path="orders" element={<MerchantOrdersPage />} />
          <Route path="menu" element={<MenuManagement />} />
          <Route path="vouchers" element={<MerchantVouchersPage />} />
          <Route path="revenue" element={<MerchantRevenuePage />} />
          <Route path="top-dishes" element={<MerchantTopDishesPage />} />
          <Route path="support" element={<MerchantSupportPage />} />
        </Route>
      </Route>

      {/* Khu vực Quản trị Admin (Được bảo vệ bởi JWT: Chỉ ADMIN) */}
      <Route element={<ProtectedRoute roles={['ADMIN']} />}>
        <Route path="admin" element={<AdminLayout />}>
          <Route index element={<AdminOverviewPage />} />
          <Route path="vouchers" element={<AdminVoucherPage />} />
          <Route path="merchants-approval" element={<AdminMerchantApprovalPage />} />
          <Route path="drivers-approval" element={<AdminDriverApprovalPage />} />
          <Route path="support" element={<AdminSupportPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
