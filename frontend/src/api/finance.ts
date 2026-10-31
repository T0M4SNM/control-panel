import { api } from './client'

export type MovementType = 'INCOME' | 'EXPENSE'

export type Recurrence = 'ONE_TIME' | 'MONTHLY' | 'YEARLY'

export interface Movement {
  id: string
  type: MovementType
  amount: number
  date: string
  description: string
  project: { id: string; name: string } | null
  contract: { id: string; name: string } | null
  recurrence: Recurrence
  endsOn: string | null
}

export interface MovementInput {
  type: MovementType
  amount: number
  date: string
  description: string
  projectId: string | null
  contractId: string | null
  recurrence: Recurrence
  endsOn: string | null
}

export interface Totals {
  income: number
  expenses: number
  contractCosts: number
  timeCosts: number
  /** income - expenses - contractCosts */
  profit: number
  /** profit - timeCosts */
  realProfit: number
}

export interface FinanceSummary {
  from: string
  to: string
  months: (Totals & { month: string; projected: boolean })[]
  totals: Totals
  projects: (Totals & { projectId: string; projectName: string })[]
  unassigned: Totals
}

export const listMovements = () => api<Movement[]>('/api/movements')

export const createMovement = (input: MovementInput) =>
  api<Movement>('/api/movements', { method: 'POST', body: JSON.stringify(input) })

export const updateMovement = (id: string, input: MovementInput) =>
  api<Movement>(`/api/movements/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteMovement = (id: string) => api<void>(`/api/movements/${id}`, { method: 'DELETE' })

export const financeSummary = (year: number) =>
  api<FinanceSummary>(`/api/finance/summary?from=${year}-01&to=${year}-12`)

export const RECURRENCE_LABELS: Record<Recurrence, string> = {
  ONE_TIME: 'Puntual',
  MONTHLY: 'Cada mes',
  YEARLY: 'Cada año',
}
