import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Button, Card, DatePicker, Descriptions, Input, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { useState } from 'react'
import { getApiError } from '../../api/apiError'
import { fetchAdminOrder, fetchAdminOrders, type AdminOrderSummary } from '../../api/adminOrders'
import { ORDER_STATUS_LABEL, optionText, statusLabel, type OrderStatus } from '../../api/orders'

// Trang admin don toan san (Khang, tuan 6): loc, tim, xem chi tiet. Chi xem, khong doi trang thai.

const STATUS_COLOR: Partial<Record<OrderStatus, string>> = {
  PENDING_PAYMENT: 'gold',
  PLACED: 'orange',
  PREPARING: 'blue',
  READY_FOR_PICKUP: 'cyan',
  DELIVERING: 'purple',
  DELIVERED: 'green',
  CANCELLED: 'default',
}

const PAYMENT_LABEL = { COD: 'COD', VNPAY: 'VNPay', MOMO: 'MoMo' }
const PAGE_SIZE = 20

function vnd(amount: number) {
  return `${amount.toLocaleString('vi-VN')}đ`
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—'
}

export default function AdminOrdersPage() {
  const [status, setStatus] = useState<OrderStatus | undefined>()
  const [paymentMethod, setPaymentMethod] = useState<'COD' | 'VNPAY' | undefined>()
  const [keyword, setKeyword] = useState('')
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null)
  const [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null) // don dang xem chi tiet

  const from = range?.[0]?.format('YYYY-MM-DD')
  const to = range?.[1]?.format('YYYY-MM-DD')

  const orders = useQuery({
    queryKey: ['admin-orders', status, paymentMethod, keyword, from, to, page],
    queryFn: () =>
      fetchAdminOrders({ status, paymentMethod, q: keyword || undefined, from, to, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })

  const detail = useQuery({
    queryKey: ['admin-order', openId],
    queryFn: () => fetchAdminOrder(openId!),
    enabled: openId !== null,
  })

  // Doi bo loc thi quay ve trang dau
  function changeFilter(apply: () => void) {
    apply()
    setPage(0)
  }

  const columns: ColumnsType<AdminOrderSummary> = [
    { title: 'Mã đơn', dataIndex: 'code' },
    { title: 'Quán', dataIndex: 'restaurantName' },
    {
      title: 'Khách',
      key: 'customer',
      render: (_, o) => (
        <span>
          {o.customerName}
          <br />
          <Typography.Text type="secondary">{o.customerPhone}</Typography.Text>
        </span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      render: (s: OrderStatus) => <Tag color={STATUS_COLOR[s]}>{statusLabel(s)}</Tag>,
    },
    { title: 'Thanh toán', dataIndex: 'paymentMethod', render: (p: AdminOrderSummary['paymentMethod']) => PAYMENT_LABEL[p] },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', align: 'right', render: vnd },
    { title: 'Tài xế', dataIndex: 'driverName', render: (name: string | null) => name ?? '—' },
    { title: 'Đặt lúc', dataIndex: 'createdAt', render: formatTime },
    {
      title: '',
      key: 'action',
      render: (_, o) => (
        <Button size="small" onClick={() => setOpenId(o.id)}>
          Xem
        </Button>
      ),
    },
  ]

  const order = detail.data

  return (
    <Card>
      <Typography.Title level={4}>Đơn toàn sàn</Typography.Title>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 170 }}
          value={status}
          onChange={(value) => changeFilter(() => setStatus(value))}
          options={(Object.keys(ORDER_STATUS_LABEL) as OrderStatus[]).map((s) => ({ value: s, label: statusLabel(s) }))}
        />
        <Select
          allowClear
          placeholder="Thanh toán"
          style={{ width: 140 }}
          value={paymentMethod}
          onChange={(value) => changeFilter(() => setPaymentMethod(value))}
          options={[
            { value: 'COD', label: 'COD' },
            { value: 'VNPAY', label: 'VNPay' },
          ]}
        />
        <Input.Search
          allowClear
          placeholder="Mã đơn hoặc SĐT khách"
          style={{ width: 240 }}
          onSearch={(value) => changeFilter(() => setKeyword(value.trim()))}
        />
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          placeholder={['Từ ngày', 'Đến ngày']}
          value={range}
          onChange={(value) => changeFilter(() => setRange(value))}
        />
      </Space>
      {orders.isError && <Typography.Text type="danger">{getApiError(orders.error).message}</Typography.Text>}
      <Table
        rowKey="id"
        columns={columns}
        dataSource={orders.data?.items ?? []}
        loading={orders.isFetching}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total: orders.data?.totalItems ?? 0,
          showSizeChanger: false,
          onChange: (next) => setPage(next - 1),
        }}
      />

      <Modal
        open={openId !== null}
        title={order ? `Đơn ${order.code}` : 'Chi tiết đơn'}
        footer={null}
        width={720}
        onCancel={() => setOpenId(null)}
      >
        {detail.isLoading && <Typography.Text>Đang tải...</Typography.Text>}
        {detail.isError && <Typography.Text type="danger">{getApiError(detail.error).message}</Typography.Text>}
        {order && (
          <Space orientation="vertical" style={{ width: '100%' }}>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Trạng thái">
                <Tag color={STATUS_COLOR[order.status]}>{statusLabel(order.status)}</Tag>
                {order.cancelReason && <Typography.Text type="secondary">{order.cancelReason}</Typography.Text>}
              </Descriptions.Item>
              <Descriptions.Item label="Khách">{order.customerName}</Descriptions.Item>
              <Descriptions.Item label="Người nhận">
                {order.receiverName} · {order.receiverPhone}
              </Descriptions.Item>
              <Descriptions.Item label="Giao đến">{order.deliveryAddress}</Descriptions.Item>
              <Descriptions.Item label="Quán">{order.restaurant.name}</Descriptions.Item>
              <Descriptions.Item label="Tài xế">
                {order.driver ? `${order.driver.name} · ${order.driver.phone}` : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Thanh toán">{PAYMENT_LABEL[order.paymentMethod]}</Descriptions.Item>
              <Descriptions.Item label="Đặt lúc">{formatTime(order.createdAt)}</Descriptions.Item>
            </Descriptions>
            <Table
              rowKey={(_, index) => String(index)}
              size="small"
              pagination={false}
              dataSource={order.items}
              columns={[
                {
                  title: 'Món',
                  key: 'name',
                  render: (_, item) => (
                    <>
                      {item.name}
                      {/* Tuy chon (size, topping...) cua mon, ten va gia luc dat */}
                      {optionText(item.options) && (
                        <div>
                          <Typography.Text type="secondary">{optionText(item.options)}</Typography.Text>
                        </div>
                      )}
                    </>
                  ),
                },
                { title: 'SL', dataIndex: 'quantity', align: 'right' },
                { title: 'Thành tiền', dataIndex: 'lineTotal', align: 'right', render: vnd },
              ]}
            />
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Tạm tính">{vnd(order.subtotal)}</Descriptions.Item>
              <Descriptions.Item label="Phí giao">{vnd(order.deliveryFee)}</Descriptions.Item>
              {order.discountAmount > 0 && (
                <Descriptions.Item label="Giảm giá">−{vnd(order.discountAmount)}</Descriptions.Item>
              )}
              <Descriptions.Item label="Tổng cộng">
                <Typography.Text strong>{vnd(order.totalAmount)}</Typography.Text>
              </Descriptions.Item>
            </Descriptions>
            <Typography.Text strong>Lịch sử trạng thái</Typography.Text>
            {order.history.map((h, index) => (
              <Typography.Text key={index}>
                {formatTime(h.changedAt)} · {statusLabel(h.status)}
                {h.note && <Typography.Text type="secondary"> · {h.note}</Typography.Text>}
              </Typography.Text>
            ))}
          </Space>
        )}
      </Modal>
    </Card>
  )
}
