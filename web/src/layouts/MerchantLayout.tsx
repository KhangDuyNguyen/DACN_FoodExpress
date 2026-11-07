import React, { useState } from 'react'
import {
  Layout,
  Menu,
  Button,
  Avatar,
  Dropdown,
  Space,
  Typography,
  Badge,
  Modal,
  Switch,
  message,
  type MenuProps,
} from 'antd'
import {
  DashboardOutlined,
  ShoppingOutlined,
  TagOutlined,
  LineChartOutlined,
  FireOutlined,
  CustomerServiceOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ShopOutlined,
  UserOutlined,
  BellOutlined,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const { Header, Sider, Content, Footer } = Layout
const { Text } = Typography

export const MerchantLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false)
  const [isOpenStore, setIsOpenStore] = useState(true)
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()

  // Xác định active menu key
  const getSelectedKey = () => {
    const path = location.pathname
    if (path.startsWith('/merchant/orders')) return '/merchant/orders'
    if (path.startsWith('/merchant/vouchers')) return '/merchant/vouchers'
    if (path.startsWith('/merchant/revenue')) return '/merchant/revenue'
    if (path.startsWith('/merchant/top-dishes')) return '/merchant/top-dishes'
    if (path.startsWith('/merchant/support')) return '/merchant/support'
    if (path.startsWith('/merchant/menu')) return '/merchant/menu'
    if (path.startsWith('/merchant/settings')) return '/merchant/settings'
    if (path.startsWith('/merchant/branch')) return '/merchant/branch'
    return '/merchant'
  }

  // Danh mục điều hướng Dashboard Cửa hàng
  const menuItems: MenuProps['items'] = [
    {
      key: '/merchant',
      icon: <DashboardOutlined />,
      label: 'Tổng quan quán',
      onClick: () => navigate('/merchant'),
    },
    {
      key: '/merchant/branch',
      icon: <BranchesOutlined />,
      label: 'Chọn chi nhánh',
      onClick: () => navigate('/merchant/branch'),
    },
    {
      key: '/merchant/menu',
      icon: <UnorderedListOutlined />,
      label: 'Quản lý Menu',
      onClick: () => navigate('/merchant/menu'),
    },
    {
      key: '/merchant/settings',
      icon: <SettingOutlined />,
      label: 'Thông tin quán',
      onClick: () => navigate('/merchant/settings'),
    },
    {
      key: '/merchant/orders',
      icon: <ShoppingOutlined />,
      label: (
        <Space>
          <span>Đơn hàng</span>
          <Badge count="Realtime" style={{ backgroundColor: '#52c41a', fontSize: 10 }} />
        </Space>
      ),
      onClick: () => navigate('/merchant/orders'),
    },
    {
      key: '/merchant/vouchers',
      icon: <TagOutlined />,
      label: 'Voucher của quán',
      onClick: () => navigate('/merchant/vouchers'),
    },
    {
      key: '/merchant/revenue',
      icon: <LineChartOutlined />,
      label: 'Thống kê doanh thu',
      onClick: () => navigate('/merchant/revenue'),
    },
    {
      key: '/merchant/top-dishes',
      icon: <FireOutlined />,
      label: 'Món bán chạy',
      onClick: () => navigate('/merchant/top-dishes'),
    },
    {
      key: '/merchant/support',
      icon: <CustomerServiceOutlined />,
      label: 'Hỗ trợ cửa hàng',
      onClick: () => navigate('/merchant/support'),
    },
  ]

  // Xử lý chuyển trạng thái mở cửa của quán
  const handleToggleStore = (checked: boolean) => {
    setIsOpenStore(checked)
    if (checked) {
      message.success('Quán đã bật trạng thái NHẬN ĐƠN HÀNG')
    } else {
      message.warning('Quán đã tạm ngưng nhận đơn mới')
    }
  }

  // Xử lý đăng xuất
  const handleLogout = () => {
    Modal.confirm({
      title: 'Xác nhận đăng xuất',
      content: 'Bạn có chắc chắn muốn rời khỏi trang quản lý cửa hàng?',
      okText: 'Đăng xuất',
      cancelText: 'Hủy',
      okType: 'danger',
      onOk: () => {
        // logout() xoa ca token lan thong tin user dang giu trong AuthProvider
        logout()
        message.success('Đã đăng xuất khỏi tài khoản Cửa hàng')
        navigate('/')
      },
    })
  }

  // Menu tài khoản quán
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'store-info',
      icon: <ShopOutlined />,
      label: 'Thông tin chi nhánh quán',
      onClick: () => message.info('Trang thông tin quán đang hoàn thiện'),
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Đăng xuất',
      danger: true,
      onClick: handleLogout,
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Sidebar bên trái */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        breakpoint="lg"
        onBreakpoint={(broken) => {
          if (broken) setCollapsed(true)
        }}
        width={250}
        style={{
          background: '#141414',
          boxShadow: '2px 0 8px rgba(0,0,0,0.15)',
          zIndex: 10,
        }}
      >
        {/* Logo Cửa hàng */}
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            padding: collapsed ? 0 : '0 20px',
            background: '#1f1f1f',
            gap: 10,
            overflow: 'hidden',
            cursor: 'pointer',
          }}
          onClick={() => navigate('/merchant')}
        >
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: 8,
              background: 'linear-gradient(135deg, #16a34a 0%, #52c41a 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 18,
              flexShrink: 0,
            }}
          >
            <ShopOutlined />
          </div>
          {!collapsed && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <Text strong style={{ color: '#fff', fontSize: 15, lineHeight: 1.2 }}>
                Cơm Tấm Sài Gòn
              </Text>
              <Text style={{ color: '#52c41a', fontSize: 11, fontWeight: 600 }}>
                MERCHANT DASHBOARD
              </Text>
            </div>
          )}
        </div>

        {/* Menu chính */}
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[getSelectedKey()]}
          items={menuItems}
          style={{ borderRight: 0, marginTop: 8, background: '#141414' }}
        />
      </Sider>

      {/* Vùng nội dung chính */}
      <Layout style={{ background: '#f8fafc' }}>
        {/* Header phía trên */}
        <Header
          style={{
            padding: '0 24px',
            background: '#ffffff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
            position: 'sticky',
            top: 0,
            zIndex: 9,
            height: 64,
          }}
        >
          {/* Nút thu gọn và Tên quán */}
          <Space size="middle">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{ fontSize: 16, width: 40, height: 40 }}
            />
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Text strong style={{ fontSize: 15, color: '#1e293b' }}>
                Quán: Cơm Tấm Sài Gòn (CN 1)
              </Text>
              <Badge
                status={isOpenStore ? 'success' : 'default'}
                text={
                  <Text style={{ fontSize: 12, color: isOpenStore ? '#16a34a' : '#64748b' }}>
                    {isOpenStore ? 'Đang mở cửa' : 'Đã đóng cửa'}
                  </Text>
                }
              />
            </div>
          </Space>

          {/* Phía phải: Switch nhận đơn, Chuông thông báo & Hồ sơ */}
          <Space size="middle">
            <Space align="center" style={{ display: 'flex', alignItems: 'center' }}>
              <Text style={{ fontSize: 12 }} type="secondary">
                Nhận đơn:
              </Text>
              <Switch
                checked={isOpenStore}
                onChange={handleToggleStore}
                checkedChildren="Mở"
                unCheckedChildren="Tắt"
                style={{ backgroundColor: isOpenStore ? '#52c41a' : undefined }}
              />
            </Space>

            <Badge count={2} size="small" offset={[-2, 2]}>
              <Button
                type="text"
                shape="circle"
                icon={<BellOutlined style={{ fontSize: 17, color: '#64748b' }} />}
                onClick={() => message.info('2 đơn hàng mới đang chờ quán xác nhận!')}
              />
            </Badge>

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow>
              <Space style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 6 }}>
                <Avatar
                  style={{ backgroundColor: '#16a34a', verticalAlign: 'middle' }}
                  icon={<UserOutlined />}
                />
                <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
                  <Text strong style={{ fontSize: 14 }}>
                    Chủ quán Điệp
                  </Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    Đối tác Cửa hàng
                  </Text>
                </div>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        {/* Nội dung trang */}
        <Content
          style={{
            margin: '24px',
            minHeight: 'calc(100vh - 64px - 70px - 48px)',
          }}
        >
          <Outlet />
        </Content>

        {/* Footer */}
        <Footer style={{ textAlign: 'center', color: '#94a3b8', fontSize: 13, padding: '16px 24px' }}>
          Food Delivery Express ©2026 — Kênh dành riêng cho Đối tác Cửa hàng | Phụ trách: Sầm Xuân Điệp
        </Footer>
      </Layout>
    </Layout>
  )
}

export default MerchantLayout
