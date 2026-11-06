import { api } from './client'

// ── Types ──────────────────────────────────────────────────────────────────

export interface CuisineDTO {
  id: number
  name: string
  iconUrl: string | null
}

export interface RestaurantSummaryDTO {
  id: number
  name: string
  cuisineId: number
  cuisineName: string
  address: string
  lat: number
  lng: number
  logoUrl: string | null
  avgRating: number
  ratingCount: number
  minOrderAmount: number
  prepTimeMinutes: number
  openStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED'
}

export interface RestaurantHoursDTO {
  dayOfWeek: number   // 1=Thứ 2 ... 7=Chủ nhật
  openTime: string    // "08:00:00"
  closeTime: string   // "22:00:00"
}

export interface RestaurantDetailDTO {
  id: number
  name: string
  description: string | null
  phone: string | null
  address: string
  lat: number
  lng: number
  logoUrl: string | null
  coverUrl: string | null
  avgRating: number
  ratingCount: number
  minOrderAmount: number
  prepTimeMinutes: number
  openStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED'
  cuisines: CuisineDTO[]
  hours: RestaurantHoursDTO[]
}

export interface OptionDTO {
  id: number
  name: string
  extraPrice: number
  isAvailable: boolean
  sortOrder: number
}

export interface OptionGroupDTO {
  id: number
  name: string
  isRequired: boolean
  minSelect: number
  maxSelect: number
  sortOrder: number
  options: OptionDTO[]
}

export interface MenuItemDTO {
  id: number
  name: string
  description: string | null
  price: number
  imageUrl: string | null
  isAvailable: boolean
  sortOrder: number
  optionGroups: OptionGroupDTO[]
}

export interface MenuCategoryDTO {
  id: number
  name: string
  sortOrder: number
  items: MenuItemDTO[]
}

export interface MenuDTO {
  restaurantId: number
  categories: MenuCategoryDTO[]
}

// ── API calls ──────────────────────────────────────────────────────────────

export async function fetchCuisines(): Promise<CuisineDTO[]> {
  const res = await api.get<CuisineDTO[]>('/cuisines')
  return res.data
}

export interface RestaurantSearchParams {
  keyword?: string
  cuisine?: number
  lat?: number
  lng?: number
}

export async function fetchRestaurants(params?: RestaurantSearchParams): Promise<RestaurantSummaryDTO[]> {
  const res = await api.get<RestaurantSummaryDTO[]>('/restaurants', { params })
  return res.data
}

export async function fetchRestaurantById(id: number): Promise<RestaurantDetailDTO> {
  const res = await api.get<RestaurantDetailDTO>(`/restaurants/${id}`)
  return res.data
}

export async function fetchRestaurantMenu(id: number): Promise<MenuDTO> {
  const res = await api.get<MenuDTO>(`/restaurants/${id}/menu`)
  return res.data
}
