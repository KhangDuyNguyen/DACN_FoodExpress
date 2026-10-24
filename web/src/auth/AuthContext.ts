import { createContext } from 'react'
import type { AuthResponse, User } from '../api/auth'

export interface AuthContextValue {
  user: User | null
  token: string | null
  // true khi co token va dang goi /users/me de khoi phuc dang nhap
  loading: boolean
  login: (res: AuthResponse) => void
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
