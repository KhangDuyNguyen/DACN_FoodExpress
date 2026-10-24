import { useContext } from 'react'
import { AuthContext } from './AuthContext'

// Dung trong component: const { user, login, logout } = useAuth()
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phai dung ben trong <AuthProvider>')
  return ctx
}
