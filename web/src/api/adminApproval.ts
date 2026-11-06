import { api } from './client'

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface AdminMerchant {
  id: number
  ownerUserId: number
  ownerFullName: string
  ownerEmail: string | null
  ownerPhone: string
  businessName: string
  taxCode: string | null
  contactPhone: string | null
  status: ApprovalStatus
  totalRestaurants: number
  createdAt: string
  updatedAt: string | null
}

export interface AdminRestaurant {
  id: number
  merchantId: number
  merchantBusinessName: string
  merchantOwnerUserId: number
  merchantOwnerName: string
  merchantOwnerPhone: string
  merchantStatus: ApprovalStatus
  name: string
  description: string | null
  phone: string | null
  address: string
  lat: number
  lng: number
  logoUrl: string | null
  coverUrl: string | null
  openStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED'
  status: ApprovalStatus
  minOrderAmount: number
  prepTimeMinutes: number
  avgRating: number
  ratingCount: number
  createdAt: string
  updatedAt: string | null
}

export interface AdminDriver {
  id: number
  userId: number
  fullName: string
  phone: string
  email: string | null
  avatarUrl: string | null
  vehicleType: 'MOTORBIKE' | 'BICYCLE' | 'CAR'
  licensePlate: string | null
  status: ApprovalStatus
  isOnline: boolean
  currentLat: number | null
  currentLng: number | null
  avgRating: number
  createdAt: string
}

export interface ApprovalStatusRequest {
  status: ApprovalStatus
  reason?: string
}

// ================= MERCHANTS =================
export async function fetchAdminMerchants(params?: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<AdminMerchant>> {
  const res = await api.get<PageResponse<AdminMerchant>>('/admin/merchants', { params })
  return res.data
}

export async function fetchAdminMerchantById(id: number): Promise<AdminMerchant> {
  const res = await api.get<AdminMerchant>(`/admin/merchants/${id}`)
  return res.data
}

export async function updateAdminMerchantStatus(
  id: number,
  status: ApprovalStatus,
  reason?: string
): Promise<AdminMerchant> {
  const res = await api.patch<AdminMerchant>(`/admin/merchants/${id}/status`, { status, reason })
  return res.data
}

// ================= RESTAURANTS =================
export async function fetchAdminRestaurants(params?: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<AdminRestaurant>> {
  const res = await api.get<PageResponse<AdminRestaurant>>('/admin/restaurants', { params })
  return res.data
}

export async function fetchAdminRestaurantById(id: number): Promise<AdminRestaurant> {
  const res = await api.get<AdminRestaurant>(`/admin/restaurants/${id}`)
  return res.data
}

export async function updateAdminRestaurantStatus(
  id: number,
  status: ApprovalStatus,
  reason?: string
): Promise<AdminRestaurant> {
  const res = await api.patch<AdminRestaurant>(`/admin/restaurants/${id}/status`, { status, reason })
  return res.data
}

// ================= DRIVERS =================
export async function fetchAdminDrivers(params?: {
  keyword?: string
  status?: string
  page?: number
  size?: number
}): Promise<PageResponse<AdminDriver>> {
  const res = await api.get<PageResponse<AdminDriver>>('/admin/drivers', { params })
  return res.data
}

export async function fetchAdminDriverById(id: number): Promise<AdminDriver> {
  const res = await api.get<AdminDriver>(`/admin/drivers/${id}`)
  return res.data
}

export async function updateAdminDriverStatus(
  id: number,
  status: ApprovalStatus,
  reason?: string
): Promise<AdminDriver> {
  const res = await api.patch<AdminDriver>(`/admin/drivers/${id}/status`, { status, reason })
  return res.data
}

