import { isAxiosError } from 'axios'

// Dinh dang loi backend tra ve (ApiError trong common cua Spring Boot)
export interface ApiError {
  status: number
  message: string
  fieldErrors: Record<string, string>
}

// Lay ApiError tu loi Axios. Khong goi duoc server thi tra thong bao chung.
export function getApiError(error: unknown): ApiError {
  if (isAxiosError<ApiError>(error) && error.response?.data?.message) {
    return {
      status: error.response.status,
      message: error.response.data.message,
      fieldErrors: error.response.data.fieldErrors ?? {},
    }
  }
  return { status: 0, message: 'Không kết nối được máy chủ, vui lòng thử lại', fieldErrors: {} }
}
