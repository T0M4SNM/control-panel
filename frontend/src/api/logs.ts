import { api } from './client'
import type { IngestKey } from './errors'

export type LogLevel = 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'

export interface LogLine {
  id: number
  source: string
  level: LogLevel | null
  message: string
  occurredAt: string
}

export interface LogSource {
  name: string
  lines: number
  errors: number
  lastAt: string
}

export interface LogFilter {
  source?: string
  /** Ese nivel y los más graves */
  level?: LogLevel
  q?: string
  before?: LogLine
}

export function listLogs(serverId: string, filter: LogFilter): Promise<LogLine[]> {
  const params = new URLSearchParams({ limit: '200' })
  if (filter.source) params.set('source', filter.source)
  if (filter.level) params.set('level', filter.level)
  if (filter.q) params.set('q', filter.q)
  if (filter.before) {
    params.set('beforeTime', filter.before.occurredAt)
    params.set('beforeId', String(filter.before.id))
  }
  return api<LogLine[]>(`/api/servers/${serverId}/logs?${params}`)
}

export const listLogSources = (serverId: string) => api<LogSource[]>(`/api/servers/${serverId}/log-sources`)

export const listServerKeys = (serverId: string) => api<IngestKey[]>(`/api/servers/${serverId}/ingest-keys`)

/** Devuelve la clave en claro en {@code key}: es la única vez que se puede ver. */
export const createServerKey = (serverId: string, name: string) =>
  api<{ info: IngestKey; key: string }>(`/api/servers/${serverId}/ingest-keys`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
