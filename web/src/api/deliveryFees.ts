import { api } from './client'

// Khop voi AdminDeliveryFeeController o backend (package delivery, tuan 6).
// Dat hang dung quy tac DANG BAT MOI NHAT (applied = true).

export interface DeliveryFeeRule {
  id: number
  name: string
  baseFee: number
  baseDistanceKm: number
  perKmFee: number
  active: boolean
  applied: boolean
  createdAt: string
}

export interface DeliveryFeeRuleBody {
  name: string
  baseFee: number
  baseDistanceKm: number
  perKmFee: number
}

export const deliveryFeeRulesQueryKey = ['admin-delivery-fee-rules']

export async function fetchDeliveryFeeRules(): Promise<DeliveryFeeRule[]> {
  const res = await api.get<DeliveryFeeRule[]>('/admin/delivery-fee-rules')
  return res.data
}

export async function createDeliveryFeeRule(body: DeliveryFeeRuleBody): Promise<DeliveryFeeRule> {
  const res = await api.post<DeliveryFeeRule>('/admin/delivery-fee-rules', body)
  return res.data
}

// Tat quy tac dang bat cuoi cung -> 409
export async function setDeliveryFeeRuleActive(id: number, active: boolean): Promise<DeliveryFeeRule> {
  const res = await api.patch<DeliveryFeeRule>(`/admin/delivery-fee-rules/${id}/active`, { active })
  return res.data
}
