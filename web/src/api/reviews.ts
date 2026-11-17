import { api } from './client'
import type { PageResponse } from './page'

// ── Types ──────────────────────────────────────────────────────────────────

export interface ReviewReply {
  id: number
  merchantName: string
  content: string
  createdAt: string
}

export interface Review {
  id: number
  orderId: number
  customerName: string
  restaurantRating: number
  driverRating: number | null
  comment: string | null
  createdAt: string
  reply: ReviewReply | null
}

export interface CreateReviewRequest {
  restaurantRating: number
  driverRating?: number | null
  comment?: string
}

// ── API calls ──────────────────────────────────────────────────────────────

/** Khách tạo review cho đơn DELIVERED */
export async function createReview(orderId: number, data: CreateReviewRequest): Promise<Review> {
  const res = await api.post<Review>(`/orders/${orderId}/review`, data)
  return res.data
}

/** Khách xem review đã viết của đơn */
export async function getOrderReview(orderId: number): Promise<Review> {
  const res = await api.get<Review>(`/orders/${orderId}/review`)
  return res.data
}

/** Danh sách reviews của quán (public) */
export async function getRestaurantReviews(
  restaurantId: number,
  page = 0,
  size = 10
): Promise<PageResponse<Review>> {
  const res = await api.get<PageResponse<Review>>(`/restaurants/${restaurantId}/reviews`, {
    params: { page, size },
  })
  return res.data
}

/** Merchant xem reviews của quán mình */
export async function getMerchantReviews(
  restaurantId: number,
  page = 0,
  size = 10
): Promise<PageResponse<Review>> {
  const res = await api.get<PageResponse<Review>>(`/merchant/restaurants/${restaurantId}/reviews`, {
    params: { page, size },
  })
  return res.data
}

/** Merchant trả lời review */
export async function replyReview(reviewId: number, content: string): Promise<ReviewReply> {
  const res = await api.post<ReviewReply>(`/merchant/reviews/${reviewId}/reply`, { content })
  return res.data
}
