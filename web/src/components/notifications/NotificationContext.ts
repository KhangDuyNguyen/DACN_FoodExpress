import { createContext, useContext } from 'react'
import type { NotificationItem } from '../../api/notifications'

export type Center = {
  role: 'CUSTOMER' | 'MERCHANT'
  allowOrderPopup: (orderId: unknown) => boolean
  valid: boolean
  denial: 401 | 403 | 'changed' | null
  unread: number | undefined
  unreadOnly: boolean
  setUnreadOnly: (value: boolean) => void
  items: NotificationItem[]
  loading: boolean
  error: boolean
  actionError: boolean
  retry: () => void
  more: () => void
  hasMore: boolean
  fetching: boolean
  openItem: (item: NotificationItem) => void
  sound: boolean
  setSound: (value: boolean) => void
  connection: 'connecting' | 'live' | 'reconnecting' | 'disconnected'
  online: boolean
}

export const Context = createContext<Center | null>(null)
export function useNotificationCenter() {
  return useContext(Context)
}
