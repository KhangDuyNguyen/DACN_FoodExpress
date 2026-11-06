import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { fetchMe, type AuthResponse } from '../api/auth'
import { AuthContext } from './AuthContext'

const TOKEN_KEY = 'token'

// Giu trang thai dang nhap cho ca web.
// Tai lai trang: con token trong localStorage thi goi /users/me de lay lai thong tin user.
export default function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY))

  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: fetchMe,
    enabled: token !== null,
    retry: false,
    staleTime: Infinity,
  })

  function login(res: AuthResponse) {
    localStorage.setItem(TOKEN_KEY, res.token)
    queryClient.setQueryData(['me'], res.user)
    setToken(res.token)
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY)
    // Xoa het du lieu da tai (thong tin user, gio hang...), de nguoi dang nhap sau khong thay du lieu cua nguoi truoc
    queryClient.clear()
    setToken(null)
  }

  const value = {
    user: token ? (meQuery.data ?? null) : null,
    token,
    loading: token !== null && meQuery.isLoading,
    login,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
