import { api } from './client'

export type ErrorStatus = 'OPEN' | 'RESOLVED'

export interface ErrorGroup {
  id: string
  projectId: string
  projectName: string
  type: string
  message: string
  status: ErrorStatus
  occurrences: number
  firstSeenAt: string
  lastSeenAt: string
  resolvedAt: string | null
}

export interface ErrorEvent {
  id: string
  occurredAt: string
  message: string
  stack: string | null
  environment: string | null
  release: string | null
  /** Objeto JSON en texto (url, method...) */
  context: string | null
}

export interface IngestKey {
  id: string
  name: string
  keyPrefix: string
  createdAt: string
  lastUsedAt: string | null
  revokedAt: string | null
}

export const listErrors = (projectId: string, status: ErrorStatus | 'ALL' = 'OPEN') =>
  api<ErrorGroup[]>(`/api/projects/${projectId}/errors?status=${status}`)

export const getError = (groupId: string) =>
  api<{ group: ErrorGroup; events: ErrorEvent[] }>(`/api/errors/${groupId}`)

export const resolveError = (groupId: string) => api<ErrorGroup>(`/api/errors/${groupId}/resolve`, { method: 'POST' })

export const reopenError = (groupId: string) => api<ErrorGroup>(`/api/errors/${groupId}/reopen`, { method: 'POST' })

export const listIngestKeys = (projectId: string) => api<IngestKey[]>(`/api/projects/${projectId}/ingest-keys`)

/** Devuelve la clave en claro en {@code key}: es la única vez que se puede ver. */
export const createIngestKey = (projectId: string, name: string) =>
  api<{ info: IngestKey; key: string }>(`/api/projects/${projectId}/ingest-keys`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })

export const revokeIngestKey = (keyId: string) => api<void>(`/api/ingest-keys/${keyId}`, { method: 'DELETE' })
