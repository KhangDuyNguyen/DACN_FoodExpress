import { api } from './client'

export interface AdminDashboardStats {
  totalOrders: number
  totalRevenue: number
  totalUsers: number
  totalRestaurants: number
}

export async function fetchAdminDashboardStats(): Promise<AdminDashboardStats> {
  const res = await api.get<AdminDashboardStats>('/admin/dashboard/stats')
  return res.data
}
