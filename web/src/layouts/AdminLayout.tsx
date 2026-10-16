import { Layout } from 'antd'
import { Outlet } from 'react-router-dom'

// Khung trang quản trị (Ant Design). Khang và Điệp thêm menu bên trái khi làm từng trang.
export default function AdminLayout() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Header style={{ color: '#fff', fontWeight: 600 }}>Quản trị hệ thống</Layout.Header>
      <Layout.Content style={{ padding: 24 }}>
        <Outlet />
      </Layout.Content>
    </Layout>
  )
}
