import { api } from './client'

export interface Server {
  id: string
  name: string
  provider: string | null
  ipAddress: string | null
  os: string | null
  cpuCores: number | null
  ramMb: number | null
  diskGb: number | null
  notes: string | null
  /** Solo para el admin */
  costs?: {
    contractId: string | null
    contractName: string | null
    monthlyAmount: number
    assignedSharePercent: number
  }
  projects: {
    projectId: string
    projectName: string
    /** Solo para el admin */
    costSharePercent?: number
    /** Solo para el admin */
    monthlyCost?: number
  }[]
}

export interface ServerInput {
  name: string
  provider: string | null
  ipAddress: string | null
  os: string | null
  cpuCores: number | null
  ramMb: number | null
  diskGb: number | null
  notes: string | null
  contractId: string | null
}

export const listServers = () => api<Server[]>('/api/servers')

export const createServer = (input: ServerInput) =>
  api<Server>('/api/servers', { method: 'POST', body: JSON.stringify(input) })

export const updateServer = (id: string, input: ServerInput) =>
  api<Server>(`/api/servers/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteServer = (id: string) => api<void>(`/api/servers/${id}`, { method: 'DELETE' })

export const linkProjectServer = (projectId: string, serverId: string, costSharePercent: number) =>
  api<Server>(`/api/projects/${projectId}/servers/${serverId}`, {
    method: 'PUT',
    body: JSON.stringify({ costSharePercent }),
  })

export const unlinkProjectServer = (projectId: string, serverId: string) =>
  api<void>(`/api/projects/${projectId}/servers/${serverId}`, { method: 'DELETE' })

export const listProjectServers = (projectId: string) => api<Server[]>(`/api/projects/${projectId}/servers`)
