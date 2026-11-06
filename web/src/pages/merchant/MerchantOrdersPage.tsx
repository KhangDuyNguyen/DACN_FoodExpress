import React, { useState } from 'react'
import { Button, Card, Descriptions, Input, Modal, Select, Table, Tabs, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getApiError } from '../../api/apiError'
import {
  changeOrderStatus,
  fetchMerchantOrder,
  fetchMerchantOrders,
  fetchMyRestaurants,
} from '../../api/merchantOrders'
import { statusLabel, type OrderStatus, type OrderSummary } from '../../api/orders'

// Man hinh nhan don va doi trang thai (Khang, Module D tuan 3).
// TODO(diep): tuan 5 them WebSocket STOMP (/topic/restaurants/{id}/orders) de don moi hien ngay,
//             hien tai tu tai lai danh sach moi 10 giay.

const { Title, Text } = Typography

const PAGE_SIZE = 20

const STATUS_COLOR: Partial<Record<OrderStatus, string>> = {
  PLACED: 'orange',
  PREPARING: 'blue',
  DELIVERING: 'purple',
  DELIVERED: 'green',
  CANCELLED: 'default',
}

const STATUS_TABS: { key: OrderStatus | 'ALL'; label: string }[] = [
  { key: 'PLACED', label: 'Chờ xác nhận' },
  { key: 'PREPARING', label: 'Đang chuẩn bị' },
  { key: 'DELIVERING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Hoàn thành' },
  { key: 'CANCELLED', label: 'Đã hủy' },
  { key: 'ALL', label: 'Tất cả' },
]

// Buoc tiep theo cua tung trang thai, khop voi luat OrderStatusFlow o backend
const NEXT_ACTION: Partial<Record<OrderStatus, { status: OrderStatus; label: string }>> = {
  PLACED: { status: 'PREPARING', label: 'Nhận đơn' },
  PREPARING: { status: 'DELIVERING', label: 'Bắt đầu giao' },
  DELIVERING: { status: 'DELIVERED', label: 'Hoàn thành' },
}
const CAN_CANCEL: OrderStatus[] = ['PLACED', 'PREPARING']

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

// Loi 400 co the nam trong fieldErrors (vi du thieu ly do huy), lay cau dau tien de hien
function errorText(error: unknown) {
  const apiError = getApiError(error)
  return Object.values(apiError.fieldErrors)[0] ?? apiError.message
}

export const MerchantOrdersPage: React.FC = () => {
  const queryClient = useQueryClient()
  const [restaurantId, setRestaurantId] = useState<number | undefined>()
  const [tab, setTab] = useState<OrderStatus | 'ALL'>('PLACED')
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [cancelTarget, setCancelTarget] = useState<OrderSummary | null>(null)
  const [cancelReason, setCancelReason] = useState('')

  const { data: restaurants = [] } = useQuery({ queryKey: ['merchant-restaurants'], queryFn: fetchMyRestaurants })

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['merchant-orders', restaurantId, tab, page],
    queryFn: () =>
      fetchMerchantOrders({ restaurantId, status: tab === 'ALL' ? undefined : tab, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
    refetchInterval: 10_000,
  })

  const { data: detail, isLoading: loadingDetail } = useQuery({
    queryKey: ['merchant-order', detailId],
    queryFn: () => fetchMerchantOrder(detailId as number),
    enabled: detailId !== null,
  })

  const statusMutation = useMutation({
    mutationFn: (vars: { id: number; status: OrderStatus; reason?: string }) =>
      changeOrderStatus(vars.id, { status: vars.status, reason: vars.reason }),
    onSuccess: (order) => {
      message.success(`Đơn ${order.code}: ${statusLabel(order.status)}`)
      queryClient.invalidateQueries({ queryKey: ['merchant-orders'] })
      queryClient.setQueryData(['merchant-order', order.id], order)
      setCancelTarget(null)
      setCancelReason('')
    },
    onError: (error) => message.error(errorText(error)),
  })

  const columns: ColumnsType<OrderSummary> = [
    {
      title: 'Mã đơn',
      key: 'code',
      render: (_, o) => (
        <div>
          <Text strong>{o.code}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {new Date(o.createdAt).toLocaleString('vi-VN')}
            </Text>
          </div>
        </div>
      ),
    },
    { title: 'Chi nhánh', dataIndex: 'restaurantName', key: 'restaurantName' },
    {
      title: 'Giao đến',
      key: 'receiver',
      render: (_, o) => (
        <div>
          <Text>{o.receiverName}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {o.deliveryAddress}
            </Text>
          </div>
        </div>
      ),
    },
    { title: 'Số phần', dataIndex: 'itemCount', key: 'itemCount', align: 'center' },
    { title: 'Tổng tiền', key: 'total', align: 'right', render: (_, o) => vnd(o.totalAmount) },
    {
      title: 'Trạng thái',
      key: 'status',
      render: (_, o) => <Tag color={STATUS_COLOR[o.status]}>{statusLabel(o.status)}</Tag>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_, o) => {
        const next = NEXT_ACTION[o.status]
        return (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Button size="small" onClick={() => setDetailId(o.id)}>
              Chi tiết
            </Button>
            {next && (
              <Button
                size="small"
                type="primary"
                loading={statusMutation.isPending && statusMutation.variables?.id === o.id}
                onClick={() => statusMutation.mutate({ id: o.id, status: next.status })}
              >
                {next.label}
              </Button>
            )}
            {CAN_CANCEL.includes(o.status) && (
              <Button size="small" danger onClick={() => setCancelTarget(o)}>
                {o.status === 'PLACED' ? 'Từ chối' : 'Hủy'}
              </Button>
            )}
          </div>
        )
      },
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            Đơn hàng
          </Title>
          <Text type="secondary">Danh sách tự cập nhật mỗi 10 giây{isFetching ? ' · đang tải...' : ''}</Text>
        </div>
        <Select
          style={{ minWidth: 280 }}
          value={restaurantId ?? 'ALL'}
          onChange={(value: number | 'ALL') => {
            setRestaurantId(value === 'ALL' ? undefined : value)
            setPage(0)
          }}
          options={[
            { value: 'ALL', label: 'Tất cả chi nhánh' },
            ...restaurants.map((r) => ({ value: r.id, label: r.name })),
          ]}
        />
      </div>

      <Card style={{ marginTop: 16, borderRadius: 12 }}>
        <Tabs
          activeKey={tab}
          onChange={(key) => {
            setTab(key as OrderStatus | 'ALL')
            setPage(0)
          }}
          items={STATUS_TABS.map((t) => ({ key: t.key, label: t.label }))}
        />
        <Table
          rowKey="id"
          columns={columns}
          dataSource={data?.items ?? []}
          loading={isLoading}
          locale={{ emptyText: 'Không có đơn nào' }}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total: data?.totalItems ?? 0,
            onChange: (p) => setPage(p - 1),
          }}
          scroll={{ x: 900 }}
        />
      </Card>

      {/* Chi tiet don */}
      <Modal
        open={detailId !== null}
        title={detail ? `Đơn ${detail.code}` : 'Chi tiết đơn'}
        onCancel={() => setDetailId(null)}
        footer={null}
        width={640}
      >
        {loadingDetail || !detail ? (
          <Text type="secondary">Đang tải...</Text>
        ) : (
          <>
            <Descriptions column={1} size="small" bordered>
              <Descriptions.Item label="Trạng thái">
                <Tag color={STATUS_COLOR[detail.status]}>{statusLabel(detail.status)}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Khách đặt">{detail.customerName}</Descriptions.Item>
              <Descriptions.Item label="Người nhận">
                {detail.receiverName} · {detail.receiverPhone}
              </Descriptions.Item>
              <Descriptions.Item label="Địa chỉ">{detail.deliveryAddress}</Descriptions.Item>
              {detail.note && <Descriptions.Item label="Ghi chú">{detail.note}</Descriptions.Item>}
              {detail.cancelReason && <Descriptions.Item label="Lý do hủy">{detail.cancelReason}</Descriptions.Item>}
            </Descriptions>
            <Table
              style={{ marginTop: 16 }}
              size="small"
              rowKey={(_, index) => String(index)}
              pagination={false}
              dataSource={detail.items}
              columns={[
                { title: 'Món', key: 'name', render: (_, i) => (i.note ? `${i.name} (${i.note})` : i.name) },
                { title: 'SL', dataIndex: 'quantity', key: 'quantity', align: 'center' },
                { title: 'Thành tiền', key: 'lineTotal', align: 'right', render: (_, i) => vnd(i.lineTotal) },
              ]}
            />
            <div style={{ marginTop: 12, textAlign: 'right' }}>
              <div>Tạm tính: {vnd(detail.subtotal)}</div>
              <div>Phí giao: {vnd(detail.deliveryFee)}</div>
              <Text strong>Tổng thu khi giao (COD): {vnd(detail.totalAmount)}</Text>
            </div>
          </>
        )}
      </Modal>

      {/* Nhap ly do huy / tu choi */}
      <Modal
        open={cancelTarget !== null}
        title={cancelTarget ? `Hủy đơn ${cancelTarget.code}` : ''}
        okText="Xác nhận hủy"
        okButtonProps={{ danger: true, disabled: !cancelReason.trim(), loading: statusMutation.isPending }}
        cancelText="Đóng"
        onCancel={() => {
          setCancelTarget(null)
          setCancelReason('')
        }}
        onOk={() =>
          cancelTarget &&
          statusMutation.mutate({ id: cancelTarget.id, status: 'CANCELLED', reason: cancelReason.trim() })
        }
      >
        <Text>Lý do (khách sẽ thấy lý do này):</Text>
        <Input.TextArea
          style={{ marginTop: 8 }}
          rows={3}
          maxLength={255}
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
          placeholder="Ví dụ: hết món, quán quá tải"
        />
      </Modal>
    </div>
  )
}

export default MerchantOrdersPage
