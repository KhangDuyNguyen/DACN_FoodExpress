// Kết quả phân trang dùng chung, khớp với PageResponse<T> ở backend (common/PageResponse.java).
// page bắt đầu từ 0.
export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
