import { Route, Routes } from 'react-router-dom'
import AdminLayout from './layouts/AdminLayout'
import CustomerLayout from './layouts/CustomerLayout'
import MerchantLayout from './layouts/MerchantLayout'
import AdminHome from './pages/admin/AdminHome'
import CustomerHome from './pages/customer/CustomerHome'
import MerchantHome from './pages/merchant/MerchantHome'

// Web gom 3 khu trong 1 project:
//   /          -> khach hang
//   /merchant  -> dashboard cua hang
//   /admin     -> quan tri
// Moi nguoi them route cua minh vao dung khu (xem README trong tung thu muc pages/).
export default function App() {
  return (
    <Routes>
      <Route element={<CustomerLayout />}>
        <Route index element={<CustomerHome />} />
      </Route>
      <Route path="merchant" element={<MerchantLayout />}>
        <Route index element={<MerchantHome />} />
      </Route>
      <Route path="admin" element={<AdminLayout />}>
        <Route index element={<AdminHome />} />
      </Route>
    </Routes>
  )
}
