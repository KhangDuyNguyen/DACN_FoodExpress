import React, { useEffect, useRef, useState } from 'react'
import {
  Button,
  Card,
  Descriptions,
  Input,
  Modal,
  Select,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
  notification,
} from 'antd'
import {
  CheckCircleOutlined,
  SyncOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { getApiError } from '../../api/apiError'
import {
  changeOrderStatus,
  fetchMerchantOrder,
  fetchMerchantOrders,
  fetchMyRestaurants,
} from '../../api/merchantOrders'
import { statusLabel, type OrderStatus, type OrderSummary } from '../../api/orders'

// Man hinh nhan don va doi trang thai voi cap nhat thoi gian thuc qua WebSocket STOMP
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

function getWsUrl(): string {
  if (import.meta.env.VITE_WS_URL) {
    return import.meta.env.VITE_WS_URL
  }
  if (
    import.meta.env.VITE_API_URL &&
    typeof import.meta.env.VITE_API_URL === 'string' &&
    import.meta.env.VITE_API_URL.startsWith('http')
  ) {
    try {
      const apiUrl = new URL(import.meta.env.VITE_API_URL)
      const wsProto = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'
      return `${wsProto}//${apiUrl.host}/ws`
    } catch {
      // Fallback
    }
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

type ConnectionStatus = 'Online' | 'Connecting' | 'Offline'

export const MerchantOrdersPage: React.FC = () => {
  const queryClient = useQueryClient()
  const [restaurantId, setRestaurantId] = useState<number | undefined>()
  const [tab, setTab] = useState<OrderStatus | 'ALL'>('PLACED')
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [cancelTarget, setCancelTarget] = useState<OrderSummary | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('Connecting')

  const clientRef = useRef<Client | null>(null)
  const subscriptionsRef = useRef<StompSubscription[]>([])
  const processedOrderIdsRef = useRef<Set<string | number>>(new Set())

  const restaurantIdRef = useRef<number | undefined>(restaurantId)
  const queryClientRef = useRef(queryClient)
  const { data: restaurants = [] } = useQuery({ queryKey: ['merchant-restaurants'], queryFn: fetchMyRestaurants })
  const restaurantsRef = useRef(restaurants)

  useEffect(() => {
    restaurantIdRef.current = restaurantId
  }, [restaurantId])

  useEffect(() => {
    queryClientRef.current = queryClient
  }, [queryClient])

  useEffect(() => {
    restaurantsRef.current = restaurants
  }, [restaurants])

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['merchant-orders', restaurantId, tab, page],
    queryFn: () =>
      fetchMerchantOrders({ restaurantId, status: tab === 'ALL' ? undefined : tab, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
    refetchInterval: connectionStatus === 'Online' ? false : 15_000,
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

  // Xu ly su kien don hang moi tu WebSocket STOMP
  const handleIncomingOrder = (msg: IMessage) => {
    try {
      let orderData: any
      try {
        orderData = JSON.parse(msg.body)
      } catch {
        orderData = { code: msg.body }
      }

      // Tranh trung lap bang bo nho tam processedOrderIds
      const orderKey = orderData?.orderId ?? orderData?.id ?? orderData?.code
      if (orderKey) {
        if (processedOrderIdsRef.current.has(orderKey)) {
          return
        }
        processedOrderIdsRef.current.add(orderKey)
        if (processedOrderIdsRef.current.size > 200) {
          const oldest = processedOrderIdsRef.current.values().next().value
          if (oldest !== undefined) processedOrderIdsRef.current.delete(oldest)
        }
      }

      const orderCode = orderData?.code ? `Đơn hàng ${orderData.code}` : orderKey ? `Đơn hàng #${orderKey}` : 'Đơn hàng mới'
      const restaurantName = orderData?.restaurantName ? ` tại ${orderData.restaurantName}` : ''
      const amountStr = orderData?.totalAmount ? ` (${vnd(Number(orderData.totalAmount))})` : ''

      notification.info({
        message: '🔔 Có đơn hàng mới!',
        description: `${orderCode}${restaurantName}${amountStr} vừa được đặt thành công. Danh sách đơn đã được cập nhật.`,
        placement: 'topRight',
        duration: 6,
        btn: (
          <Button
            type="primary"
            size="small"
            onClick={() => {
              setTab('PLACED')
              setPage(0)
              queryClientRef.current.invalidateQueries({ queryKey: ['merchant-orders'] })
              notification.destroy()
            }}
          >
            Xem ngay
          </Button>
        ),
      })

      // Invalidate queries de cap nhat bang ngay lap tuc ma khong can reload trang
      queryClientRef.current.invalidateQueries({ queryKey: ['merchant-orders'] })
    } catch (err) {
      console.error('Lỗi xử lý sự kiện đơn hàng WebSocket:', err)
    }
  }

  // Dang ky subscription theo chi nhanh dang chon hoac tat ca chi nhanh so huu
  const updateSubscriptions = () => {
    // Huy cac subscription cu
    subscriptionsRef.current.forEach((sub) => {
      try {
        sub.unsubscribe()
      } catch (e) {
        console.warn('Lỗi khi hủy subscription STOMP:', e)
      }
    })
    subscriptionsRef.current = []

    const client = clientRef.current
    if (!client || !client.connected) {
      return
    }

    const currentRestaurantId = restaurantIdRef.current
    const currentRestaurants = restaurantsRef.current

    const targetIds: number[] = []
    if (currentRestaurantId !== undefined) {
      targetIds.push(currentRestaurantId)
    } else if (currentRestaurants && currentRestaurants.length > 0) {
      currentRestaurants.forEach((r) => targetIds.push(r.id))
    }

    targetIds.forEach((id) => {
      const topic = `/topic/restaurants/${id}/orders`
      try {
        const sub = client.subscribe(topic, (msg: IMessage) => {
          handleIncomingOrder(msg)
        })
        subscriptionsRef.current.push(sub)
      } catch (err) {
        console.error(`Không thể subscribe topic ${topic}:`, err)
      }
    })
  }

  // Khoi tao WebSocket STOMP Client
  useEffect(() => {
    const token = localStorage.getItem('token')
    const client = new Client({
      brokerURL: getWsUrl(),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      beforeConnect: () => {
        setConnectionStatus('Connecting')
        const currentToken = localStorage.getItem('token')
        if (currentToken) {
          client.connectHeaders = { Authorization: `Bearer ${currentToken}` }
        }
      },
      onConnect: () => {
        setConnectionStatus('Online')
        // Dong bo du lieu khi ket noi / ket noi lai
        queryClientRef.current.invalidateQueries({ queryKey: ['merchant-orders'] })
        updateSubscriptions()
      },
      onDisconnect: () => {
        setConnectionStatus('Offline')
      },
      onWebSocketClose: () => {
        setConnectionStatus('Offline')
      },
      onStompError: (frame) => {
        console.warn('Lỗi STOMP:', frame)
        setConnectionStatus('Offline')
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      subscriptionsRef.current.forEach((sub) => {
        try {
          sub.unsubscribe()
        } catch {
          // ignore
        }
      })
      subscriptionsRef.current = []
      client.deactivate()
      clientRef.current = null
    }
  }, [])

  // Khi danh sach quan hoac quan duoc chon thay doi, cap nhat lai subscription
  useEffect(() => {
    if (clientRef.current && clientRef.current.connected) {
      updateSubscriptions()
    }
  }, [restaurantId, restaurants])

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
          <Text type="secondary">
            Cập nhật thời gian thực qua WebSocket · Trạng thái:{' '}
            <strong
              style={{
                color:
                  connectionStatus === 'Online'
                    ? '#52c41a'
                    : connectionStatus === 'Connecting'
                      ? '#1677ff'
                      : '#ff4d4f',
              }}
            >
              {connectionStatus}
            </strong>
            {isFetching ? ' · đang đồng bộ...' : ''}
          </Text>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          {connectionStatus === 'Online' && (
            <Tag color="success" icon={<CheckCircleOutlined />}>
              Online
            </Tag>
          )}
          {connectionStatus === 'Connecting' && (
            <Tag color="processing" icon={<SyncOutlined spin />}>
              Connecting
            </Tag>
          )}
          {connectionStatus === 'Offline' && (
            <Tag color="error" icon={<CloseCircleOutlined />}>
              Offline
            </Tag>
          )}

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
              // Trong 1 don, cung mon va cung ghi chu da duoc gop luc them vao gio nen bo 3 nay khong trung
              rowKey={(i) => `${i.menuItemId}-${i.name}-${i.note ?? ''}`}
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
