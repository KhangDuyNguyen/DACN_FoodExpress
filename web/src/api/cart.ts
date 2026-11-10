import { api } from './client'

// Khop voi CartResponse / CartItemResponse o backend (package cart)
export interface CartItem {
  id: number
  menuItemId: number
  name: string
  imageUrl: string | null
  unitPrice: number
  quantity: number
  note: string | null
  lineTotal: number
  isAvailable: boolean // false: mon da het hoac nhom mon bi tat, phai xoa khoi gio moi dat duoc
}

export interface Cart {
  restaurantId: number | null // null khi gio trong
  restaurantName: string | null
  minOrderAmount: number
  items: CartItem[]
  itemCount: number // tong so phan, hien tren icon gio hang
  subtotal: number
}

export interface AddCartItemBody {
  menuItemId: number
  quantity: number
  note?: string
  replaceCart?: boolean // true: dong y xoa gio cua quan khac
}

// Dung chung 1 key de moi trang (header, chi tiet quan, gio, thanh toan) cung doc 1 ban gio
export const cartQueryKey = ['cart']

export async function fetchCart(): Promise<Cart> {
  const res = await api.get<Cart>('/cart')
  return res.data
}

export async function addCartItem(body: AddCartItemBody): Promise<Cart> {
  const res = await api.post<Cart>('/cart/items', body)
  return res.data
}

export async function updateCartItem(id: number, body: { quantity: number; note: string | null }): Promise<Cart> {
  const res = await api.patch<Cart>(`/cart/items/${id}`, body)
  return res.data
}

export async function removeCartItem(id: number): Promise<Cart> {
  const res = await api.delete<Cart>(`/cart/items/${id}`)
  return res.data
}
