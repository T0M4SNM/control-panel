import { api } from './client'

export interface Ref {
  id: string
  name: string
}

export interface Domain {
  id: string
  name: string
  parent: Ref | null
  server: Ref | null
  project: Ref | null
  registrar: string | null
  notes: string | null
  /** Solo para el admin; en un subdominio es el del dominio raíz (inherited) */
  contract?: { id: string; name: string; renewalDate: string; inherited: boolean }
}

export interface DomainInput {
  name: string
  parentId: string | null
  contractId: string | null
  serverId: string | null
  projectId: string | null
  registrar: string | null
  notes: string | null
}

export const listDomains = () => api<Domain[]>('/api/domains')

export const createDomain = (input: DomainInput) =>
  api<Domain>('/api/domains', { method: 'POST', body: JSON.stringify(input) })

export const updateDomain = (id: string, input: DomainInput) =>
  api<Domain>(`/api/domains/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteDomain = (id: string) => api<void>(`/api/domains/${id}`, { method: 'DELETE' })
