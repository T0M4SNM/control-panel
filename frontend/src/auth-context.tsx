import { createContext, useContext } from 'react'
import type { CurrentUser } from './api/auth'

export interface Auth {
  user: CurrentUser
  isAdmin: boolean
  /** Vuelve al login (la API respondió 401: la sesión caducó) */
  sessionExpired: () => void
  logout: () => void
}

export const AuthContext = createContext<Auth | null>(null)

/** Usuario y sesión actuales; solo se usa dentro de la parte con sesión iniciada. */
export function useAuth(): Auth {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth fuera de AuthContext')
  }
  return auth
}
