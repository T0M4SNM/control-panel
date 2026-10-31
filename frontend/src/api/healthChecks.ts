import { api } from './client'

export type CheckType = 'HTTP' | 'SSL' | 'PING' | 'TCP_PORT'

export type CheckStatus = 'UP' | 'DOWN' | 'UNKNOWN'

export interface Ref {
  id: string
  name: string
}

export interface HealthCheck {
  id: string
  name: string
  type: CheckType
  target: string
  port: number | null
  server: Ref | null
  domain: Ref | null
  project: Ref | null
  intervalSeconds: number
  timeoutMs: number
  failureThreshold: number
  enabled: boolean
  currentStatus: CheckStatus
  consecutiveFailures: number
  lastCheckedAt: string | null
  statusChangedAt: string | null
}

export interface CheckResult {
  checkedAt: string
  success: boolean
  responseMs: number | null
  statusCode: number | null
  error: string | null
  sslExpiresAt: string | null
}

export interface HealthCheckInput {
  name: string
  type: CheckType
  target: string
  port: number | null
  serverId: string | null
  domainId: string | null
  projectId: string | null
  intervalSeconds: number
  timeoutMs?: number
  failureThreshold: number
  enabled: boolean
}

export const listHealthChecks = () => api<HealthCheck[]>('/api/health-checks')

export const healthCheckResults = (id: string, limit = 20) =>
  api<CheckResult[]>(`/api/health-checks/${id}/results?limit=${limit}`)

export const createHealthCheck = (input: HealthCheckInput) =>
  api<HealthCheck>('/api/health-checks', { method: 'POST', body: JSON.stringify(input) })

export const updateHealthCheck = (id: string, input: HealthCheckInput) =>
  api<HealthCheck>(`/api/health-checks/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteHealthCheck = (id: string) => api<void>(`/api/health-checks/${id}`, { method: 'DELETE' })

export const runHealthCheck = (id: string) => api<CheckResult>(`/api/health-checks/${id}/run`, { method: 'POST' })

export const CHECK_TYPE_LABELS: Record<CheckType, string> = {
  HTTP: 'Web (HTTP)',
  SSL: 'Certificado SSL',
  PING: 'Ping',
  TCP_PORT: 'Puerto TCP',
}
