import React from 'react'
import { Card, Col, Row, Statistic, Typography, Tag, Alert, Button, Table } from 'antd'
import {
  UserOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  DollarOutlined,
  ArrowRightOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

const { Title, Text, Paragraph } = Typography

export const AdminOverviewPage: React.FC = () => {
  const navigate = useNavigate()

  // Dữ liệu hiển thị mẫu khung giao diện cho Tuần 2
  const sampleActivities = [
    {
      key: '1',
      time: '19:40',
      action: 'Cửa hàng "Trà Sữa KOI Thé" gửi yêu cầu duyệt chi nhánh mới',
      type: 'MERCHANT',
      status: 'Chờ duyệt',
    },
    {
      key: '2',
      time: '18:15',
      action: 'Tài xế Nguyễn Văn A tải lên giấy phép lái xe',
      type: 'DRIVER',
      status: 'Chờ duyệt',
    },
    {
      key: '3',
      time: '16:00',
      action: 'Voucher "WELCOME2026" đạt 50 lượt sử dụng',
      type: 'VOUCHER',
      status: 'Hoạt động',
    },
  ]

  const columns = [
    {
      title: 'Thời gian',
      dataIndex: 'time',
      key: 'time',
      width: 100,
      render: (t: string) => <Text type="secondary">{t}</Text>,
    },
    {
      title: 'Hoạt động hệ thống',
      dataIndex: 'action',
      key: 'action',
      render: (text: string) => <Text strong>{text}</Text>,
    },
    {
      title: 'Phân loại',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (type: string) => {
        const color = type === 'MERCHANT' ? 'blue' : type === 'DRIVER' ? 'cyan' : 'gold'
        return <Tag color={color}>{type}</Tag>
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status: string) => (
        <Tag icon={<ClockCircleOutlined />} color="processing">
          {status}
        </Tag>
      ),
    },
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Banner thông báo tiến độ Tuần 2 */}
      <Alert
        message="Giao diện Quản trị viên (Admin) — Phiên bản nền tảng Tuần 2"
        description="Đã hoàn thành dựng bố cục chuẩn Ant Design với Sidebar điều hướng, Header quản trị, thống kê tổng quan và các trang chức năng. Dữ liệu bên dưới là minh họa khung bố cục; API nghiệp vụ sẽ được tích hợp từ Tuần 3."
        type="info"
        showIcon
        closable
      />

      {/* 4 Thẻ KPI Thống kê Tổng quan */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Tổng người dùng</Text>}
              value={1280}
              prefix={<UserOutlined style={{ color: '#1677ff', marginRight: 8 }} />}
              suffix={<Text type="secondary" style={{ fontSize: 12 }}>(+12 hôm nay)</Text>}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Quán ăn liên kết</Text>}
              value={86}
              prefix={<ShopOutlined style={{ color: '#52c41a', marginRight: 8 }} />}
              suffix={<Text type="secondary" style={{ fontSize: 12 }}>(3 chờ duyệt)</Text>}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Đơn hàng hôm nay</Text>}
              value={342}
              prefix={<ShoppingCartOutlined style={{ color: '#fa8c16', marginRight: 8 }} />}
              suffix={<Text type="secondary" style={{ fontSize: 12 }}>(98% thành công)</Text>}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Doanh thu sàn hôm nay</Text>}
              value={42580000}
              prefix={<DollarOutlined style={{ color: '#eb2f96', marginRight: 8 }} />}
              suffix="₫"
            />
          </Card>
        </Col>
      </Row>

      {/* Phím tắt thao tác nhanh */}
      <Card
        title={<Text strong>Lối tắt quản trị nhanh</Text>}
        bordered={false}
        style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={8}>
            <Card
              size="small"
              hoverable
              onClick={() => navigate('/admin/vouchers')}
              style={{ borderLeft: '4px solid #fa8c16', borderRadius: 8 }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Quản lý Voucher toàn sàn
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0', fontSize: 13 }}>
                Tạo mã giảm giá toàn sàn, kiểm soát ngân sách khuyến mãi
              </Paragraph>
              <Button type="link" size="small" style={{ padding: 0, marginTop: 8 }}>
                Đi tới trang <ArrowRightOutlined />
              </Button>
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card
              size="small"
              hoverable
              onClick={() => navigate('/admin/merchants-approval')}
              style={{ borderLeft: '4px solid #1677ff', borderRadius: 8 }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Duyệt chủ quán & Cửa hàng
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0', fontSize: 13 }}>
                Kiểm duyệt hồ sơ đăng ký quán mới trước khi mở bán
              </Paragraph>
              <Button type="link" size="small" style={{ padding: 0, marginTop: 8 }}>
                Đi tới trang <ArrowRightOutlined />
              </Button>
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card
              size="small"
              hoverable
              onClick={() => navigate('/admin/drivers-approval')}
              style={{ borderLeft: '4px solid #52c41a', borderRadius: 8 }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Duyệt hồ sơ tài xế
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0', fontSize: 13 }}>
                Xác thực bằng lái, CCCD, biển số xe để cấp quyền giao hàng
              </Paragraph>
              <Button type="link" size="small" style={{ padding: 0, marginTop: 8 }}>
                Đi tới trang <ArrowRightOutlined />
              </Button>
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Hoạt động gần đây */}
      <Card
        title={<Text strong>Yêu cầu chờ xử lý gần đây</Text>}
        bordered={false}
        style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
      >
        <Table
          dataSource={sampleActivities}
          columns={columns}
          pagination={false}
          size="middle"
        />
      </Card>
    </div>
  )
}

export default AdminOverviewPage
