import { api } from './client'

export interface TimeEntry {
  id: string
  user: { id: string; name: string }
  project: { id: string; name: string }
  startedAt: string
  endedAt: string | null
  running: boolean
  durationSeconds: number
  description: string | null
  /** Solo para el admin */
  hourlyRate?: number | null
  /** Solo para el admin */
  cost?: number
}

export interface TimeEntryInput {
  projectId: string
  startedAt: string
  endedAt: string | null
  description: string | null
}

/** Cronómetro en marcha, o null (la API responde 204). */
export const currentTimer = () => api<TimeEntry | undefined>('/api/timer').then((t) => t ?? null)

export const startTimer = (projectId: string, description: string | null) =>
  api<TimeEntry>('/api/timer/start', { method: 'POST', body: JSON.stringify({ projectId, description }) })

export const stopTimer = () => api<TimeEntry>('/api/timer/stop', { method: 'POST' })

export const listTimeEntries = (from: string, to: string) =>
  api<TimeEntry[]>(`/api/time-entries?from=${from}&to=${to}`)

export const createTimeEntry = (input: TimeEntryInput) =>
  api<TimeEntry>('/api/time-entries', { method: 'POST', body: JSON.stringify(input) })

export const updateTimeEntry = (id: string, input: TimeEntryInput) =>
  api<TimeEntry>(`/api/time-entries/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteTimeEntry = (id: string) => api<void>(`/api/time-entries/${id}`, { method: 'DELETE' })
