import { api } from './client'

export type DiscountType = 'PERCENT' | 'FIXED' | 'FREE_SHIP'
export type VoucherStatus = 'ACTIVE' | 'PAUSED' | 'EXPIRED'

export interface Voucher {
  id: number
  code: string
  title: string
  description: string | null
  discountType: DiscountType
  discountValue: number
  minOrder: number
  maxDiscount: number | null
  startAt: string
  endAt: string
  usageLimit: number | null
  perUserLimit: number
  usedCount: number
  restaurantId: number | null
  restaurantName: string | null
  createdBy: number | null
  status: VoucherStatus
  createdAt: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface VoucherValidateRequest {
  code: string
  restaurantId?: number
  orderAmount: number
  shippingFee?: number
}

export interface VoucherValidateResponse {
  valid: boolean
  message: string
  voucherId: number | null
  code: string | null
  title: string | null
  discountType: DiscountType | null
  discountAmount: number
  finalOrderAmount: number
  finalShippingFee: number
  minOrder: number | null
  maxDiscount: number | null
}

export interface AdminVoucherRequest {
  code: string
  title: string
  description?: string
  discountType: DiscountType
  discountValue: number
  minOrder: number
  maxDiscount?: number
  startAt: string
  endAt: string
  usageLimit?: number
  perUserLimit?: number
  restaurantId?: number
  status?: VoucherStatus
}

export interface MerchantVoucherRequest {
  restaurantId: number
  code: string
  title: string
  description?: string
  discountType: DiscountType
  discountValue: number
  minOrder: number
  maxDiscount?: number
  startAt: string
  endAt: string
  usageLimit?: number
  perUserLimit?: number
  status?: VoucherStatus
}

export interface MerchantRestaurantOption {
  id: number
  name: string
  address: string
}

// Client voucher APIs
export async function fetchAvailableVouchers(restaurantId?: number, orderAmount?: number): Promise<Voucher[]> {
  const params: Record<string, unknown> = {}
  if (restaurantId) params.restaurantId = restaurantId
  if (orderAmount !== undefined) params.orderAmount = orderAmount
  const res = await api.get<Voucher[]>('/vouchers', { params })
  return res.data
}

export async function validateVoucher(data: VoucherValidateRequest): Promise<VoucherValidateResponse> {
  const res = await api.post<VoucherValidateResponse>('/vouchers/validate', data)
  return res.data
}

// Admin voucher APIs
export async function fetchAdminVouchers(params?: {
  keyword?: string
  discountType?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<Voucher>> {
  const res = await api.get<PageResponse<Voucher>>('/admin/vouchers', { params })
  return res.data
}

export async function createAdminVoucher(data: AdminVoucherRequest): Promise<Voucher> {
  const res = await api.post<Voucher>('/admin/vouchers', data)
  return res.data
}

export async function updateAdminVoucher(id: number, data: AdminVoucherRequest): Promise<Voucher> {
  const res = await api.put<Voucher>(`/admin/vouchers/${id}`, data)
  return res.data
}

export async function updateAdminVoucherStatus(id: number, status: VoucherStatus): Promise<void> {
  await api.patch(`/admin/vouchers/${id}/status`, { status })
}

// Merchant voucher APIs
export async function fetchMerchantVouchers(params?: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<Voucher>> {
  const res = await api.get<PageResponse<Voucher>>('/merchant/vouchers', { params })
  return res.data
}

export async function fetchMerchantRestaurants(): Promise<MerchantRestaurantOption[]> {
  const res = await api.get<MerchantRestaurantOption[]>('/merchant/vouchers/restaurants')
  return res.data
}

export async function createMerchantVoucher(data: MerchantVoucherRequest): Promise<Voucher> {
  const res = await api.post<Voucher>('/merchant/vouchers', data)
  return res.data
}

export async function updateMerchantVoucher(id: number, data: MerchantVoucherRequest): Promise<Voucher> {
  const res = await api.put<Voucher>(`/merchant/vouchers/${id}`, data)
  return res.data
}

export async function updateMerchantVoucherStatus(id: number, status: VoucherStatus): Promise<void> {
  await api.patch(`/merchant/vouchers/${id}/status`, { status })
}

