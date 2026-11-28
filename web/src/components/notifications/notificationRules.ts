import type { NotificationItem, NotificationPage } from '../../api/notifications'

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function positiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

const eventName = /^[A-Z][A-Z0-9_]{0,63}$/
const instant = /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d)$/

function parseItem(value: unknown): NotificationItem | null {
  if (!record(value) || !positiveId(value.id) || typeof value.type !== 'string' ||
      typeof value.title !== 'string' || typeof value.body !== 'string' || typeof value.isRead !== 'boolean' ||
      typeof value.createdAt !== 'string' || !instant.test(value.createdAt) ||
      !Number.isFinite(Date.parse(value.createdAt))) return null
  const orderId = record(value.data) ? value.data.orderId : null
  return {
    id: value.id, type: value.type, title: value.title, body: value.body, isRead: value.isRead,
    eventType: typeof value.eventType === 'string' && eventName.test(value.eventType) ? value.eventType : null,
    createdAt: value.createdAt, data: positiveId(orderId) ? { orderId } : {},
  }
}

export function readNotificationPage(value: unknown): NotificationPage {
  if (!record(value) || !Array.isArray(value.items) || typeof value.unreadCount !== 'number' ||
      !Number.isSafeInteger(value.unreadCount) || value.unreadCount < 0 ||
      !(value.nextCursor === null || (typeof value.nextCursor === 'string' && value.nextCursor.length > 0))) {
    throw new Error('Dữ liệu thông báo không hợp lệ')
  }
  return {
    items: value.items.map(parseItem).filter(item => item !== null),
    nextCursor: value.nextCursor, unreadCount: value.unreadCount,
  }
}

export function readNotificationSignal(body: string): number | null {
  try {
    const value: unknown = JSON.parse(body)
    if (!record(value) || typeof value.eventType !== 'string' || !eventName.test(value.eventType)) return null
    return positiveId(value.notificationId) ? value.notificationId : null
  } catch {
    return null
  }
}

export function notificationOrderPath(orderId: unknown): string | null {
  return positiveId(orderId) ? `/orders/${orderId}` : null
}

export function notificationSocketUrl(apiBase: string, origin: string): string {
  const api = new URL(apiBase, origin)
  if (api.protocol !== 'http:' && api.protocol !== 'https:') throw new Error('Địa chỉ API không hợp lệ')
  const prefix = api.pathname.replace(/\/+$/, '').replace(/\/api$/, '')
  return (api.protocol === 'https:' ? 'wss://' : 'ws://') + api.host + prefix + '/ws'
}

export function uniqueNotificationItems(pages: NotificationPage[]): NotificationItem[] {
  const items = new Map<number, NotificationItem>()
  for (const page of pages) {
    for (const item of page.items) {
      if (!items.has(item.id)) items.set(item.id, item)
    }
  }
  return [...items.values()]
}

export function confirmedNewNotifications(page: NotificationPage, pending: Set<number>, seen: Set<number>) {
  const found = uniqueNotificationItems([page]).filter(item => !item.isRead && pending.has(item.id) && !seen.has(item.id))
  for (const id of pending) seen.add(id)
  for (const item of page.items) seen.add(item.id)
  return found
}
