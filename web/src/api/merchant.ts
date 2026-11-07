import { api } from './client'

// ── Types ──────────────────────────────────────────────────────────────────

export interface MerchantRestaurantDTO {
  id: number
  name: string
  address: string
  openStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED'
  status: 'PENDING' | 'ACTIVE' | 'HIDDEN' | 'SUSPENDED'
}

export interface UpdateRestaurantRequest {
  name: string
  description?: string
  address: string
  phone?: string
  logoUrl?: string
  coverUrl?: string
  minOrderAmount?: number
  prepTimeMinutes?: number
}

export interface HourEntry {
  dayOfWeek: number   // 1=Thứ 2 ... 7=Chủ nhật
  openTime: string    // "08:00"
  closeTime: string   // "22:00"
}

export interface CategoryRequest {
  name: string
  sortOrder: number
}

export interface CategoryDTO {
  id: number
  name: string
  sortOrder: number
  items: never[]
}

export interface MenuItemRequest {
  categoryId: number
  name: string
  description?: string
  price: number
  imageUrl?: string
  isAvailable: boolean
  sortOrder: number
}

export interface MenuItemRow {
  id: number
  name: string
  description: string | null
  price: number
  image_url: string | null
  is_available: boolean | number
  sort_order: number
  category_id: number
  category_name: string
}

export interface OptionGroupRequest {
  name: string
  isRequired: boolean
  minSelect: number
  maxSelect: number
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

export interface OptionRequest {
  name: string
  extraPrice: number
  isAvailable: boolean
  sortOrder: number
}

export interface OptionDTO {
  id: number
  name: string
  extraPrice: number
  isAvailable: boolean
  sortOrder: number
}

// ── API calls ──────────────────────────────────────────────────────────────

// Lấy danh sách quán của merchant đang đăng nhập
export async function fetchMyRestaurants(): Promise<MerchantRestaurantDTO[]> {
  const res = await api.get<MerchantRestaurantDTO[]>('/merchant/restaurants')
  return res.data
}

// Cập nhật thông tin quán
export async function updateRestaurant(id: number, data: UpdateRestaurantRequest): Promise<void> {
  await api.put(`/merchant/restaurants/${id}`, data)
}

// Cập nhật giờ mở cửa
export async function updateHours(id: number, hours: HourEntry[]): Promise<void> {
  await api.put(`/merchant/restaurants/${id}/hours`, { hours })
}

// Mở / đóng quán
export async function updateOpenStatus(id: number, openStatus: string): Promise<void> {
  await api.patch(`/merchant/restaurants/${id}/open-status`, { openStatus })
}

// ── Categories ──────────────────────────────────────────────────────────────

export async function fetchCategories(restaurantId: number): Promise<CategoryDTO[]> {
  const res = await api.get<CategoryDTO[]>(`/merchant/restaurants/${restaurantId}/categories`)
  return res.data
}

export async function createCategory(restaurantId: number, data: CategoryRequest): Promise<{ id: number }> {
  const res = await api.post<{ id: number }>(`/merchant/restaurants/${restaurantId}/categories`, data)
  return res.data
}

export async function updateCategory(restaurantId: number, categoryId: number, data: CategoryRequest): Promise<void> {
  await api.put(`/merchant/restaurants/${restaurantId}/categories/${categoryId}`, data)
}

export async function deleteCategory(restaurantId: number, categoryId: number): Promise<void> {
  await api.delete(`/merchant/restaurants/${restaurantId}/categories/${categoryId}`)
}

// ── Menu Items ──────────────────────────────────────────────────────────────

export async function fetchItems(restaurantId: number): Promise<MenuItemRow[]> {
  const res = await api.get<MenuItemRow[]>(`/merchant/restaurants/${restaurantId}/items`)
  return res.data
}

export async function createItem(restaurantId: number, data: MenuItemRequest): Promise<{ id: number }> {
  const res = await api.post<{ id: number }>(`/merchant/restaurants/${restaurantId}/items`, data)
  return res.data
}

export async function updateItem(restaurantId: number, itemId: number, data: MenuItemRequest): Promise<void> {
  await api.put(`/merchant/restaurants/${restaurantId}/items/${itemId}`, data)
}

export async function deleteItem(restaurantId: number, itemId: number): Promise<void> {
  await api.delete(`/merchant/restaurants/${restaurantId}/items/${itemId}`)
}

export async function patchAvailability(itemId: number, isAvailable: boolean): Promise<void> {
  await api.patch(`/merchant/items/${itemId}/availability`, { isAvailable })
}

// ── Option Groups ───────────────────────────────────────────────────────────

export async function fetchOptionGroups(restaurantId: number, itemId: number): Promise<OptionGroupDTO[]> {
  const res = await api.get<OptionGroupDTO[]>(`/merchant/restaurants/${restaurantId}/items/${itemId}/option-groups`)
  return res.data
}

export async function createOptionGroup(restaurantId: number, itemId: number, data: OptionGroupRequest): Promise<{ id: number }> {
  const res = await api.post<{ id: number }>(`/merchant/restaurants/${restaurantId}/items/${itemId}/option-groups`, data)
  return res.data
}

export async function deleteOptionGroup(restaurantId: number, itemId: number, groupId: number): Promise<void> {
  await api.delete(`/merchant/restaurants/${restaurantId}/items/${itemId}/option-groups/${groupId}`)
}

export async function createOption(restaurantId: number, itemId: number, groupId: number, data: OptionRequest): Promise<{ id: number }> {
  const res = await api.post<{ id: number }>(`/merchant/restaurants/${restaurantId}/items/${itemId}/option-groups/${groupId}/options`, data)
  return res.data
}

export async function deleteOption(restaurantId: number, itemId: number, groupId: number, optionId: number): Promise<void> {
  await api.delete(`/merchant/restaurants/${restaurantId}/items/${itemId}/option-groups/${groupId}/options/${optionId}`)
}
