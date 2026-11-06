import { Navigate } from 'react-router-dom'

// Trang chủ redirect thẳng sang danh sách quán
export default function CustomerHome() {
  return <Navigate to="/restaurants" replace />
}
