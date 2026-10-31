import { api, ApiError } from './client'

export type UserRole = 'ADMIN' | 'MEMBER'

export interface CurrentUser {
  id: string
  workspaceId: string
  email: string
  displayName: string
  role: UserRole
}

/** Usuario de la sesión actual, o null si no hay sesión. */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    return await api<CurrentUser>('/api/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

/** Pide la cookie CSRF. Spring Security la renueva al hacer login y logout. */
function refreshCsrfToken(): Promise<void> {
  return api<void>('/api/auth/csrf')
}

export async function login(email: string, password: string): Promise<CurrentUser> {
  await refreshCsrfToken()
  await api<void>('/api/auth/login', {
    method: 'POST',
    body: new URLSearchParams({ email, password }),
  })
  await refreshCsrfToken()
  return api<CurrentUser>('/api/auth/me')
}

export async function logout(): Promise<void> {
  await api<void>('/api/auth/logout', { method: 'POST' })
  await refreshCsrfToken()
}
