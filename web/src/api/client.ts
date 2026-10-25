import axios from 'axios'

// Dev: Vite proxy chuyen /api sang http://localhost:8080
// Production: dat VITE_API_URL (vi du https://api.example.com/api)
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
})

// Gan JWT vao moi request (token luu o localStorage key "token", xem src/auth/AuthProvider.tsx).
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Gap 401 (token het han / sai): xoa token, chuyen ve /login kem duong dan cu de dang nhap xong quay lai.
// Bo qua /auth/login va /auth/register: sai mat khau cung la 401, loi do hien tren form.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url: string = error.config?.url ?? ''
    if (error.response?.status === 401 && !url.startsWith('/auth/')) {
      localStorage.removeItem('token')
      if (window.location.pathname !== '/login') {
        const current = window.location.pathname + window.location.search
        window.location.href = `/login?redirect=${encodeURIComponent(current)}`
      }
    }
    return Promise.reject(error)
  },
)
