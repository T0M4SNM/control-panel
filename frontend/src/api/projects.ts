import { api } from './client'

export type ProjectStatus = 'IDEA' | 'DEVELOPMENT' | 'PRODUCTION' | 'PAUSED'

export type ProjectRole = 'OWNER' | 'EDITOR' | 'VIEWER'

export interface Project {
  id: string
  name: string
  description: string | null
  status: ProjectStatus
  repoUrl: string | null
  publicUrl: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
  /** Rol del usuario actual en el proyecto; null si es un admin que no es miembro. */
  myRole: ProjectRole | null
  permissions: {
    edit: boolean
    manageMembers: boolean
    delete: boolean
  }
}

export function listProjects(): Promise<Project[]> {
  return api<Project[]>('/api/projects')
}

export interface ProjectInput {
  name: string
  description: string | null
  status: ProjectStatus
  repoUrl: string | null
  publicUrl: string | null
  notes: string | null
}

export const getProject = (id: string) => api<Project>(`/api/projects/${id}`)

export const createProject = (input: ProjectInput) =>
  api<Project>('/api/projects', { method: 'POST', body: JSON.stringify(input) })

export const updateProject = (id: string, input: ProjectInput) =>
  api<Project>(`/api/projects/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteProject = (id: string) => api<void>(`/api/projects/${id}`, { method: 'DELETE' })

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  IDEA: 'Idea',
  DEVELOPMENT: 'En desarrollo',
  PRODUCTION: 'En producción',
  PAUSED: 'Pausado',
}
