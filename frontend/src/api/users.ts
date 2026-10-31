import { api } from './client'

export interface User {
  id: string
  email: string
  displayName: string
  role: 'ADMIN' | 'MEMBER'
  active: boolean
  hourlyRate: number | null
}

/** Solo para el admin */
export const listUsers = () => api<User[]>('/api/users')

/** Solo para el admin; null quita la tarifa */
export const setHourlyRate = (userId: string, hourlyRate: number | null) =>
  api<User>(`/api/users/${userId}/hourly-rate`, { method: 'PUT', body: JSON.stringify({ hourlyRate }) })
