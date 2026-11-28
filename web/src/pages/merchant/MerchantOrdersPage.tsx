import { isAxiosError } from 'axios'
import { invalidateMerchantOrders, merchantOrderKeys, type MerchantOrderScope } from './merchantOrderQueries'
import React, { useEffect, useEffectEvent, useRef, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
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
  ReloadOutlined,
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
import { statusLabel, type MerchantOrderSummary, type OrderStatus } from '../../api/orders'
import type { PageResponse } from '../../api/page'
import { useAuth } from '../../auth/useAuth'
import { useNotificationCenter } from '../../components/notifications/NotificationContext'

// Man hinh nhan don va doi trang thai voi cap nhat thoi gian thuc qua WebSocket STOMP
const { Title, Text } = Typography

const PAGE_SIZE = 20

const STATUS_COLOR: Partial<Record<OrderStatus, string>> = {
  PLACED: 'orange',
  PREPARING: 'blue',
  READY_FOR_PICKUP: 'cyan',
  DELIVERING: 'purple',
  DELIVERED: 'green',
  CANCELLED: 'default',
}

const STATUS_TABS: { key: OrderStatus | 'ALL'; label: string }[] = [
  { key: 'PLACED', label: 'Chờ xác nhận' },
  { key: 'PREPARING', label: 'Đang chuẩn bị' },
  { key: 'READY_FOR_PICKUP', label: 'Chờ lấy món' },
  { key: 'DELIVERING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Hoàn thành' },
  { key: 'CANCELLED', label: 'Đã hủy' },
  { key: 'ALL', label: 'Tất cả' },
]

// Buoc tiep theo cua tung trang thai, khop voi luat OrderStatusFlow o backend
const NEXT_ACTION: Partial<Record<OrderStatus, { status: OrderStatus; label: string }>> = {
  PLACED: { status: 'PREPARING', label: 'Nhận đơn' },
  PREPARING: { status: 'READY_FOR_PICKUP', label: 'Món đã xong' },
  READY_FOR_PICKUP: { status: 'DELIVERING', label: 'Bắt đầu giao' },
  DELIVERING: { status: 'DELIVERED', label: 'Hoàn thành' },
}
// 2 buoc giao hang: don da co tai xe thi tai xe bam tren app, quan chi bam khi chua co tai xe
const DELIVERY_STEPS: OrderStatus[] = ['DELIVERING', 'DELIVERED']
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
  const center = useNotificationCenter()
  const { user, token } = useAuth()
  const session = center?.merchantSession
  if (!center?.valid || !session || user?.id !== session.scope[1] || !token || !session.current()) return null
  return <MerchantOrdersSession key={session.scope[2]} scope={session.scope} />
}

const MerchantOrdersSession: React.FC<{ scope: MerchantOrderScope }> = ({ scope }) => {
  const queryClient = useQueryClient()
  const { user, token } = useAuth()
  const userId = user?.role === 'MERCHANT' ? user.id : undefined
  const notificationCenter = useNotificationCenter()
  const session = notificationCenter!.merchantSession!
  const requestSession = { token: token!, current: session.current }
  const [available, setAvailable] = useState(() => document.visibilityState === 'visible' && navigator.onLine)
  async function readCurrent<T>(work: () => Promise<T>, signal: AbortSignal): Promise<T> {
    try { return await work() }
    catch (error) {
      if (!signal.aborted && session.current() && isAxiosError(error)) {
        const status = error.response?.status
        if (status === 401 || status === 403) session.reject(status)
      }
      throw error
    }
  }
  useEffect(() => {
    function update() {
      const ready = document.visibilityState === 'visible' && navigator.onLine
      setAvailable(ready)
      if (!ready) void queryClient.cancelQueries({ queryKey: scope })
    }
    document.addEventListener('visibilitychange', update)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      document.removeEventListener('visibilitychange', update)
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [queryClient, scope])
  const [restaurantId, setRestaurantId] = useState<number | undefined>()
  const [tab, setTab] = useState<OrderStatus | 'ALL'>('PLACED')
  const [page, setPage] = useState(0)
  const [detailId, setDetailId] = useState<number | null>(null)
  const [cancelTarget, setCancelTarget] = useState<MerchantOrderSummary | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('Connecting')

  const syncRooms = useRef<(() => void) | null>(null)
  const processedOrderIdsRef = useRef<Set<string | number>>(new Set())

  const restaurantIdRef = useRef<number | undefined>(restaurantId)
  const queryClientRef = useRef(queryClient)
  const { data: restaurants = [] } = useQuery({
    queryKey: merchantOrderKeys.restaurants(scope),
    queryFn: ({ signal }) => readCurrent(() => fetchMyRestaurants(signal, requestSession), signal),
    enabled: available && session.current(), retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
  })
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

  const { data, isLoading, isFetching, isError, error, refetch } = useQuery({
    queryKey: merchantOrderKeys.list(scope, restaurantId, tab, page),
    queryFn: ({ signal }) => readCurrent(() =>
      fetchMerchantOrders({ restaurantId, status: tab === 'ALL' ? undefined : tab, page, size: PAGE_SIZE }, signal, requestSession), signal),
    enabled: available && session.current(), retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
    placeholderData: keepPreviousData,
    refetchInterval: available && session.current() ? 15_000 : false,
  })

  const { data: detail, isLoading: loadingDetail } = useQuery({
    queryKey: merchantOrderKeys.detail(scope, detailId),
    queryFn: ({ signal }) => readCurrent(() => fetchMerchantOrder(detailId as number, signal, requestSession), signal),
    enabled: detailId !== null && available && session.current(),
    refetchInterval: available && session.current() ? 15_000 : false,
    retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false,
  })

  const statusMutation = useMutation({
    mutationFn: (vars: { id: number; status: OrderStatus; reason?: string }) =>
      changeOrderStatus(vars.id, { status: vars.status, reason: vars.reason }, requestSession),
    onSuccess: (order) => {
      if (!session.current()) return
      message.success(`Đơn ${order.code}: ${statusLabel(order.status)}`)
      void invalidateMerchantOrders(queryClient, scope)
      queryClient.setQueryData(merchantOrderKeys.detail(scope, order.id), order)
      setCancelTarget(null)
      setCancelReason('')
    },
    onError: (error) => {
      if (!session.current()) return
      if (isAxiosError(error) && (error.response?.status === 401 || error.response?.status === 403)) {
        session.reject(error.response.status)
        return
      }
      message.error(errorText(error))
    },
  })

  // Xu ly su kien don hang moi tu WebSocket STOMP
  const handleIncomingOrder = useEffectEvent((msg: IMessage, current: () => boolean, popupKeys: Set<string>) => {
    try {
      let orderData: any
      try {
        orderData = JSON.parse(msg.body)
      } catch {
        orderData = { code: msg.body }
      }

      // Tranh trung lap bang bo nho tam processedOrderIds
      const orderKey = orderData?.id ?? orderData?.orderId ?? orderData?.code
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

      const incomingRestaurantId = orderData?.restaurantId ?? orderData?.restaurant?.id
      const currentFilterRestaurantId = restaurantIdRef.current

      // Cap nhat cache ngay lap tuc cho trang 0 cua tab PLACED va ALL neu phu hop bo loc
      if (orderData && typeof orderData.id === 'number') {
        const incomingOrder = orderData as MerchantOrderSummary
        const matchesFilter =
          currentFilterRestaurantId === undefined || currentFilterRestaurantId === incomingOrder.restaurantId
        if (matchesFilter) {
          (['PLACED', 'ALL'] as const).forEach((targetTab) => {
            queryClientRef.current.setQueryData<PageResponse<MerchantOrderSummary>>(
              merchantOrderKeys.list(scope, currentFilterRestaurantId, targetTab, 0),
              (old) => {
                if (!old) return old
                if (old.items.some((item) => item.id === incomingOrder.id)) {
                  return old
                }
                const nextItems = [incomingOrder, ...old.items].slice(0, PAGE_SIZE)
                return {
                  ...old,
                  items: nextItems,
                  totalItems: old.totalItems + 1,
                }
              }
            )
          })
        }
      }

      const orderCode = orderData?.code ? `Đơn hàng ${orderData.code}` : orderKey ? `Đơn hàng #${orderKey}` : 'Đơn hàng mới'
      const rawRestaurantName = orderData?.restaurantName ?? orderData?.restaurant?.name
      const restaurantName = rawRestaurantName ? ` tại ${rawRestaurantName}` : ''
      const amountStr = orderData?.totalAmount ? ` (${vnd(Number(orderData.totalAmount))})` : ''

      if (!notificationCenter || notificationCenter.allowOrderPopup(orderKey)) {
      const key = 'merchant-order-' + String(orderKey ?? crypto.getRandomValues(new Uint32Array(1))[0])
      popupKeys.add(key)
      notification.info({
        key,
        message: '🔔 Có đơn hàng mới!',
        description: `${orderCode}${restaurantName}${amountStr} vừa được đặt thành công. Danh sách đơn đã được cập nhật.`,
        placement: 'topRight',
        duration: 6,
        onClose: () => popupKeys.delete(key),
        btn: (
          <Button
            type="primary"
            size="small"
            onClick={() => {
              if (!current()) return
              if (
                currentFilterRestaurantId !== undefined &&
                typeof incomingRestaurantId === 'number' &&
                currentFilterRestaurantId !== incomingRestaurantId
              ) {
                setRestaurantId(incomingRestaurantId)
              }
              setTab('PLACED')
              setPage(0)
              invalidateMerchantOrders(queryClientRef.current, scope)
              notification.destroy(key)
            }}
          >
            Xem ngay
          </Button>
        ),
      })
      }

      // Invalidate queries de dong bo tong so don va phan trang voi backend
      invalidateMerchantOrders(queryClientRef.current, scope)
    } catch {
      console.error('Lỗi xử lý sự kiện đơn hàng WebSocket')
    }
  })

  useEffect(() => {
    if (!token || userId === undefined || localStorage.getItem('token') !== token) return
    let active = true
    const rooms = new Map<number, StompSubscription>()
    const popupKeys = new Set<string>()
    const current = () => active && localStorage.getItem('token') === token
    const client = new Client({
      brokerURL: getWsUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      beforeConnect: () => {
        if (!current()) { stop(); return }
        setConnectionStatus('Connecting')
      },
      onConnect: () => {
        if (!current()) { stop(); return }
        setConnectionStatus('Online')
        void invalidateMerchantOrders(queryClientRef.current, scope)
        subscribe()
      },
      onWebSocketClose: () => {
        rooms.clear()
        if (current()) setConnectionStatus('Offline')
      },
      onStompError: () => {
        if (current()) setConnectionStatus('Offline')
        client.forceDisconnect()
      },
    })

    function subscribe() {
      if (!current() || !client.connected) return
      const selected = restaurantIdRef.current
      const targets = new Set(restaurantsRef.current.map(row => row.id).filter(id => selected === undefined || id === selected))
      for (const [id, subscription] of rooms) {
        if (!targets.has(id)) {
          rooms.delete(id)
          subscription.unsubscribe()
        }
      }
      for (const id of targets) {
        if (rooms.has(id)) continue
        const subscription = client.subscribe(`/topic/restaurants/${id}/orders`, msg => {
          const ownsSubscription = () => current() && rooms.get(id) === subscription
          if (ownsSubscription()) handleIncomingOrder(msg, ownsSubscription, popupKeys)
        })
        rooms.set(id, subscription)
      }
    }

    function stop() {
      if (!active) return
      active = false
      clearTimeout(start)
      window.removeEventListener('storage', storage)
      if (client.connected) for (const sub of rooms.values()) sub.unsubscribe()
      rooms.clear()
      for (const key of popupKeys) notification.destroy(key)
      popupKeys.clear()
      processedOrderIdsRef.current.clear()
      if (syncRooms.current === subscribe) syncRooms.current = null
      void client.deactivate({ force: true })
    }

    function storage(event: StorageEvent) {
      if ((event.key === 'token' || event.key === null) && !current()) {
        setConnectionStatus('Offline')
        stop()
      }
    }

    syncRooms.current = subscribe
    window.addEventListener('storage', storage)
    const start = setTimeout(() => {
      if (current()) client.activate()
      else stop()
    }, 0)
    return stop
  }, [token, userId, scope])

  useEffect(() => { syncRooms.current?.() }, [restaurantId, restaurants])

  const columns: ColumnsType<MerchantOrderSummary> = [
    {
      title: 'Mã đơn',
      key: 'code',
      width: 165,
      render: (_, o) => (
        <div>
          <Text strong>{o.code}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {new Date(o.createdAt).toLocaleString('vi-VN')}
            </Text>
          </div>
          <div style={{ marginTop: 2 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {o.restaurantName}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Món ăn & Số lượng',
      key: 'items',
      width: 260,
      render: (_, o) => (
        <div>
          {o.items && o.items.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {o.items.map((item, idx) => (
                <div key={`${o.id}-item-${item.menuItemId ?? idx}`} style={{ fontSize: 13 }}>
                  <Text strong>{item.quantity}×</Text> {item.name}{' '}
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    ({vnd(item.unitPrice)})
                  </Text>
                  {item.note && (
                    <div>
                      <Text type="warning" style={{ fontSize: 12 }}>
                        · {item.note}
                      </Text>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <Text>{o.itemCount} phần</Text>
          )}
        </div>
      ),
    },
    {
      title: 'Giao đến & Ghi chú',
      key: 'receiver',
      width: 250,
      render: (_, o) => (
        <div>
          <Text strong>{o.receiverName}</Text>
          {o.receiverPhone && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {' · '}
              {o.receiverPhone}
            </Text>
          )}
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {o.deliveryAddress}
            </Text>
          </div>
          {o.note && (
            <div style={{ marginTop: 4 }}>
              <Tag color="gold" style={{ whiteSpace: 'normal', marginInlineEnd: 0 }}>
                Ghi chú: {o.note}
              </Tag>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Tổng tiền',
      key: 'total',
      align: 'right',
      width: 125,
      render: (_, o) => (
        <div>
          <Text strong>{vnd(o.totalAmount)}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 11 }}>
              {o.paymentMethod ?? 'COD'}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Tài xế',
      key: 'driver',
      width: 140,
      render: (_, o) => o.driverName ?? <Text type="secondary">Chưa có</Text>,
    },
    {
      title: 'Trạng thái',
      key: 'status',
      width: 130,
      render: (_, o) => <Tag color={STATUS_COLOR[o.status]}>{statusLabel(o.status)}</Tag>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 210,
      render: (_, o) => {
        const next = NEXT_ACTION[o.status]
        const driverDoesNext = !!next && !!o.driverName && DELIVERY_STEPS.includes(next.status)
        return (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <Button size="small" onClick={() => setDetailId(o.id)}>
              Chi tiết
            </Button>
            {next && !driverDoesNext && (
              <Button
                size="small"
                type="primary"
                loading={statusMutation.isPending && statusMutation.variables?.id === o.id}
                onClick={() => statusMutation.mutate({ id: o.id, status: next.status })}
              >
                {next.label}
              </Button>
            )}
            {driverDoesNext && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                Tài xế cập nhật
              </Text>
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
            style={{ minWidth: 220, maxWidth: '100%' }}
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

      {isError && (
        <Alert
          style={{ marginTop: 16 }}
          type="error"
          showIcon
          message="Không thể tải danh sách đơn hàng"
          description={errorText(error)}
          action={
            <Button size="small" danger icon={<ReloadOutlined />} onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        />
      )}

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
          locale={{ emptyText: <Empty description="Không có đơn hàng nào" /> }}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total: data?.totalItems ?? 0,
            onChange: (p) => setPage(p - 1),
          }}
          scroll={{ x: 1240 }}
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
              <Descriptions.Item label="Tài xế">
                {detail.driver ? (
                  `${detail.driver.name} · ${detail.driver.phone}` +
                  (detail.driver.licensePlate ? ` · ${detail.driver.licensePlate}` : '')
                ) : (
                  <Text type="secondary">Chưa có tài xế nhận</Text>
                )}
              </Descriptions.Item>
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
        title={
          cancelTarget
            ? `${cancelTarget.status === 'PLACED' ? 'Từ chối đơn' : 'Hủy đơn'} ${cancelTarget.code}`
            : ''
        }
        okText={cancelTarget?.status === 'PLACED' ? 'Xác nhận từ chối' : 'Xác nhận hủy'}
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
        <Text>
          {cancelTarget?.status === 'PLACED'
            ? 'Bạn chắc chắn muốn từ chối đơn hàng này? Vui lòng nhập lý do (khách sẽ thấy lý do này):'
            : 'Lý do hủy đơn (khách sẽ thấy lý do này):'}
        </Text>
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
