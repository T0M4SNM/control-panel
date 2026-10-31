import { api } from './client'

export type VisitRange = 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER'

export interface VisitPoint {
  start: string
  visitors: number
  views: number
}

export interface VisitCount {
  name: string
  count: number
}

export interface VisitStats {
  range: VisitRange
  stepSeconds: number
  points: VisitPoint[]
  /** Visitantes distintos; en periodos de varios días, quien vuelve cuenta una vez por día */
  visitors: number
  views: number
  /** Visitantes en los últimos 5 minutos */
  online: number
  topPages: VisitCount[]
  topReferrers: VisitCount[]
}

export const getVisits = (projectId: string, range: VisitRange) =>
  api<VisitStats>(`/api/projects/${projectId}/visits?range=${range}`)

export const VISIT_RANGE_LABELS: Record<VisitRange, string> = {
  HOUR: '1 h',
  DAY: '24 h',
  WEEK: '7 días',
  MONTH: '30 días',
  QUARTER: '90 días',
}
