import { api } from './client'

export type InteractionType = 'VIEW' | 'ADD_TO_CART' | 'ORDER' | 'FAVORITE'

export interface RecordInteractionBody {
  menuItemId: number
  type: InteractionType
  quantity?: number
  orderId?: number
  eventId?: string
}

export interface RecommendedItemDTO {
  id: number
  name: string
  description: string | null
  price: number
  imageUrl: string | null
  isAvailable: boolean
  restaurantId: number
  restaurantName: string
  restaurantAddress: string
  restaurantOpenStatus: 'OPEN' | 'CLOSED' | 'TEMP_CLOSED'
  restaurantAvgRating: number
  score: number
  reason: string
}

export type RecommendationStrategy = 'PERSONALIZED' | 'POPULARITY'

export interface RecommendationResponse {
  strategy: RecommendationStrategy
  fallbackUsed: boolean
  message: string
  items: RecommendedItemDTO[]
}

/**
 * Lấy danh sách món gợi ý cho bạn (hỗ trợ cả khi chưa đăng nhập -> POPULARITY).
 * GET /api/ai/recommendations
 */
export async function fetchRecommendations(params?: {
  limit?: number
  restaurantId?: number
}): Promise<RecommendationResponse> {
  const res = await api.get<RecommendationResponse>('/ai/recommendations', { params })
  return res.data
}

/**
 * Ghi nhận hành vi người dùng (VIEW, ADD_TO_CART, ORDER, FAVORITE).
 * Không ném lỗi để tránh làm chậm hoặc gián đoạn trải nghiệm đặt món.
 */
export async function trackInteraction(body: RecordInteractionBody): Promise<void> {
  try {
    await api.post('/interactions', body)
  } catch {
    // Bỏ qua lỗi tracking để không ảnh hưởng luồng xem menu / thêm giỏ hàng
  }
}

