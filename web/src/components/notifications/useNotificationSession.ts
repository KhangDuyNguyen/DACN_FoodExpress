import { invalidateMerchantOrders, merchantOrderKeys } from '../../pages/merchant/merchantOrderQueries'
import { useInfiniteQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { Client, ReconnectionTimeMode } from '@stomp/stompjs'
import { isAxiosError } from 'axios'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { notificationApiBase, notificationClient, type NotificationItem, type NotificationPage } from '../../api/notifications'
import { useAuth } from '../../auth/useAuth'
import type { Center } from './NotificationContext'
import {
  confirmedNewNotifications, notificationOrderPath, notificationSocketUrl, readNotificationSignal, uniqueNotificationItems,
} from './notificationRules'

type Denial = 401 | 403 | 'changed'
type Session = { sessionId: string; userId: number; token: string; role?: Center['role'] }
type Toast = { item: NotificationItem; expiresAt: number }
type Lifetime = { controller: AbortController; refresh: () => void }

export function useNotificationSession({ sessionId, userId, token, role = 'CUSTOMER' }: Session) {
  const { logout } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [denial, setDenial] = useState<Denial | null>(null)
  const denied = useRef(false)
  const alive = useRef(true)
  const logoutRef = useRef(logout)
  useEffect(() => { logoutRef.current = logout }, [logout])
  const lifetime = useRef<Lifetime | null>(null)
  const [filter, setFilter] = useState(false)
  const soundKey = `notification-sound-${userId}`
  const [sound, setSoundState] = useState(() => localStorage.getItem(soundKey) !== 'off')
  const [connection, setConnection] = useState<Center['connection']>('connecting')
  const realtime = useRef(false)
  const placedAlerts = useRef(new Set<number>())
  const [online, setOnline] = useState(navigator.onLine)
  const [actionError, setActionError] = useState(false)
  const [toasts, setToasts] = useState<Toast[]>([])
  const client = useMemo(() => notificationClient(token), [token])
  const key = useMemo(() => ['notifications', role, userId, sessionId] as const, [role, sessionId, userId])
  const orderScope = useMemo(() => role === 'MERCHANT' ? merchantOrderKeys.scope(userId, sessionId) : undefined, [role, userId, sessionId])
  const current = useCallback(() => alive.current && !denied.current && localStorage.getItem('token') === token, [token])
  const valid = denial === null && localStorage.getItem('token') === token
  const claimPlaced = useCallback((id: unknown) => {
    if (!notificationOrderPath(id)) return true
    const key = id as number
    if (placedAlerts.current.has(key)) return false
    placedAlerts.current.add(key)
    return true
  }, [])

  const revoke = useCallback((reason: Denial) => {
    if (denied.current) return
    const sameToken = localStorage.getItem('token') === token
    denied.current = true
    setDenial(reason)
    setToasts([])
    void queryClient.cancelQueries({ queryKey: key })
    queryClient.removeQueries({ queryKey: key })
    if (orderScope) {
      void queryClient.cancelQueries({ queryKey: orderScope })
      queryClient.removeQueries({ queryKey: orderScope })
    }
    if (reason === 401 && sameToken) logoutRef.current()
  }, [key, orderScope, queryClient, token])

  const merchantSession = useMemo(() => orderScope ? { scope: orderScope, current, reject: revoke } : undefined, [orderScope, current, revoke])

  const checkError = useCallback((error: unknown) => {
    if (!current() || !isAxiosError(error)) return
    const status = error.response?.status
    if (status === 401 || status === 403) revoke(status)
  }, [current, revoke])

  const queryOptions = (unreadOnly: boolean) => ({
    queryKey: [...key, unreadOnly],
    enabled: valid && (!unreadOnly || filter),
    initialPageParam: null as string | null,
    queryFn: async ({ pageParam, signal }: { pageParam: string | null; signal: AbortSignal }) => {
      if (!current() || signal.aborted) throw new DOMException('Session changed', 'AbortError')
      try {
        const result = await client.list(unreadOnly, pageParam, signal)
        if (!current() || signal.aborted) throw new DOMException('Session changed', 'AbortError')
        return result
      } catch (error) {
        if (!signal.aborted) checkError(error)
        throw error
      }
    },
    getNextPageParam: (page: NotificationPage, _pages: NotificationPage[], _param: string | null, params: (string | null)[]) =>
      params.includes(page.nextCursor) ? undefined : page.nextCursor,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  const all = useInfiniteQuery(queryOptions(false))
  const unread = useInfiniteQuery(queryOptions(true))
  const selected = filter ? unread : all

  useEffect(() => {
    if (!valid || !current()) return
    const controller = new AbortController()
    const seen = new Set<number>()
    const pending = new Set<number>()
    let ordersDirty = false
    let ordersSyncing = false
    let refreshNeeded = false
    let syncing = false
    let started = false
    let closed = false
    let timer: ReturnType<typeof setTimeout> | undefined
    let subscription: { unsubscribe: () => void } | undefined
    let socket: Client | undefined
    let audio: HTMLAudioElement | undefined
    const active = () => !controller.signal.aborted && current()

    function schedule() {
      if (!active() || syncing || timer || (!refreshNeeded && pending.size === 0 && (!ordersDirty || ordersSyncing))) return
      timer = setTimeout(() => {
        timer = undefined
        void sync()
      }, 250)
    }

    async function sync() {
      if (!active()) return
      syncing = true
      refreshNeeded = false
      const ids = new Set(pending)
      pending.clear()
      if (ordersDirty && !ordersSyncing && orderScope) {
        ordersDirty = false
        ordersSyncing = true
        void invalidateMerchantOrders(queryClient, orderScope).catch(() => {}).finally(() => {
          ordersSyncing = false
          schedule()
        })
      }
      const allKey = [...key, false]
      const unreadKey = [...key, true]
      try {
        if (!active()) return
        const old = queryClient.getQueryData<InfiniteData<NotificationPage>>(allKey)
        for (const page of old?.pages ?? []) for (const item of page.items) seen.add(item.id)
        await queryClient.invalidateQueries({ queryKey: key, refetchType: 'none' })
        if (!active()) return
        await queryClient.refetchQueries({ queryKey: allKey, exact: true, type: 'active' }, { throwOnError: true })
        if (!active()) return
        const page = queryClient.getQueryData<InfiniteData<NotificationPage>>(allKey)?.pages[0]
        if (page) {
          for (const item of page.items) {
            if (pending.delete(item.id)) ids.add(item.id)
          }
          const found = confirmedNewNotifications(page, ids, seen).filter(item =>
            role !== 'MERCHANT' || item.eventType !== 'ORDER_PLACED' || claimPlaced(item.data.orderId))
          if (found.length) {
            const expiresAt = Date.now() + 5000
            const newest = found.slice(0, 3).reverse()
            setToasts(previous => [...previous, ...newest.map(item => ({ item, expiresAt }))].slice(-3))
            if (localStorage.getItem(soundKey) !== 'off' &&
                (role === 'CUSTOMER' || found.some(item => item.eventType === 'ORDER_PLACED'))) {
              audio?.pause()
              audio = new Audio(role === 'MERCHANT' ? '/merchant-order-chime.wav' : '/notification-ting.wav')
              void audio.play().catch(() => {})
            }
          }
        }
        await queryClient.refetchQueries({ queryKey: unreadKey, exact: true, type: 'active' }, { throwOnError: true })
      } catch (error) {
        if (active()) checkError(error)
      } finally {
        syncing = false
        schedule()
      }
    }

    function refresh() {
      if (orderScope) ordersDirty = true
      refreshNeeded = true
      schedule()
    }

    function storage(event: StorageEvent) {
      if ((event.key === 'token' || event.key === null) && !current()) revoke('changed')
    }

    function network() {
      setOnline(navigator.onLine)
      if (navigator.onLine) refresh()
    }

    const state: Lifetime = { controller, refresh }
    lifetime.current = state
    if (!current()) revoke('changed')
    window.addEventListener('storage', storage)
    window.addEventListener('online', network)
    window.addEventListener('offline', network)
    const start = setTimeout(() => {
      if (!active()) return
      try {
        socket = new Client({
          brokerURL: notificationSocketUrl(notificationApiBase, location.origin),
          connectHeaders: { Authorization: `Bearer ${token}` },
          reconnectDelay: 5000, maxReconnectDelay: 30000, reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
          heartbeatIncoming: 0, heartbeatOutgoing: 0,
        })
        socket.beforeConnect = () => {
          if (!active()) {
            if (!controller.signal.aborted) revoke('changed')
            void socket?.deactivate({ force: true })
            return
          }
          setConnection(started ? 'reconnecting' : 'connecting')
          started = true
        }
        socket.onConnect = () => {
          if (!active()) return
          realtime.current = true
          setConnection('live')
          subscription = socket?.subscribe('/user/queue/notifications', message => {
            if (!active()) return
            const id = readNotificationSignal(message.body)
            if (id === null) return
            if (orderScope) {
              ordersDirty = true
              refreshNeeded = true
            }
            if (seen.has(id)) { schedule(); return }
            pending.add(id)
            schedule()
          })
          refresh()
        }
        socket.onWebSocketClose = () => {
          if (active()) {
            realtime.current = false
            setConnection('reconnecting')
          }
        }
        socket.onStompError = () => {
          if (active()) {
            realtime.current = false
            setConnection('disconnected')
          }
          socket?.forceDisconnect()
        }
        socket.activate()
      } catch {
        if (active()) setConnection('disconnected')
      }
    }, 0)

    return () => {
      if (closed) return
      closed = true
      controller.abort()
      realtime.current = false
      clearTimeout(start)
      clearTimeout(timer)
      pending.clear()
      seen.clear()
      audio?.pause()
      window.removeEventListener('storage', storage)
      window.removeEventListener('online', network)
      window.removeEventListener('offline', network)
      if (socket?.connected) subscription?.unsubscribe()
      void socket?.deactivate({ force: true })
      if (lifetime.current === state) lifetime.current = null
      queueMicrotask(() => {
        if (lifetime.current === null) {
          alive.current = false
          void queryClient.cancelQueries({ queryKey: key })
          queryClient.removeQueries({ queryKey: key })
          if (orderScope) {
            void queryClient.cancelQueries({ queryKey: orderScope })
            queryClient.removeQueries({ queryKey: orderScope })
          }
        }
      })
    }
  }, [checkError, claimPlaced, current, key, orderScope, queryClient, revoke, role, soundKey, token, valid])

  const dismiss = useCallback((id: number) => setToasts(previous => previous.filter(toast => toast.item.id !== id)), [])
  const reading = useRef(new Set<number>())

  async function openItem(item: NotificationItem) {
    const session = lifetime.current
    const active = () => session !== null && !session.controller.signal.aborted && current()
    if (!session || !active() || reading.current.has(item.id)) return
    reading.current.add(item.id)
    setActionError(false)
    try {
      await client.read(item.id, session.controller.signal)
      if (!active()) return
      queryClient.setQueriesData<InfiniteData<NotificationPage>>({ queryKey: key }, data => data && ({
        ...data,
        pages: data.pages.map(page => ({
          ...page, items: page.items.map(value => value.id === item.id ? { ...value, isRead: true } : value),
        })),
      }))
      session.refresh()
      dismiss(item.id)
      const orderPath = item.type === 'ORDER' ? notificationOrderPath(item.data.orderId) : null
      const path = orderPath && role === 'MERCHANT' ? '/merchant/orders' : orderPath
      if (path) navigate(path)
    } catch (error) {
      if (active()) {
        checkError(error)
        if (active()) setActionError(true)
      }
    } finally {
      reading.current.delete(item.id)
    }
  }

  function setSound(value: boolean) {
    if (!current()) return
    localStorage.setItem(soundKey, value ? 'on' : 'off')
    setSoundState(value)
  }

  const items = uniqueNotificationItems(selected.data?.pages ?? [])
  const value: Center = {
    merchantSession,
    role, allowOrderPopup: id => current() && !realtime.current && claimPlaced(id),
    valid, denial, unread: valid ? all.data?.pages[0]?.unreadCount : undefined,
    unreadOnly: filter, setUnreadOnly: setFilter, items: valid ? (filter ? items.filter(item => !item.isRead) : items) : [],
    loading: valid && selected.isPending && !selected.isError, error: valid && selected.isError, actionError: valid && actionError,
    retry: () => { if (current()) lifetime.current?.refresh() },
    more: () => { if (current() && !selected.isFetching) void selected.fetchNextPage({ cancelRefetch: false }) },
    hasMore: valid && selected.hasNextPage, fetching: selected.isFetchingNextPage,
    openItem: item => void openItem(item), sound, setSound, connection, online,
  }
  return { value, toasts: valid ? toasts : [], dismiss }
}
