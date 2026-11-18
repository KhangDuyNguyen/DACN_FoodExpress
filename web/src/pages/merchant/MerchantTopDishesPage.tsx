import React, { useState, useEffect } from 'react'
import {
  Card,
  Table,
  Typography,
  Space,
  Select,
  Radio,
  DatePicker,
  Button,
  Tag,
  Alert,
  Spin,
  Row,
  Col,
} from 'antd'
import {
  FireOutlined,
  ReloadOutlined,
  ShopOutlined,
  TrophyOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  Cell,
} from 'recharts'
import { useBranch } from './MerchantBranchContext'
import { fetchMyRestaurants, type MerchantRestaurantDTO } from '../../api/merchant'
import { fetchTopItemsReport, type TopSellingItem } from '../../api/reports'
import { getApiError } from '../../api/apiError'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

type DatePreset = '7days' | '30days' | 'custom'

const COLORS = [
  '#fa8c16',
  '#1890ff',
  '#52c41a',
  '#722ed1',
  '#eb2f96',
  '#13c2c2',
  '#fadb14',
  '#fa541c',
  '#2f54eb',
  '#a0d911',
]

function formatVND(amount: number): string {
  return `${(amount || 0).toLocaleString('vi-VN')} ₫`
}

export const MerchantTopDishesPage: React.FC = () => {
  const { selectedBranch, setSelectedBranch } = useBranch()

  const {
    data: restaurants = [],
    isLoading: loadingRestaurants,
    isError: errorRestaurants,
  } = useQuery<MerchantRestaurantDTO[]>({
    queryKey: ['merchant-restaurants'],
    queryFn: fetchMyRestaurants,
  })

  useEffect(() => {
    if (!selectedBranch && restaurants.length > 0) {
      setSelectedBranch(restaurants[0])
    }
  }, [selectedBranch, restaurants, setSelectedBranch])

  const [datePreset, setDatePreset] = useState<DatePreset>('30days')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(29, 'day'),
    dayjs(),
  ])

  const { from, to } = React.useMemo(() => {
    if (datePreset === '7days') {
      return {
        from: dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
        to: dayjs().format('YYYY-MM-DD'),
      }
    }
    if (datePreset === '30days') {
      return {
        from: dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
        to: dayjs().format('YYYY-MM-DD'),
      }
    }
    return {
      from: customRange[0].format('YYYY-MM-DD'),
      to: customRange[1].format('YYYY-MM-DD'),
    }
  }, [datePreset, customRange])

  const restaurantId = selectedBranch?.id

  const {
    data: topItemsData,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['merchant-top-items', restaurantId, from, to],
    queryFn: () =>
      fetchTopItemsReport({
        restaurantId: restaurantId!,
        from,
        to,
        limit: 20,
      }),
    enabled: !!restaurantId,
  })

  const items = topItemsData?.data || []

  const columns: ColumnsType<TopSellingItem> = [
    {
      title: 'Hạng',
      key: 'rank',
      width: 80,
      align: 'center',
      render: (_, __, index) => {
        if (index === 0) return <Tag color="gold"><TrophyOutlined /> #1</Tag>
        if (index === 1) return <Tag color="cyan"><TrophyOutlined /> #2</Tag>
        if (index === 2) return <Tag color="orange"><TrophyOutlined /> #3</Tag>
        return <Text type="secondary">#{index + 1}</Text>
      },
    },
    {
      title: 'Tên món ăn',
      dataIndex: 'itemName',
      key: 'itemName',
      render: (text) => <Text strong>{text}</Text>,
    },
    {
      title: 'Số lượng đã bán',
      dataIndex: 'quantitySold',
      key: 'quantitySold',
      align: 'right',
      sorter: (a, b) => a.quantitySold - b.quantitySold,
      render: (val: number) => (
        <Tag color="blue" style={{ fontSize: 13, fontWeight: 600 }}>
          {val.toLocaleString('vi-VN')} phần
        </Tag>
      ),
    },
    {
      title: 'Doanh thu đóng góp',
      dataIndex: 'revenue',
      key: 'revenue',
      align: 'right',
      sorter: (a, b) => a.revenue - b.revenue,
      render: (val: number) => (
        <Text strong style={{ color: '#cf1322' }}>
          {formatVND(val)}
        </Text>
      ),
    },
  ]

  const chartData = items.slice(0, 10).map((it) => ({
    name: it.itemName.length > 16 ? `${it.itemName.slice(0, 14)}...` : it.itemName,
    fullName: it.itemName,
    quantity: it.quantitySold,
    revenue: it.revenue,
  }))

  return (
    <div style={{ padding: '4px 0 24px' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
          marginBottom: 20,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0 }}>
            <FireOutlined style={{ color: '#fa541c', marginRight: 8 }} />
            Thống kê Món bán chạy
          </Title>
          <Text type="secondary">
            Báo cáo phân tích món ăn được yêu thích nhất và doanh thu đóng góp tại quán
          </Text>
        </div>

        <Space wrap>
          <Select
            style={{ minWidth: 220 }}
            loading={loadingRestaurants}
            value={selectedBranch?.id}
            placeholder="Chọn quán..."
            prefix={<ShopOutlined />}
            onChange={(val) => {
              const b = restaurants.find((r) => r.id === val)
              if (b) setSelectedBranch(b)
            }}
            options={restaurants.map((r) => ({
              value: r.id,
              label: `${r.name} (${r.address})`,
            }))}
          />

          <Radio.Group
            value={datePreset}
            onChange={(e) => setDatePreset(e.target.value)}
            optionType="button"
            buttonStyle="solid"
          >
            <Radio.Button value="7days">7 ngày qua</Radio.Button>
            <Radio.Button value="30days">30 ngày qua</Radio.Button>
            <Radio.Button value="custom">Tùy chọn</Radio.Button>
          </Radio.Group>

          {datePreset === 'custom' && (
            <RangePicker
              value={customRange}
              onChange={(dates) => {
                if (dates && dates[0] && dates[1]) {
                  setCustomRange([dates[0], dates[1]])
                }
              }}
              allowClear={false}
              format="DD/MM/YYYY"
            />
          )}

          <Button
            icon={<ReloadOutlined />}
            onClick={() => refetch()}
            loading={isLoading}
          >
            Làm mới
          </Button>
        </Space>
      </div>

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải báo cáo món bán chạy"
          description={getApiError(error).message}
          style={{ marginBottom: 16 }}
        />
      )}

      {errorRestaurants && (
        <Alert
          type="error"
          showIcon
          message="Lỗi tải danh sách quán"
          description="Vui lòng kiểm tra lại kết nối mạng hoặc thử lại sau."
          style={{ marginBottom: 16 }}
        />
      )}

      <Spin spinning={isLoading}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={12}>
            <Card
              title={
                <Space>
                  <FireOutlined style={{ color: '#fa541c' }} />
                  <span>Biểu đồ Top 10 món bán chạy nhất (Số lượng phần)</span>
                </Space>
              }
              variant="borderless"
            >
              <div style={{ width: '100%', height: 380 }}>
                {chartData.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '80px 0' }}>
                    <Text type="secondary">Chưa có dữ liệu món bán chạy trong khoảng thời gian này</Text>
                  </div>
                ) : (
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={chartData}
                      layout="vertical"
                      margin={{ top: 10, right: 30, left: 20, bottom: 10 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" horizontal vertical={false} />
                      <XAxis type="number" />
                      <YAxis
                        type="category"
                        dataKey="name"
                        width={120}
                        tick={{ fontSize: 12 }}
                      />
                      <RechartsTooltip
                        formatter={(val: number | string | readonly (number | string)[] | undefined) => [
                          `${Number(val || 0).toLocaleString('vi-VN')} phần`,
                          'Số lượng bán',
                        ]}
                        labelFormatter={(label) => {
                          const found = chartData.find((c) => c.name === label)
                          return found?.fullName || label
                        }}
                      />
                      <Bar dataKey="quantity" radius={[0, 6, 6, 0]}>
                        {chartData.map((_, idx) => (
                          <Cell key={`cell-${idx}`} fill={COLORS[idx % COLORS.length]} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </Card>
          </Col>

          <Col xs={24} lg={12}>
            <Card
              title={
                <Space>
                  <TrophyOutlined style={{ color: '#fa8c16' }} />
                  <span>Danh sách xếp hạng món ăn</span>
                </Space>
              }
              variant="borderless"
            >
              <Table
                rowKey="itemId"
                columns={columns}
                dataSource={items}
                pagination={{ pageSize: 8, showSizeChanger: false }}
                size="middle"
              />
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  )
}

export default MerchantTopDishesPage
