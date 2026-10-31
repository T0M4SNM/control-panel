import { api } from './client'
import type { ProjectStatus } from './projects'

export type CheckStatus = 'UP' | 'DOWN' | 'UNKNOWN'

export interface RunningTimer {
  id: string
  project: { id: string; name: string }
  startedAt: string
  description: string | null
}

export interface Dashboard {
  projects: { total: number; byStatus: Record<ProjectStatus, number> }
  checks: {
    total: number
    byStatus: Record<CheckStatus, number>
    down: { id: string; name: string; target: string; since: string | null }[]
  }
  sslExpiring: { checkId: string; name: string; target: string; expiresAt: string; daysLeft: number }[]
  unreadNotifications: number
  runningTimer?: RunningTimer
  /** Solo para el admin */
  renewals?: {
    id: string
    name: string
    provider: string
    type: 'SERVER' | 'DOMAIN' | 'OTHER'
    renewalDate: string
    daysLeft: number
    amount: number
    billingPeriod: 'MONTHLY' | 'YEARLY'
    autoRenew: boolean
  }[]
  /** Solo para el admin */
  costs?: { totalMonthly: number; totalYearly: number }
  /** Solo para el admin */
  thisMonth?: {
    income: number
    expenses: number
    contractCosts: number
    timeCosts: number
    profit: number
    realProfit: number
  }
}

export function getDashboard(): Promise<Dashboard> {
  return api<Dashboard>('/api/dashboard')
}
