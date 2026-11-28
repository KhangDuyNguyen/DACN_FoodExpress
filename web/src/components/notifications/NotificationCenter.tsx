import {
  BellOutlined, CarOutlined, CheckCircleFilled, CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined,
  CloseOutlined, CoffeeOutlined, InboxOutlined, LoadingOutlined, ShoppingOutlined, WarningOutlined, WifiOutlined,
} from '@ant-design/icons'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import type { NotificationItem } from '../../api/notifications'
import { Context, useNotificationCenter, type Center } from './NotificationContext'
import { useNotificationSession } from './useNotificationSession'
import './merchantNotification.css'

type Session = { sessionId: string; userId: number; token: string; role?: Center['role']; children: ReactNode }

const eventIcons = {
  ORDER_PLACED: ShoppingOutlined,
  ORDER_PREPARING: ClockCircleOutlined,
  ORDER_READY_FOR_PICKUP: CoffeeOutlined,
  ORDER_DELIVERING: CarOutlined,
  ORDER_DELIVERED: CheckCircleOutlined,
  ORDER_CANCELLED: CloseCircleOutlined,
}

function NotificationIcon({ eventType }: { eventType: string | null }) {
  const Icon = eventType && Object.hasOwn(eventIcons, eventType)
    ? eventIcons[eventType as keyof typeof eventIcons] : BellOutlined
  return <Icon aria-hidden />
}

export function NotificationProvider({ children, ...session }: Session) {
  const { value, toasts, dismiss } = useNotificationSession(session)
  return <Context.Provider value={value}>
    {children}
    <div className="pointer-events-none fixed bottom-4 left-4 z-50 flex w-[min(360px,calc(100vw-2rem))] flex-col gap-2" aria-live="polite">
      {toasts.map(({ item, expiresAt }) => <NotificationToast key={item.id} item={item} expiresAt={expiresAt}
        open={value.openItem} dismiss={dismiss} />)}
    </div>
  </Context.Provider>
}

function NotificationToast({ item, expiresAt, open, dismiss }: {
  item: NotificationItem; expiresAt: number; open: (item: NotificationItem) => void; dismiss: (id: number) => void
}) {
  useEffect(() => {
    const timeout = setTimeout(() => dismiss(item.id), Math.max(0, expiresAt - Date.now()))
    return () => clearTimeout(timeout)
  }, [dismiss, expiresAt, item.id])
  return <div data-notification-toast className="pointer-events-auto flex items-start gap-1 rounded-xl border border-green-200 border-l-4 border-l-green-500 bg-white p-2.5 text-gray-900 shadow-lg">
    <button type="button" className="flex min-w-0 flex-1 items-start gap-3 break-words rounded-md p-1 text-left hover:bg-green-50"
      onClick={() => open(item)}>
      <span className="mt-0.5 shrink-0 text-xl text-green-600">
        {item.eventType === 'ORDER_DELIVERED' ? <CheckCircleFilled aria-hidden /> : <NotificationIcon eventType={item.eventType} />}
      </span>
      <span className="min-w-0">
        <strong className="block text-sm">{item.title}</strong>
        <span className="block text-xs leading-snug text-gray-700">{item.body}</span>
      </span>
    </button>
    <button type="button" aria-label="Đóng thông báo" className="grid size-8 shrink-0 place-items-center rounded-md text-gray-600 hover:bg-gray-100"
      onClick={() => dismiss(item.id)}><CloseOutlined aria-hidden /></button>
  </div>
}

function NotificationState({ icon, children, retry }: { icon: ReactNode; children: ReactNode; retry?: () => void }) {
  return <div className="my-2 flex flex-col items-center gap-2 rounded-xl border border-gray-100 bg-gray-50 p-4 text-center text-sm text-gray-700">
    <span className="text-2xl" aria-hidden>{icon}</span>
    <p role="status">{children}</p>
    {retry && <button type="button" onClick={retry} className="min-h-9 rounded-lg border border-orange-300 px-4 font-medium text-orange-700 hover:bg-orange-50">Thử lại</button>}
  </div>
}

export function NotificationList() {
  const center = useNotificationCenter()
  if (!center?.valid) return <p role="status" className="p-4 text-center text-gray-600">
    {center?.denial === 403 ? 'Bạn không có quyền xem thông báo.' : 'Vui lòng đăng nhập để xem thông báo.'}
  </p>
  return <div className={center.role === 'MERCHANT' ? 'merchant-notification' : undefined}>
    <div className="mb-3 flex gap-2">
      {[false, true].map(value => <button type="button" key={String(value)} aria-pressed={center.unreadOnly === value}
        onClick={() => center.setUnreadOnly(value)}
        className={`min-h-9 rounded-full border px-4 text-sm ${center.unreadOnly === value ? 'border-orange-600 bg-orange-600 text-white' : 'border-gray-200 bg-white hover:bg-orange-50'}`}>
        {value ? `Chưa đọc${center.unread !== undefined ? ` (${center.unread})` : ''}` : 'Tất cả'}
      </button>)}
    </div>
    {center.online && center.loading && <NotificationState icon={<LoadingOutlined className="text-orange-600" />}>Đang tải thông báo...</NotificationState>}
    {center.online && center.connection !== 'live' && <p role="status" className="mb-2 flex items-center gap-2 rounded-lg bg-blue-50 px-3 py-2 text-xs text-blue-800"><WifiOutlined aria-hidden />{
      { connecting: 'Đang kết nối...', reconnecting: 'Đang kết nối lại...', disconnected: 'Mất kết nối realtime' }[center.connection]
    }</p>}
    {(center.error || !center.online) && <NotificationState retry={center.retry}
      icon={center.online ? <WarningOutlined className="text-orange-700" /> : <WifiOutlined className="text-gray-500" />}>
      {center.online ? 'Không thể tải thông báo' : 'Không có kết nối Internet'}
    </NotificationState>}
    {center.actionError && <p role="alert" className="p-2 text-sm text-red-700">Không thể đánh dấu đã đọc. Vui lòng thử lại.</p>}
    {center.online && !center.loading && !center.error && !center.items.length &&
      <NotificationState icon={<InboxOutlined className="text-gray-500" />}>{center.unreadOnly ? 'Bạn không có thông báo chưa đọc' : 'Chưa có thông báo nào'}</NotificationState>}
    <div className="max-h-[min(20rem,40dvh)] space-y-1 overflow-y-auto overscroll-contain">
      {center.items.map(item => <button key={item.id} type="button" data-notification-item={item.id} onClick={() => center.openItem(item)}
        className={`flex w-full items-start gap-2.5 rounded-lg p-2.5 text-left hover:bg-orange-100/70 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-orange-600 ${item.isRead ? 'bg-gray-50' : 'bg-orange-50'}`}>
        <span className={`grid size-8 shrink-0 place-items-center rounded-lg text-lg ${item.isRead ? 'bg-gray-100 text-gray-600' : 'bg-orange-100 text-orange-700'}`}>
          <NotificationIcon eventType={item.eventType} />
        </span>
        <span className="min-w-0 flex-1">
          <strong className="block break-words text-sm leading-snug">{item.title}</strong>
          <span className="mt-0.5 block break-words text-xs leading-snug text-gray-700">{item.body}</span>
          <time className="mt-1 block text-xs text-gray-600" dateTime={item.createdAt}>{
            new Date(item.createdAt).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })
          }</time>
        </span>
        {!item.isRead && <span className="mt-1.5 size-2 shrink-0 rounded-full bg-orange-600"><span className="sr-only">Chưa đọc</span></span>}
      </button>)}
    </div>
    {center.hasMore && <button type="button" disabled={center.fetching} onClick={center.more} className="mt-2 w-full rounded-lg border border-orange-200 p-2 text-sm font-medium text-orange-700 hover:bg-orange-50 disabled:opacity-50">
      {center.fetching ? 'Đang tải thêm...' : 'Tải thêm'}
    </button>}
  </div>
}

export function NotificationBell() {
  const center = useNotificationCenter()
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState({ top: 0, left: 0, maxHeight: 0 })
  const ref = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panel = useId()
  const updatePosition = () => {
    const bounds = buttonRef.current?.getBoundingClientRect()
    if (!bounds) return
    const width = Math.min(380, window.innerWidth - 16)
    const top = Math.max(8, bounds.bottom + 6)
    setPosition({
      top, left: Math.max(8, Math.min(bounds.right - width, window.innerWidth - width - 8)),
      maxHeight: Math.max(0, window.innerHeight - top - 8),
    })
  }
  useEffect(() => {
    if (!open) return
    const click = (event: MouseEvent) => {
      const target = event.target as Node
      if (!ref.current?.contains(target) && !panelRef.current?.contains(target)) setOpen(false)
    }
    const key = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', click)
    document.addEventListener('keydown', key)
    window.addEventListener('resize', updatePosition)
    window.addEventListener('scroll', updatePosition)
    return () => {
      document.removeEventListener('mousedown', click)
      document.removeEventListener('keydown', key)
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition)
    }
  }, [open])
  if (!center?.valid) return null
  const merchant = center.role === 'MERCHANT'
  const dropdown = open && <section ref={panelRef} role="region" aria-label="Danh sách thông báo nhanh" id={panel} style={position}
      className={`fixed z-40 w-[min(380px,calc(100vw-1rem))] overflow-y-auto overscroll-contain rounded-xl border border-gray-200 bg-white p-3 text-gray-900 shadow-xl sm:p-4 ${merchant ? 'merchant-notification' : ''}`}>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <strong>Thông báo{center.unread !== undefined ? ` (${center.unread})` : ''}</strong>
        <button type="button" role="switch" aria-checked={center.sound} onClick={() => center.setSound(!center.sound)}
          className="flex min-h-9 items-center gap-2 rounded-lg px-1.5 text-xs hover:bg-orange-50">
          Nhận thông báo âm thanh
          <span aria-hidden className={`relative h-5 w-9 shrink-0 rounded-full ${center.sound ? 'bg-orange-600' : 'bg-gray-400'}`}>
            <span className={`absolute left-0.5 top-0.5 size-4 rounded-full bg-white shadow ${center.sound ? 'translate-x-4' : ''}`} />
          </span>
        </button>
      </div>
      <NotificationList />
      <Link to={merchant ? '/merchant/notifications' : '/notifications'} onClick={() => setOpen(false)} className="mt-3 block rounded-lg border-t border-gray-100 py-2 text-center text-sm font-semibold text-orange-700 hover:bg-orange-50">Xem tất cả thông báo →</Link>
    </section>
  return <div ref={ref} className={`relative shrink-0 ${merchant ? 'merchant-notification' : ''}`}>
    <button type="button" ref={buttonRef} aria-label={center.unread === undefined ? 'Thông báo' : `Thông báo, ${center.unread} chưa đọc`}
      aria-expanded={open} aria-controls={open ? panel : undefined}
      onClick={() => { if (!open) updatePosition(); setOpen(!open) }}
      className="relative grid size-10 place-items-center rounded-lg text-xl text-orange-600 hover:bg-orange-50 focus-visible:outline-2">
      <BellOutlined aria-hidden />{center.unread !== undefined && center.unread > 0 && <span aria-hidden className="absolute right-0 top-0 grid min-w-4 place-items-center rounded-full bg-red-600 px-1 text-xs text-white">{center.unread > 99 ? '99+' : center.unread}</span>}
    </button>
    {merchant ? createPortal(dropdown, document.body) : dropdown}
  </div>
}
