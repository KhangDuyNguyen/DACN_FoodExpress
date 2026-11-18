import { api } from './client'

// Khop voi AddressRequest / AddressResponse o backend (package user)
export interface Address {
  id: number
  label: string | null // ten goi nho: "Nha rieng", "Cong ty"...
  receiverName: string
  receiverPhone: string
  addressLine: string
  lat: number
  lng: number
  isDefault: boolean
}

export interface AddressBody {
  label?: string
  receiverName: string
  receiverPhone: string
  addressLine: string
  lat: number
  lng: number
  isDefault: boolean
}

// Dung chung 1 key cho trang so dia chi va trang thanh toan
export const addressesQueryKey = ['addresses']

export async function fetchMyAddresses(): Promise<Address[]> {
  const res = await api.get<Address[]>('/users/me/addresses')
  return res.data
}

export async function createAddress(body: AddressBody): Promise<Address> {
  const res = await api.post<Address>('/users/me/addresses', body)
  return res.data
}

export async function updateAddress(id: number, body: AddressBody): Promise<Address> {
  const res = await api.put<Address>(`/users/me/addresses/${id}`, body)
  return res.data
}

export async function setDefaultAddress(id: number): Promise<Address> {
  const res = await api.patch<Address>(`/users/me/addresses/${id}/default`)
  return res.data
}

export async function deleteAddress(id: number): Promise<void> {
  await api.delete(`/users/me/addresses/${id}`)
}
