import type { OrderStatus } from '../../api/orders'

// Mau nhan trang thai don (Tailwind), dung chung cho trang lich su va trang theo doi don
export const STATUS_BADGE: Partial<Record<OrderStatus, string>> = {
  PENDING_PAYMENT: 'bg-yellow-100 text-yellow-800',
  PLACED: 'bg-orange-100 text-orange-700',
  PREPARING: 'bg-blue-100 text-blue-700',
  READY_FOR_PICKUP: 'bg-cyan-100 text-cyan-700',
  DELIVERING: 'bg-purple-100 text-purple-700',
  DELIVERED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-gray-200 text-gray-600',
}
