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
  message,
  type MenuProps,
} from 'antd'
import {
  DashboardOutlined,
  TagOutlined,
  ShopOutlined,
  CarOutlined,
  CustomerServiceOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  BellOutlined,
  ThunderboltFilled,
} from '@ant-design/icons'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

const { Header, Sider, Content, Footer } = Layout
const { Text } = Typography

export const AdminLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const navigate = useNavigate()

  // Xác định active menu key theo đường dẫn hiện tại
  const getSelectedKey = () => {
    const path = location.pathname
    if (path.startsWith('/admin/vouchers')) return '/admin/vouchers'
    if (path.startsWith('/admin/merchants-approval')) return '/admin/merchants-approval'
    if (path.startsWith('/admin/drivers-approval')) return '/admin/drivers-approval'
    if (path.startsWith('/admin/support')) return '/admin/support'
    return '/admin'
  }

  // Danh mục điều hướng Quản trị viên
  const menuItems: MenuProps['items'] = [
    {
      key: '/admin',
      icon: <DashboardOutlined />,
      label: 'Tổng quan',
      onClick: () => navigate('/admin'),
    },
    {
      key: '/admin/vouchers',
      icon: <TagOutlined />,
      label: 'Quản lý voucher',
      onClick: () => navigate('/admin/vouchers'),
    },
    {
      key: '/admin/merchants-approval',
      icon: <ShopOutlined />,
      label: 'Duyệt chủ quán & quán',
      onClick: () => navigate('/admin/merchants-approval'),
    },
    {
      key: '/admin/drivers-approval',
      icon: <CarOutlined />,
      label: 'Duyệt tài xế',
      onClick: () => navigate('/admin/drivers-approval'),
    },
    {
      key: '/admin/support',
      icon: <CustomerServiceOutlined />,
      label: 'Hỗ trợ khách hàng',
      onClick: () => navigate('/admin/support'),
    },
  ]

  // Xử lý đăng xuất
  const handleLogout = () => {
    Modal.confirm({
      title: 'Xác nhận đăng xuất',
      content: 'Bạn có chắc chắn muốn đăng xuất khỏi tài khoản Quản trị viên?',
      okText: 'Đăng xuất',
      cancelText: 'Hủy',
      okType: 'danger',
      onOk: () => {
        localStorage.removeItem('token')
        message.success('Đã đăng xuất thành công')
        navigate('/')
      },
    })
  }

  // Menu hồ sơ admin
  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: 'Thông tin tài khoản',
      onClick: () => message.info('Trang thông tin tài khoản đang hoàn thiện'),
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
          background: '#001529',
          boxShadow: '2px 0 8px rgba(0,21,41,0.15)',
          zIndex: 10,
        }}
      >
        {/* Logo / Tên thương hiệu */}
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            padding: collapsed ? 0 : '0 20px',
            background: '#002140',
            gap: 10,
            overflow: 'hidden',
            cursor: 'pointer',
          }}
          onClick={() => navigate('/admin')}
        >
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: 8,
              background: 'linear-gradient(135deg, #fa541c 0%, #fa8c16 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 18,
              flexShrink: 0,
            }}
          >
            <ThunderboltFilled />
          </div>
          {!collapsed && (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <Text strong style={{ color: '#fff', fontSize: 16, lineHeight: 1.2 }}>
                FoodExpress
              </Text>
              <Text style={{ color: '#fa8c16', fontSize: 11, fontWeight: 600 }}>
                ADMIN PORTAL
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
          style={{ borderRight: 0, marginTop: 8 }}
        />
      </Sider>

      {/* Khu vực chính */}
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
          {/* Nút thu gọn Sidebar */}
          <Space size="middle">
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{ fontSize: 16, width: 40, height: 40 }}
            />
            <Text strong style={{ fontSize: 16, color: '#1e293b' }}>
              Hệ thống Quản trị Trung tâm
            </Text>
          </Space>

          {/* Góc phải Header: Thông báo & Tài khoản Admin */}
          <Space size="large">
            <Badge count={3} size="small" offset={[-2, 2]}>
              <Button
                type="text"
                shape="circle"
                icon={<BellOutlined style={{ fontSize: 17, color: '#64748b' }} />}
                onClick={() => message.info('3 thông báo mới: 2 yêu cầu duyệt quán, 1 tài xế mới')}
              />
            </Badge>

            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" arrow>
              <Space style={{ cursor: 'pointer', padding: '4px 8px', borderRadius: 6 }}>
                <Avatar
                  style={{ backgroundColor: '#fa8c16', verticalAlign: 'middle' }}
                  icon={<UserOutlined />}
                />
                <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
                  <Text strong style={{ fontSize: 14 }}>
                    Sầm Xuân Điệp
                  </Text>
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    Quản trị viên
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
          Food Delivery Express ©2026 — Đồ án chuyên ngành HUTECH | Thành viên phụ trách: Sầm Xuân Điệp (2380614940)
        </Footer>
      </Layout>
    </Layout>
  )
}

export default AdminLayout
