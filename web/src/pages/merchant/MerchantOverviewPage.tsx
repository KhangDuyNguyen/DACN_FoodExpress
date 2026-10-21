import React from 'react'
import { Card, Col, Row, Statistic, Typography, Tag, Alert, Button, Space, Table, Badge } from 'antd'
import {
  ShoppingOutlined,
  DollarOutlined,
  TagOutlined,
  ArrowRightOutlined,
  FireOutlined,
} from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

const { Title, Text, Paragraph } = Typography

export const MerchantOverviewPage: React.FC = () => {
  const navigate = useNavigate()

  // Dữ liệu mẫu minh họa khung bố cục Tuần 2
  const sampleIncomingOrders = [
    {
      key: '1',
      code: '#ORD-20261021-01',
      time: '19:48',
      items: '2x Cơm sườn bì chả, 1x Canh rong biển',
      total: '125.000 ₫',
      status: 'Đang nấu',
    },
    {
      key: '2',
      code: '#ORD-20261021-02',
      time: '19:42',
      items: '1x Cơm tấm sườn ốp la, 1x Trà đá',
      total: '55.000 ₫',
      status: 'Chờ tài xế',
    },
    {
      key: '3',
      code: '#ORD-20261021-03',
      time: '19:30',
      items: '3x Cơm tấm đặc biệt',
      total: '210.000 ₫',
      status: 'Hoàn tất',
    },
  ]

  const columns = [
    {
      title: 'Mã đơn',
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => <Text strong code>{code}</Text>,
    },
    {
      title: 'Thời gian',
      dataIndex: 'time',
      key: 'time',
      render: (t: string) => <Text type="secondary">{t}</Text>,
    },
    {
      title: 'Món ăn',
      dataIndex: 'items',
      key: 'items',
    },
    {
      title: 'Tổng tiền',
      dataIndex: 'total',
      key: 'total',
      render: (val: string) => <Text strong style={{ color: '#fa541c' }}>{val}</Text>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      render: (st: string) => {
        const color = st === 'Đang nấu' ? 'gold' : st === 'Chờ tài xế' ? 'blue' : 'green'
        return <Tag color={color}>{st}</Tag>
      },
    },
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Banner lộ trình */}
      <Alert
        message="Dashboard Cửa hàng (Merchant Portal) — Nền tảng giao diện Tuần 2"
        description="Bố cục trang quản lý riêng biệt cho Chủ quán/Cửa hàng. Đã chuẩn bị sẵn cấu trúc menu và khung chứa để tích hợp luồng Đơn hàng Realtime (STOMP WebSocket ở Tuần 5), Voucher của quán (Tuần 3) và Thống kê Recharts (Tuần 6)."
        type="success"
        showIcon
        closable
      />

      {/* Thông tin cửa hàng tóm tắt */}
      <Card bordered={false} style={{ borderRadius: 10, background: 'linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%)' }}>
        <Row justify="space-between" align="middle">
          <Col>
            <Space align="center" size="middle">
              <Badge status="processing" color="#52c41a" />
              <div>
                <Title level={4} style={{ margin: 0, color: '#9a3412' }}>
                  Cơm Tấm Sài Gòn — Chi nhánh 1 (Quận 1)
                </Title>
                <Text style={{ color: '#c2410c', fontSize: 13 }}>
                  Mã quán: REST-001 | Giờ mở cửa: 07:00 - 22:00 | Trạng thái: <Tag color="success">Đang nhận đơn</Tag>
                </Text>
              </div>
            </Space>
          </Col>
          <Col>
            <Button type="primary" style={{ background: '#ea580c', borderColor: '#ea580c' }} onClick={() => navigate('/merchant/orders')}>
              Xem bảng đơn hàng realtime
            </Button>
          </Col>
        </Row>
      </Card>

      {/* 4 Thẻ chỉ số kinh doanh */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Đơn hàng hôm nay</Text>}
              value={48}
              prefix={<ShoppingOutlined style={{ color: '#ea580c', marginRight: 8 }} />}
              suffix={<Text type="secondary" style={{ fontSize: 12 }}>(2 đơn mới)</Text>}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Doanh thu hôm nay</Text>}
              value={3680000}
              prefix={<DollarOutlined style={{ color: '#16a34a', marginRight: 8 }} />}
              suffix="₫"
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Voucher quán đang chạy</Text>}
              value={2}
              prefix={<TagOutlined style={{ color: '#2563eb', marginRight: 8 }} />}
              suffix={<Text type="secondary" style={{ fontSize: 12 }}>(Giảm 10%, 20k)</Text>}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
            <Statistic
              title={<Text style={{ color: '#64748b' }}>Món bán chạy nhất</Text>}
              value="Sườn bì chả"
              prefix={<FireOutlined style={{ color: '#dc2626', marginRight: 8 }} />}
              valueStyle={{ fontSize: 18 }}
            />
          </Card>
        </Col>
      </Row>

      {/* Lối tắt quản lý nhanh */}
      <Card
        title={<Text strong>Khu vực quản lý nghiệp vụ</Text>}
        bordered={false}
        style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
      >
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={8}>
            <Card
              size="small"
              hoverable
              onClick={() => navigate('/merchant/orders')}
              style={{ borderLeft: '4px solid #ea580c', borderRadius: 8 }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Quản lý Đơn hàng
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0', fontSize: 13 }}>
                Nhận đơn mới, từ chối, chuyển trạng thái chế biến (sẵn sàng kết nối WebSocket Tuần 5)
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
              onClick={() => navigate('/merchant/vouchers')}
              style={{ borderLeft: '4px solid #2563eb', borderRadius: 8 }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Voucher riêng của quán
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0', fontSize: 13 }}>
                Tạo mã ưu đãi dành riêng cho khách hàng đặt món tại quán
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
              onClick={() => navigate('/merchant/revenue')}
              style={{ borderLeft: '4px solid #16a34a', borderRadius: 8 }}
            >
              <Title level={5} style={{ margin: 0 }}>
                Báo cáo Doanh thu
              </Title>
              <Paragraph type="secondary" style={{ margin: '6px 0 0', fontSize: 13 }}>
                Thống kê theo ngày/tuần và dự báo doanh thu 7 ngày tới
              </Paragraph>
              <Button type="link" size="small" style={{ padding: 0, marginTop: 8 }}>
                Đi tới trang <ArrowRightOutlined />
              </Button>
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Đơn hàng gần nhất */}
      <Card
        title={<Text strong>Đơn hàng gần đây của quán</Text>}
        bordered={false}
        style={{ borderRadius: 10, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
      >
        <Table
          dataSource={sampleIncomingOrders}
          columns={columns}
          pagination={false}
          size="middle"
        />
      </Card>
    </div>
  )
}

export default MerchantOverviewPage
