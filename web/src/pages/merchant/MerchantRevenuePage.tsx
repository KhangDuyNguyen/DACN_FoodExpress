import React, { useState, useEffect, useMemo } from 'react'
import {
  Card,
  Row,
  Col,
  Statistic,
  Typography,
  DatePicker,
  Radio,
  Select,
  Table,
  Spin,
  Empty,
  Alert,
  Button,
  Space,
  Tag,
  Segmented,
} from 'antd'
import {
  DollarOutlined,
  ShoppingOutlined,
  AccountBookOutlined,
  ReloadOutlined,
  ShopOutlined,
  FireOutlined,
  AreaChartOutlined,
  BarChartOutlined,
  CalendarOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  Legend,
} from 'recharts'
import { useBranch } from './MerchantBranchContext'
import { fetchMyRestaurants, type MerchantRestaurantDTO } from '../../api/merchant'
import {
  fetchRevenueReport,
  fetchTopItemsReport,
  type TopSellingItem,
} from '../../api/reports'
import { getApiError } from '../../api/apiError'

const { Title, Text } = Typography
const { RangePicker } = DatePicker

type DatePreset = '7days' | '30days' | 'custom'
type ChartType = 'area' | 'bar'

function formatVND(amount: number): string {
  return `${(amount || 0).toLocaleString('vi-VN')} ₫`
}

function formatYAxisVND(val: number): string {
  if (val >= 1_000_000_000) return `${(val / 1_000_000_000).toFixed(1)}Tỷ`
  if (val >= 1_000_000) return `${(val / 1_000_000).toFixed(1)}Tr`
  if (val >= 1_000) return `${(val / 1_000).toFixed(0)}k`
  return `${val}`
}

export const MerchantRevenuePage: React.FC = () => {
  const { selectedBranch, setSelectedBranch } = useBranch()

  // Danh sách chi nhánh
  const {
    data: restaurants = [],
    isLoading: loadingRestaurants,
    isError: errorRestaurants,
  } = useQuery<MerchantRestaurantDTO[]>({
    queryKey: ['merchant-restaurants'],
    queryFn: fetchMyRestaurants,
  })

  // Tự động gán chi nhánh đầu tiên nếu chưa chọn
  useEffect(() => {
    if (!selectedBranch && restaurants.length > 0) {
      setSelectedBranch(restaurants[0])
    }
  }, [selectedBranch, restaurants, setSelectedBranch])

  // Trạng thái ngày lọc: mặc định 30 ngày qua
  const [datePreset, setDatePreset] = useState<DatePreset>('30days')
  const [chartType, setChartType] = useState<ChartType>('area')
  const [customRange, setCustomRange] = useState<[Dayjs, Dayjs]>([
    dayjs().subtract(29, 'day'),
    dayjs(),
  ])

  // Tính chuỗi from và to theo preset hoặc custom range
  const { fromDate, toDate } = useMemo(() => {
    let from: Dayjs
    let to: Dayjs = dayjs()

    if (datePreset === '7days') {
      from = dayjs().subtract(6, 'day')
    } else if (datePreset === '30days') {
      from = dayjs().subtract(29, 'day')
    } else {
      from = customRange[0]
      to = customRange[1]
    }

    return {
      fromDate: from.format('YYYY-MM-DD'),
      toDate: to.format('YYYY-MM-DD'),
    }
  }, [datePreset, customRange])

  const restaurantId = selectedBranch?.id

  // 1. Query báo cáo doanh thu
  const {
    data: revenueReport,
    isLoading: loadingRevenue,
    isError: errorRevenue,
    error: revError,
    refetch: refetchRevenue,
  } = useQuery({
    queryKey: ['merchant-revenue-report', restaurantId, fromDate, toDate],
    queryFn: () =>
      fetchRevenueReport({
        restaurantId: restaurantId!,
        from: fromDate,
        to: toDate,
      }),
    enabled: !!restaurantId,
  })

  // 2. Query món bán chạy
  const {
    data: topItemsReport,
    isLoading: loadingTopItems,
    isError: errorTopItems,
    error: topError,
    refetch: refetchTopItems,
  } = useQuery({
    queryKey: ['merchant-top-items-report', restaurantId, fromDate, toDate],
    queryFn: () =>
      fetchTopItemsReport({
        restaurantId: restaurantId!,
        from: fromDate,
        to: toDate,
        limit: 10,
      }),
    enabled: !!restaurantId,
  })

  // Tính các chỉ số KPI
  const { totalRevenue, totalOrders, averageOrderValue } = useMemo(() => {
    const rev = revenueReport?.totalRevenue ?? 0
    let orders = revenueReport?.totalOrders
    if (orders === undefined && revenueReport?.data) {
      orders = revenueReport.data.reduce((sum, item) => sum + (item.orderCount || 0), 0)
    }
    const safeOrders = orders ?? 0
    const aov = safeOrders > 0 ? Math.round(rev / safeOrders) : 0

    return {
      totalRevenue: rev,
      totalOrders: safeOrders,
      averageOrderValue: aov,
    }
  }, [revenueReport])

  // Cấu hình bảng món bán chạy
  const topColumns: ColumnsType<TopSellingItem> = [
    {
      title: 'Hạng',
      key: 'rank',
      width: 80,
      align: 'center',
      render: (_, __, index) => {
        const rank = index + 1
        if (rank === 1) return <Tag color="gold" style={{ fontWeight: 600 }}>🥇 #1</Tag>
        if (rank === 2) return <Tag color="cyan" style={{ fontWeight: 600 }}>🥈 #2</Tag>
        if (rank === 3) return <Tag color="orange" style={{ fontWeight: 600 }}>🥉 #3</Tag>
        return <Text strong style={{ color: '#8c8c8c' }}>#{rank}</Text>
      },
    },
    {
      title: 'Tên món ăn',
      key: 'itemName',
      render: (_, record) => (
        <Space size={6}>
          <FireOutlined style={{ color: '#ea580c' }} />
          <Text strong>{record.itemName || record.name}</Text>
        </Space>
      ),
    },
    {
      title: 'Số lượng bán',
      key: 'quantitySold',
      width: 130,
      align: 'right',
      render: (_, record) => (
        <Tag color="blue" style={{ fontSize: 13, fontWeight: 600 }}>
          {(record.quantitySold ?? record.quantity ?? 0).toLocaleString('vi-VN')} phần
        </Tag>
      ),
    },
    {
      title: 'Doanh thu đóng góp',
      key: 'revenue',
      width: 170,
      align: 'right',
      render: (_, record) => (
        <Text strong style={{ color: '#16a34a' }}>
          {formatVND(record.revenue)}
        </Text>
      ),
    },
  ]

  // Xử lý đổi chi nhánh
  const handleBranchChange = (branchId: number) => {
    const branch = restaurants.find((r) => r.id === branchId)
    if (branch) setSelectedBranch(branch)
  }

  // Refetch toàn bộ
  const handleRefresh = () => {
    refetchRevenue()
    refetchTopItems()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Thanh tiêu đề và bộ lọc */}
      <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} md={10}>
            <Title level={3} style={{ margin: 0 }}>
              📊 Báo cáo Doanh thu & Thống kê
            </Title>
            <Text type="secondary">
              Xem chi tiết doanh số bán hàng, lượng đơn hợp lệ và phân tích món chạy nhất
            </Text>
          </Col>

          <Col xs={24} md={14}>
            <Space wrap style={{ width: '100%', justifyContent: 'flex-end' }}>
              {/* Chọn chi nhánh */}
              <Space>
                <ShopOutlined style={{ color: '#ea580c', fontSize: 16 }} />
                <Select
                  style={{ minWidth: 220 }}
                  value={selectedBranch?.id}
                  placeholder="Chọn chi nhánh"
                  onChange={handleBranchChange}
                  loading={loadingRestaurants}
                  options={restaurants.map((r) => ({
                    value: r.id,
                    label: `${r.name} (${r.address})`,
                  }))}
                />
              </Space>

              <Button icon={<ReloadOutlined />} onClick={handleRefresh}>
                Làm mới
              </Button>
            </Space>
          </Col>
        </Row>

        {/* Bộ lọc khoảng thời gian */}
        <Row justify="space-between" align="middle" style={{ marginTop: 16 }} gutter={[12, 12]}>
          <Col xs={24} sm={14}>
            <Space wrap>
              <CalendarOutlined style={{ color: '#1677ff' }} />
              <Radio.Group
                value={datePreset}
                onChange={(e) => setDatePreset(e.target.value)}
                buttonStyle="solid"
              >
                <Radio.Button value="7days">7 ngày qua</Radio.Button>
                <Radio.Button value="30days">30 ngày qua</Radio.Button>
                <Radio.Button value="custom">Tùy chọn ngày</Radio.Button>
              </Radio.Group>

              {datePreset === 'custom' && (
                <RangePicker
                  value={customRange}
                  onChange={(dates) => {
                    if (dates && dates[0] && dates[1]) {
                      setCustomRange([dates[0], dates[1]])
                    }
                  }}
                  format="DD/MM/YYYY"
                  allowClear={false}
                />
              )}
            </Space>
          </Col>

          <Col xs={24} sm={10} style={{ textAlign: 'right' }}>
            <Text type="secondary">
              Khoảng thời gian: <Text strong>{dayjs(fromDate).format('DD/MM/YYYY')}</Text> đến{' '}
              <Text strong>{dayjs(toDate).format('DD/MM/YYYY')}</Text>
            </Text>
          </Col>
        </Row>
      </Card>

      {/* Thông báo lỗi nếu có */}
      {(errorRestaurants || errorRevenue || errorTopItems) && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải đầy đủ báo cáo"
          description={
            (revError && getApiError(revError).message) ||
            (topError && getApiError(topError).message) ||
            'Vui lòng kiểm tra kết nối hệ thống hoặc thử lại sau.'
          }
          action={
            <Button size="small" danger onClick={handleRefresh}>
              Thử lại
            </Button>
          }
        />
      )}

      {/* Trường hợp chưa có quán nào */}
      {!loadingRestaurants && restaurants.length === 0 && (
        <Card bordered={false} style={{ borderRadius: 12 }}>
          <Empty
            description="Tài khoản của bạn chưa có chi nhánh quán nào hoạt động"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        </Card>
      )}

      {/* 3 Thẻ chỉ số KPI */}
      {selectedBranch && (
        <Row gutter={[16, 16]}>
          {/* Tổng doanh thu */}
          <Col xs={24} sm={8}>
            <Card
              bordered={false}
              style={{
                borderRadius: 12,
                boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
                borderTop: '4px solid #16a34a',
              }}
            >
              <Statistic
                title={<Text style={{ color: '#4b5563', fontSize: 14 }}>Tổng Doanh Thu</Text>}
                value={totalRevenue}
                loading={loadingRevenue}
                formatter={(val) => (
                  <span style={{ color: '#16a34a', fontWeight: 700 }}>
                    {formatVND(Number(val) || 0)}
                  </span>
                )}
                prefix={<DollarOutlined style={{ color: '#16a34a', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Doanh số từ các đơn giao thành công (DELIVERED)
                </Text>
              </div>
            </Card>
          </Col>

          {/* Tổng đơn hợp lệ */}
          <Col xs={24} sm={8}>
            <Card
              bordered={false}
              style={{
                borderRadius: 12,
                boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
                borderTop: '4px solid #ea580c',
              }}
            >
              <Statistic
                title={<Text style={{ color: '#4b5563', fontSize: 14 }}>Tổng Đơn Hợp Lệ</Text>}
                value={totalOrders}
                loading={loadingRevenue}
                formatter={(val) => (
                  <span style={{ color: '#ea580c', fontWeight: 700 }}>
                    {Number(val).toLocaleString('vi-VN')} đơn
                  </span>
                )}
                prefix={<ShoppingOutlined style={{ color: '#ea580c', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Tổng số đơn hoàn tất giao đến tay khách hàng
                </Text>
              </div>
            </Card>
          </Col>

          {/* Giá trị trung bình đơn (AOV) */}
          <Col xs={24} sm={8}>
            <Card
              bordered={false}
              style={{
                borderRadius: 12,
                boxShadow: '0 1px 4px rgba(0,0,0,0.06)',
                borderTop: '4px solid #2563eb',
              }}
            >
              <Statistic
                title={<Text style={{ color: '#4b5563', fontSize: 14 }}>Giá Trị Trung Bình / Đơn (AOV)</Text>}
                value={averageOrderValue}
                loading={loadingRevenue}
                formatter={(val) => (
                  <span style={{ color: '#2563eb', fontWeight: 700 }}>
                    {formatVND(Number(val) || 0)}
                  </span>
                )}
                prefix={<AccountBookOutlined style={{ color: '#2563eb', marginRight: 8 }} />}
              />
              <div style={{ marginTop: 8 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Doanh thu bình quân mỗi đơn hàng
                </Text>
              </div>
            </Card>
          </Col>
        </Row>
      )}

      {/* Biểu đồ doanh thu Recharts */}
      {selectedBranch && (
        <Card
          bordered={false}
          style={{ borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
          title={
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8 }}>
              <Space>
                <Title level={5} style={{ margin: 0 }}>
                  📈 Biểu đồ Doanh thu & Lượng đơn theo ngày
                </Title>
                <Tag color="blue">{selectedBranch.name}</Tag>
              </Space>

              <Segmented
                value={chartType}
                onChange={(val) => setChartType(val as ChartType)}
                options={[
                  {
                    label: 'Miền (Area)',
                    value: 'area',
                    icon: <AreaChartOutlined />,
                  },
                  {
                    label: 'Cột (Bar)',
                    value: 'bar',
                    icon: <BarChartOutlined />,
                  },
                ]}
              />
            </div>
          }
        >
          {loadingRevenue ? (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 320 }}>
              <Spin tip="Đang tải biểu đồ doanh thu..." size="large" />
            </div>
          ) : !revenueReport?.data || revenueReport.data.length === 0 ? (
            <Empty
              description="Không có dữ liệu doanh thu trong khoảng thời gian này"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              style={{ padding: '40px 0' }}
            />
          ) : (
            <div style={{ width: '100%', height: 350 }}>
              <ResponsiveContainer width="100%" height="100%">
                {chartType === 'area' ? (
                  <AreaChart
                    data={revenueReport.data}
                    margin={{ top: 10, right: 30, left: 15, bottom: 25 }}
                  >
                    <defs>
                      <linearGradient id="revenueColor" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#16a34a" stopOpacity={0.8} />
                        <stop offset="95%" stopColor="#16a34a" stopOpacity={0.05} />
                      </linearGradient>
                      <linearGradient id="orderColor" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#ea580c" stopOpacity={0.7} />
                        <stop offset="95%" stopColor="#ea580c" stopOpacity={0.05} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                    <XAxis
                      dataKey="date"
                      tickFormatter={(val: string) => dayjs(val).format('DD/MM')}
                      dy={10}
                      tick={{ fill: '#64748b', fontSize: 12 }}
                    />
                    <YAxis
                      yAxisId="left"
                      tickFormatter={formatYAxisVND}
                      tick={{ fill: '#64748b', fontSize: 12 }}
                      width={60}
                    />
                    <YAxis
                      yAxisId="right"
                      orientation="right"
                      tickFormatter={(val) => `${val}`}
                      tick={{ fill: '#ea580c', fontSize: 12 }}
                      width={40}
                    />
                    <RechartsTooltip
                      formatter={(value: any, name: any) => {
                        if (name === 'revenue' || name === 'Doanh thu') {
                          return [formatVND(Number(value) || 0), 'Doanh thu']
                        }
                        if (name === 'orderCount' || name === 'Số đơn hoàn thành') {
                          return [`${value} đơn`, 'Số đơn hoàn thành']
                        }
                        return [value, name]
                      }}
                      labelFormatter={(label) => `Ngày: ${dayjs(String(label)).format('DD/MM/YYYY')}`}
                      contentStyle={{
                        borderRadius: 8,
                        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                        border: 'none',
                      }}
                    />
                    <Legend
                      verticalAlign="top"
                      height={36}
                      formatter={(val) =>
                        val === 'revenue'
                          ? 'Doanh thu (₫)'
                          : val === 'orderCount'
                            ? 'Số đơn'
                            : val
                      }
                    />
                    <Area
                      yAxisId="left"
                      type="monotone"
                      dataKey="revenue"
                      name="Doanh thu"
                      stroke="#16a34a"
                      strokeWidth={2}
                      fillOpacity={1}
                      fill="url(#revenueColor)"
                    />
                    <Area
                      yAxisId="right"
                      type="monotone"
                      dataKey="orderCount"
                      name="Số đơn"
                      stroke="#ea580c"
                      strokeWidth={2}
                      fillOpacity={1}
                      fill="url(#orderColor)"
                    />
                  </AreaChart>
                ) : (
                  <BarChart
                    data={revenueReport.data}
                    margin={{ top: 10, right: 30, left: 15, bottom: 25 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                    <XAxis
                      dataKey="date"
                      tickFormatter={(val: string) => dayjs(val).format('DD/MM')}
                      dy={10}
                      tick={{ fill: '#64748b', fontSize: 12 }}
                    />
                    <YAxis
                      yAxisId="left"
                      tickFormatter={formatYAxisVND}
                      tick={{ fill: '#64748b', fontSize: 12 }}
                      width={60}
                    />
                    <YAxis
                      yAxisId="right"
                      orientation="right"
                      tickFormatter={(val) => `${val}`}
                      tick={{ fill: '#ea580c', fontSize: 12 }}
                      width={40}
                    />
                    <RechartsTooltip
                      formatter={(value: any, name: any) => {
                        if (name === 'revenue' || name === 'Doanh thu') {
                          return [formatVND(Number(value) || 0), 'Doanh thu']
                        }
                        if (name === 'orderCount' || name === 'Số đơn hoàn thành') {
                          return [`${value} đơn`, 'Số đơn hoàn thành']
                        }
                        return [value, name]
                      }}
                      labelFormatter={(label) => `Ngày: ${dayjs(String(label)).format('DD/MM/YYYY')}`}
                      contentStyle={{
                        borderRadius: 8,
                        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                        border: 'none',
                      }}
                    />
                    <Legend
                      verticalAlign="top"
                      height={36}
                      formatter={(val) =>
                        val === 'revenue'
                          ? 'Doanh thu (₫)'
                          : val === 'orderCount'
                            ? 'Số đơn'
                            : val
                      }
                    />
                    <Bar
                      yAxisId="left"
                      dataKey="revenue"
                      name="Doanh thu"
                      fill="#16a34a"
                      radius={[4, 4, 0, 0]}
                    />
                    <Bar
                      yAxisId="right"
                      dataKey="orderCount"
                      name="Số đơn"
                      fill="#ea580c"
                      radius={[4, 4, 0, 0]}
                    />
                  </BarChart>
                )}
              </ResponsiveContainer>
            </div>
          )}
        </Card>
      )}

      {/* Bảng Top món bán chạy */}
      {selectedBranch && (
        <Card
          bordered={false}
          style={{ borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}
          title={
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Space>
                <FireOutlined style={{ color: '#ea580c', fontSize: 18 }} />
                <Title level={5} style={{ margin: 0 }}>
                  Top 10 Món Ăn Bán Chạy Nhất
                </Title>
              </Space>
              <Text type="secondary" style={{ fontSize: 13 }}>
                Xếp hạng theo số lượng phần ăn bán ra
              </Text>
            </div>
          }
        >
          <Table
            rowKey={(r, idx) => r.itemId ?? r.id ?? r.menuItemId ?? `${idx}-${r.itemName}`}
            columns={topColumns}
            dataSource={topItemsReport?.data ?? []}
            loading={loadingTopItems}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  description="Chưa có dữ liệu món bán chạy trong khoảng thời gian này"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              ),
            }}
          />
        </Card>
      )}
    </div>
  )
}

export default MerchantRevenuePage
