import { Route, Routes } from 'react-router-dom'
import AdminLayout from './layouts/AdminLayout'
import CustomerLayout from './layouts/CustomerLayout'
import MerchantLayout from './layouts/MerchantLayout'

// Khách hàng
import CustomerHome from './pages/customer/CustomerHome'

// Quản trị viên (Admin - Sầm Xuân Điệp)
import AdminOverviewPage from './pages/admin/AdminOverviewPage'
import AdminVoucherPage from './pages/admin/AdminVoucherPage'
import AdminMerchantApprovalPage from './pages/admin/AdminMerchantApprovalPage'
import AdminDriverApprovalPage from './pages/admin/AdminDriverApprovalPage'
import AdminSupportPage from './pages/admin/AdminSupportPage'

// Cửa hàng (Merchant - Sầm Xuân Điệp phụ trách các trang đơn realtime, voucher, thống kê)
import MerchantOverviewPage from './pages/merchant/MerchantOverviewPage'
import MerchantOrdersPage from './pages/merchant/MerchantOrdersPage'
import MerchantVouchersPage from './pages/merchant/MerchantVouchersPage'
import MerchantRevenuePage from './pages/merchant/MerchantRevenuePage'
import MerchantTopDishesPage from './pages/merchant/MerchantTopDishesPage'
import MerchantSupportPage from './pages/merchant/MerchantSupportPage'

export default function App() {
  return (
    <Routes>
      {/* Khu vực Khách hàng */}
      <Route element={<CustomerLayout />}>
        <Route index element={<CustomerHome />} />
      </Route>

      {/* Khu vực Dashboard Cửa hàng */}
      <Route path="merchant" element={<MerchantLayout />}>
        <Route index element={<MerchantOverviewPage />} />
        <Route path="orders" element={<MerchantOrdersPage />} />
        <Route path="vouchers" element={<MerchantVouchersPage />} />
        <Route path="revenue" element={<MerchantRevenuePage />} />
        <Route path="top-dishes" element={<MerchantTopDishesPage />} />
        <Route path="support" element={<MerchantSupportPage />} />
      </Route>

      {/* Khu vực Quản trị Admin */}
      <Route path="admin" element={<AdminLayout />}>
        <Route index element={<AdminOverviewPage />} />
        <Route path="vouchers" element={<AdminVoucherPage />} />
        <Route path="merchants-approval" element={<AdminMerchantApprovalPage />} />
        <Route path="drivers-approval" element={<AdminDriverApprovalPage />} />
        <Route path="support" element={<AdminSupportPage />} />
      </Route>
    </Routes>
  )
}
