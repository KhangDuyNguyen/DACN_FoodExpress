import { Layout } from 'antd'
import { Outlet } from 'react-router-dom'

// Khung dashboard cửa hàng (Ant Design). Dưỡng và Điệp thêm menu bên trái khi làm từng trang.
export default function MerchantLayout() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Header style={{ color: '#fff', fontWeight: 600 }}>Dashboard cửa hàng</Layout.Header>
      <Layout.Content style={{ padding: 24 }}>
        <Outlet />
      </Layout.Content>
    </Layout>
  )
}
