import { api } from './client'

// Khop voi package payment o backend (thanh toan VNPay, tuan 5)

export interface VnPayReturnResult {
  orderId: number
  orderCode: string
  paymentStatus: 'PENDING' | 'SUCCESS' | 'FAILED'
  success: boolean
  message: string // cau hien cho khach, kem ly do khi that bai
}

// Gui nguyen query string VNPay tra ve (vd "?vnp_Amount=...&vnp_SecureHash=...").
// Server tu kiem tra chu ky; don con cho thi server tu hoi lai VNPay roi moi tra ket qua.
export async function confirmVnPayReturn(search: string): Promise<VnPayReturnResult> {
  const res = await api.get<VnPayReturnResult>(`/payments/vnpay/return${search}`)
  return res.data
}
