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
