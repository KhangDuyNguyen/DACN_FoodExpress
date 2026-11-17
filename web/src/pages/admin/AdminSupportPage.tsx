import React, { useState, useMemo } from 'react'
import {
  Card,
  Table,
  Tag,
  Button,
  Space,
  Input,
  Select,
  Typography,
  Descriptions,
  message,
  Alert,
  Tabs,
  Drawer,
  Empty,
  Popconfirm,
} from 'antd'
import {
  CustomerServiceOutlined,
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  UserOutlined,
  ShoppingOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  fetchAdminTickets,
  updateAdminTicketStatus,
  type SupportTicket,
  type SupportTicketStatus,
} from '../../api/support'
import { getApiError } from '../../api/apiError'

const { Title, Text, Paragraph } = Typography
const { Option } = Select

const STATUS_CONFIG: Record<
  SupportTicketStatus,
  { label: string; color: string; icon: React.ReactNode }
> = {
  OPEN: {
    label: 'Chờ tiếp nhận',
    color: 'processing',
    icon: <ClockCircleOutlined />,
  },
  IN_PROGRESS: {
    label: 'Đang xử lý',
    color: 'warning',
    icon: <SyncOutlined spin />,
  },
  RESOLVED: {
    label: 'Đã giải quyết',
    color: 'success',
    icon: <CheckCircleOutlined />,
  },
  CLOSED: {
    label: 'Đã đóng',
    color: 'default',
    icon: <CloseCircleOutlined />,
  },
}

const STATUS_TABS: { key: string; label: string }[] = [
  { key: 'ALL', label: 'Tất cả' },
  { key: 'OPEN', label: 'Chờ tiếp nhận (OPEN)' },
  { key: 'IN_PROGRESS', label: 'Đang xử lý (IN_PROGRESS)' },
  { key: 'RESOLVED', label: 'Đã giải quyết (RESOLVED)' },
  { key: 'CLOSED', label: 'Đã đóng (CLOSED)' },
]

export const AdminSupportPage: React.FC = () => {
  const queryClient = useQueryClient()

  // Bộ lọc
  const [activeTab, setActiveTab] = useState<string>('ALL')
  const [keyword, setKeyword] = useState<string>('')
  const [searchParam, setSearchParam] = useState<string>('')
  const [page, setPage] = useState<number>(0)
  const [pageSize, setPageSize] = useState<number>(10)

  // Drawer chi tiết ticket
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null)
  const [isDrawerOpen, setIsDrawerOpen] = useState<boolean>(false)
  const [statusToUpdate, setStatusToUpdate] = useState<SupportTicketStatus | null>(null)

  // Query danh sách ticket
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin-tickets', activeTab, searchParam, page, pageSize],
    queryFn: () =>
      fetchAdminTickets({
        status: activeTab === 'ALL' ? undefined : (activeTab as SupportTicketStatus),
        keyword: searchParam.trim() || undefined,
        page,
        size: pageSize,
      }),
  })

  // Normalize dữ liệu từ PageResponse hoặc list
  const { tickets, totalItems } = useMemo(() => {
    if (!data) return { tickets: [], totalItems: 0 }
    const items: SupportTicket[] = Array.isArray(data)
      ? data
      : Array.isArray((data as any)?.items)
        ? (data as any).items
        : Array.isArray((data as any)?.content)
          ? (data as any).content
          : []

    const total: number = Array.isArray(data)
      ? data.length
      : typeof (data as any)?.totalItems === 'number'
        ? (data as any).totalItems
        : typeof (data as any)?.totalElements === 'number'
          ? (data as any).totalElements
          : items.length

    return { tickets: items, totalItems: total }
  }, [data])

  // Mutation cập nhật trạng thái ticket
  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: SupportTicketStatus }) =>
      updateAdminTicketStatus(id, status),
    onSuccess: (updated) => {
      message.success(`Đã cập nhật trạng thái ticket #${updated.id} thành ${STATUS_CONFIG[updated.status]?.label ?? updated.status}`)
      queryClient.invalidateQueries({ queryKey: ['admin-tickets'] })
      // Cập nhật lại state của Drawer
      setSelectedTicket((prev) => (prev && prev.id === updated.id ? { ...prev, ...updated } : prev))
      setStatusToUpdate(null)
    },
    onError: (err) => {
      message.error(getApiError(err).message)
    },
  })

  // Tìm kiếm
  const handleSearch = () => {
    setPage(0)
    setSearchParam(keyword)
  }

  // Mở Drawer xem chi tiết
  const handleOpenDetail = (ticket: SupportTicket) => {
    setSelectedTicket(ticket)
    setStatusToUpdate(ticket.status)
    setIsDrawerOpen(true)
  }

  // Đổi trạng thái trực tiếp
  const handleApplyStatusChange = (newStatus: SupportTicketStatus) => {
    if (!selectedTicket) return
    updateStatusMutation.mutate({
      id: selectedTicket.id,
      status: newStatus,
    })
  }

  // Render tag trạng thái
  const renderStatusTag = (status: SupportTicketStatus) => {
    const config = STATUS_CONFIG[status] || {
      label: status,
      color: 'default',
      icon: null,
    }
    return (
      <Tag color={config.color} icon={config.icon}>
        {config.label}
      </Tag>
    )
  }

  // Columns bảng tickets
  const columns: ColumnsType<SupportTicket> = [
    {
      title: 'Mã Ticket',
      dataIndex: 'id',
      key: 'id',
      width: 90,
      render: (id) => <Text code strong>#{id}</Text>,
    },
    {
      title: 'Tiêu đề & Nội dung tóm tắt',
      key: 'subject',
      width: 280,
      render: (_, record) => (
        <div>
          <Text strong style={{ fontSize: 14 }}>{record.subject}</Text>
          {record.description && (
            <Paragraph
              type="secondary"
              ellipsis={{ rows: 2 }}
              style={{ margin: '4px 0 0', fontSize: 12 }}
            >
              {record.description}
            </Paragraph>
          )}
        </div>
      ),
    },
    {
      title: 'Khách hàng',
      key: 'customer',
      width: 220,
      render: (_, record) => {
        const name = record.customerName || record.userName || 'Khách hàng'
        const email = record.customerEmail || record.userEmail
        return (
          <div>
            <Space orientation="horizontal" size={4}>
              <UserOutlined style={{ color: '#1677ff' }} />
              <Text strong>{name}</Text>
            </Space>
            {email && (
              <div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {email}
                </Text>
              </div>
            )}
            {(record.userId || record.user_id) && (
              <div>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  Mã KH: #{record.userId || record.user_id}
                </Text>
              </div>
            )}
          </div>
        )
      },
    },
    {
      title: 'Đơn hàng',
      key: 'order',
      width: 120,
      align: 'center',
      render: (_, record) => {
        const orderId = record.orderId ?? record.order_id
        return orderId ? (
          <Tag color="purple" icon={<ShoppingOutlined />}>
            Đơn #{orderId}
          </Tag>
        ) : (
          <Text type="secondary">—</Text>
        )
      },
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      align: 'center',
      render: (status: SupportTicketStatus) => renderStatusTag(status),
    },
    {
      title: 'Thời gian tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (_, record) => {
        const date = record.createdAt || record.created_at
        return date ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {new Date(date).toLocaleString('vi-VN')}
          </Text>
        ) : (
          '—'
        )
      },
    },
    {
      title: 'Giải quyết lúc',
      dataIndex: 'resolvedAt',
      key: 'resolvedAt',
      width: 150,
      render: (_, record) => {
        const date = record.resolvedAt || record.resolved_at
        return date ? (
          <Text style={{ fontSize: 12, color: '#16a34a' }}>
            {new Date(date).toLocaleString('vi-VN')}
          </Text>
        ) : (
          <Text type="secondary">—</Text>
        )
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 130,
      align: 'center',
      render: (_, record) => (
        <Button
          type="primary"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleOpenDetail(record)}
        >
          Xử lý
        </Button>
      ),
    },
  ]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Header */}
      <div>
        <Title level={3} style={{ marginBottom: 4 }}>
          <CustomerServiceOutlined style={{ marginRight: 8, color: '#1677ff' }} />
          Quản lý Yêu cầu Hỗ trợ (Customer Support Tickets)
        </Title>
        <Text type="secondary">
          Tiếp nhận, phân loại và cập nhật tiến độ xử lý khiếu nại, sự cố đơn hàng và giải đáp thắc mắc từ người dùng
        </Text>
      </div>

      {isError && (
        <Alert
          type="error"
          showIcon
          message="Không thể tải danh sách ticket hỗ trợ"
          description={getApiError(error).message}
          action={
            <Button size="small" danger onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      )}

      <Card bordered={false} style={{ borderRadius: 12, boxShadow: '0 1px 4px rgba(0,0,0,0.06)' }}>
        {/* Thanh tìm kiếm và bộ lọc */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            flexWrap: 'wrap',
            gap: 12,
            marginBottom: 16,
          }}
        >
          <Space wrap>
            <Input
              placeholder="Tìm theo tiêu đề, tên khách, email..."
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={handleSearch}
              style={{ width: 300 }}
              allowClear
            />
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              Tìm kiếm
            </Button>
          </Space>

          <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
            Làm mới danh sách
          </Button>
        </div>

        {/* Tab trạng thái */}
        <Tabs
          activeKey={activeTab}
          onChange={(key) => {
            setActiveTab(key)
            setPage(0)
          }}
          items={STATUS_TABS.map((t) => ({ key: t.key, label: t.label }))}
        />

        {/* Bảng dữ liệu */}
        <Table
          columns={columns}
          dataSource={tickets}
          rowKey="id"
          loading={isLoading}
          locale={{
            emptyText: (
              <Empty
                description="Không có yêu cầu hỗ trợ nào phù hợp với bộ lọc"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize,
            total: totalItems,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50'],
            showTotal: (total) => `Tổng cộng ${total} ticket`,
            onChange: (p, s) => {
              setPage(p - 1)
              setPageSize(s)
            },
          }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* DRAWER CHI TIẾT VÀ XỬ LÝ TICKET */}
      <Drawer
        title={
          <Space>
            <CustomerServiceOutlined style={{ color: '#1677ff' }} />
            <span>Chi tiết Ticket #{selectedTicket?.id}</span>
          </Space>
        }
        open={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        width={560}
      >
        {selectedTicket && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Mã Ticket">
                <Text code strong>#{selectedTicket.id}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Trạng thái hiện tại">
                {renderStatusTag(selectedTicket.status)}
              </Descriptions.Item>
              <Descriptions.Item label="Tiêu đề">
                <Text strong>{selectedTicket.subject}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Khách hàng">
                <div>
                  <Text strong>
                    {selectedTicket.customerName || selectedTicket.userName || 'Khách hàng'}
                  </Text>
                  {selectedTicket.customerEmail || selectedTicket.userEmail ? (
                    <div>
                      <Text type="secondary">
                        {selectedTicket.customerEmail || selectedTicket.userEmail}
                      </Text>
                    </div>
                  ) : null}
                  {(selectedTicket.userId || selectedTicket.user_id) && (
                    <div>
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        User ID: #{selectedTicket.userId || selectedTicket.user_id}
                      </Text>
                    </div>
                  )}
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="Mã đơn hàng liên quan">
                {selectedTicket.orderId || selectedTicket.order_id ? (
                  <Tag color="purple">#{selectedTicket.orderId || selectedTicket.order_id}</Tag>
                ) : (
                  <Text type="secondary">Không liên kết đơn hàng</Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian gửi">
                {selectedTicket.createdAt || selectedTicket.created_at
                  ? new Date(
                      (selectedTicket.createdAt || selectedTicket.created_at)!
                    ).toLocaleString('vi-VN')
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Thời gian giải quyết">
                {selectedTicket.resolvedAt || selectedTicket.resolved_at ? (
                  <Text style={{ color: '#16a34a' }}>
                    {new Date(
                      selectedTicket.resolvedAt || selectedTicket.resolved_at!
                    ).toLocaleString('vi-VN')}
                  </Text>
                ) : (
                  <Text type="secondary">Chưa giải quyết</Text>
                )}
              </Descriptions.Item>
            </Descriptions>

            {/* Khung nội dung phản ánh */}
            <Card
              size="small"
              title={<Text strong>Nội dung phản ánh / khiếu nại</Text>}
              style={{ backgroundColor: '#fcfcfc', borderRadius: 8 }}
            >
              <Paragraph style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                {selectedTicket.description || 'Không có mô tả chi tiết.'}
              </Paragraph>
            </Card>

            {/* Cập nhật trạng thái */}
            <Card
              size="small"
              title={<Text strong>Cập nhật trạng thái ticket</Text>}
              style={{ borderRadius: 8 }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Chuyển trạng thái xử lý cho ticket này để thông báo cho khách hàng:
                </Text>

                <Select
                  style={{ width: '100%' }}
                  value={statusToUpdate ?? selectedTicket.status}
                  onChange={(val) => setStatusToUpdate(val as SupportTicketStatus)}
                >
                  <Option value="OPEN">🔵 Chờ tiếp nhận (OPEN)</Option>
                  <Option value="IN_PROGRESS">🟡 Đang xử lý (IN_PROGRESS)</Option>
                  <Option value="RESOLVED">🟢 Đã giải quyết (RESOLVED)</Option>
                  <Option value="CLOSED">⚪ Đã đóng (CLOSED)</Option>
                </Select>

                <Button
                  type="primary"
                  loading={updateStatusMutation.isPending}
                  disabled={!statusToUpdate || statusToUpdate === selectedTicket.status}
                  onClick={() => statusToUpdate && handleApplyStatusChange(statusToUpdate)}
                >
                  Lưu thay đổi trạng thái
                </Button>

                {/* Các nút hành động nhanh */}
                <div style={{ marginTop: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>
                    Thao tác nhanh:
                  </Text>
                  <Space wrap>
                    {selectedTicket.status !== 'IN_PROGRESS' && (
                      <Button
                        size="small"
                        icon={<SyncOutlined />}
                        loading={updateStatusMutation.isPending}
                        onClick={() => handleApplyStatusChange('IN_PROGRESS')}
                      >
                        Bắt đầu xử lý
                      </Button>
                    )}
                    {selectedTicket.status !== 'RESOLVED' && (
                      <Popconfirm
                        title="Đánh dấu ticket đã được giải quyết?"
                        onConfirm={() => handleApplyStatusChange('RESOLVED')}
                        okText="Đồng ý"
                        cancelText="Hủy"
                      >
                        <Button
                          size="small"
                          type="primary"
                          style={{ backgroundColor: '#16a34a', borderColor: '#16a34a' }}
                          icon={<CheckCircleOutlined />}
                          loading={updateStatusMutation.isPending}
                        >
                          Giải quyết xong
                        </Button>
                      </Popconfirm>
                    )}
                    {selectedTicket.status !== 'CLOSED' && (
                      <Button
                        size="small"
                        icon={<CloseCircleOutlined />}
                        loading={updateStatusMutation.isPending}
                        onClick={() => handleApplyStatusChange('CLOSED')}
                      >
                        Đóng ticket
                      </Button>
                    )}
                  </Space>
                </div>
              </div>
            </Card>
          </div>
        )}
      </Drawer>
    </div>
  )
}

export default AdminSupportPage
