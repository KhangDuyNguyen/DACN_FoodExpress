import axios from 'axios'
import { readNotificationPage } from '../components/notifications/notificationRules'

export interface NotificationItem {
  id: number
  type: string
  eventType: string | null
  title: string
  body: string
  isRead: boolean
  createdAt: string
  data: { orderId?: number }
}

export interface NotificationPage {
  items: NotificationItem[]
  nextCursor: string | null
  unreadCount: number
}

export const notificationApiBase = import.meta.env.VITE_API_URL || '/api'

export function notificationClient(token: string) {
  const client = axios.create({
    baseURL: notificationApiBase,
    headers: { Authorization: `Bearer ${token}` },
  })
  return {
    async list(unreadOnly: boolean, cursor: string | null, signal: AbortSignal): Promise<NotificationPage> {
      const { data } = await client.get<unknown>('/notifications', {
        params: { limit: 20, unreadOnly, ...(cursor ? { cursor } : {}) },
        signal,
      })
      return readNotificationPage(data)
    },
    async read(id: number, signal: AbortSignal) {
      const { status } = await client.patch(`/notifications/${id}/read`, undefined, { signal })
      if (status !== 204) throw new Error('Lưu trạng thái thông báo không thành công')
    },
  }
}
