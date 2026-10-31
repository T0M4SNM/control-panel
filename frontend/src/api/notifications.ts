import { api } from './client'

export type NotificationType = 'CONTRACT_EXPIRING' | 'CHECK_DOWN' | 'CHECK_RECOVERED' | 'SSL_EXPIRING'

export type Severity = 'INFO' | 'WARNING' | 'CRITICAL'

export interface AppNotification {
  id: string
  type: NotificationType
  severity: Severity
  title: string
  message: string
  contractId: string | null
  healthCheckId: string | null
  createdAt: string
  /** null = sin leer */
  readAt: string | null
}

export function listNotifications(limit = 20): Promise<AppNotification[]> {
  return api<AppNotification[]>(`/api/notifications?limit=${limit}`)
}

export async function unreadCount(): Promise<number> {
  return (await api<{ count: number }>('/api/notifications/unread-count')).count
}

export function markRead(id: string): Promise<AppNotification> {
  return api<AppNotification>(`/api/notifications/${id}/read`, { method: 'POST' })
}

export function markAllRead(): Promise<void> {
  return api<void>('/api/notifications/read-all', { method: 'POST' })
}
