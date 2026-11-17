import { api } from './client'

export interface DailyRevenueItem {
  date: string
  revenue: number
  orderCount: number
}

export interface RevenueReportResponse {
  restaurantId: number
  from: string
  to: string
  totalRevenue: number
  totalOrders?: number
  data: DailyRevenueItem[]
}

export interface RevenueReportParams {
  restaurantId: number
  from: string // YYYY-MM-DD
  to: string   // YYYY-MM-DD
}

export interface TopSellingItem {
  itemId?: number
  id?: number
  menuItemId?: number
  itemName: string
  name?: string
  quantitySold: number
  quantity?: number
  revenue: number
}

export interface TopItemsReportResponse {
  data: TopSellingItem[]
}

export interface TopItemsReportParams {
  restaurantId: number
  from: string
  to: string
  limit?: number
}

/**
 * Lấy báo cáo doanh thu theo chi nhánh và khoảng thời gian
 * GET /api/merchant/reports/revenue?restaurantId=&from=&to=
 */
export async function fetchRevenueReport(params: RevenueReportParams): Promise<RevenueReportResponse> {
  const res = await api.get<RevenueReportResponse>('/merchant/reports/revenue', { params })
  return res.data
}

/**
 * Lấy danh sách món bán chạy theo chi nhánh và khoảng thời gian
 * GET /api/merchant/reports/top-items?restaurantId=&from=&to=&limit=
 */
export async function fetchTopItemsReport(params: TopItemsReportParams): Promise<TopItemsReportResponse> {
  const res = await api.get<TopItemsReportResponse>('/merchant/reports/top-items', { params })
  return res.data
}
