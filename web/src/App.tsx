import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './auth/ProtectedRoute'
import AdminLayout from './layouts/AdminLayout'
import CustomerLayout from './layouts/CustomerLayout'
import MerchantLayout from './layouts/MerchantLayout'

// Khách hàng
import CustomerHome from './pages/customer/CustomerHome'
import Login from './pages/customer/Login'
import Register from './pages/customer/Register'
import RestaurantDetail from './pages/customer/RestaurantDetail'
import RestaurantList from './pages/customer/RestaurantList'

// Quản trị viên (Admin - Sầm Xuân Điệp)
import AdminDriverApprovalPage from './pages/admin/AdminDriverApprovalPage'
import AdminMerchantApprovalPage from './pages/admin/AdminMerchantApprovalPage'
import AdminOverviewPage from './pages/admin/AdminOverviewPage'
import AdminSupportPage from './pages/admin/AdminSupportPage'
import AdminVoucherPage from './pages/admin/AdminVoucherPage'

// Cửa hàng (Merchant)
import MenuManagement from './pages/merchant/MenuManagement'
import MerchantOrdersPage from './pages/merchant/MerchantOrdersPage'
import MerchantOverviewPage from './pages/merchant/MerchantOverviewPage'
import MerchantRevenuePage from './pages/merchant/MerchantRevenuePage'
import MerchantSupportPage from './pages/merchant/MerchantSupportPage'
import MerchantTopDishesPage from './pages/merchant/MerchantTopDishesPage'
import MerchantVouchersPage from './pages/merchant/MerchantVouchersPage'
// Tuần 4 - Dưỡng
import BranchSelector from './pages/merchant/BranchSelector'
import RestaurantSettings from './pages/merchant/RestaurantSettings'
import { BranchProvider } from './pages/merchant/MerchantBranchContext'

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
      </Route>

      {/* Điều hướng đăng nhập cũ về trang đăng nhập chung */}
      <Route path="merchant/login" element={<Navigate to="/login" replace />} />

      {/* Khu vực Dashboard Cửa hàng (Chỉ MERCHANT) */}
      <Route element={<ProtectedRoute roles={['MERCHANT']} />}>
        {/* BranchProvider bọc toàn bộ merchant để chia sẻ chi nhánh đang chọn */}
        <Route element={<BranchProvider><MerchantLayout /></BranchProvider>}>
          <Route path="merchant" element={<MerchantOverviewPage />} />
          <Route path="merchant/branch" element={<BranchSelector />} />
          <Route path="merchant/orders" element={<MerchantOrdersPage />} />
          <Route path="merchant/menu" element={<MenuManagement />} />
          <Route path="merchant/settings" element={<RestaurantSettings />} />
          <Route path="merchant/vouchers" element={<MerchantVouchersPage />} />
          <Route path="merchant/revenue" element={<MerchantRevenuePage />} />
          <Route path="merchant/top-dishes" element={<MerchantTopDishesPage />} />
          <Route path="merchant/support" element={<MerchantSupportPage />} />
        </Route>
      </Route>

      {/* Khu vực Quản trị Admin (Chỉ ADMIN) */}
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
