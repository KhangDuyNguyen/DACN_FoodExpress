import axios from 'axios'

// Dev: Vite proxy chuyen /api sang http://localhost:8080
// Production: dat VITE_API_URL (vi du https://api.example.com/api)
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
})

// Gan JWT vao moi request (Khang lam dang nhap, luu token vao localStorage key "token").
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
