import { api } from './client'

export type ContractType = 'SERVER' | 'DOMAIN' | 'OTHER'

export type BillingPeriod = 'MONTHLY' | 'YEARLY'

export interface Contract {
  id: string
  name: string
  provider: string
  type: ContractType
  amount: number
  billingPeriod: BillingPeriod
  /** Coste mensual equivalente (un anual cuenta como importe / 12) */
  monthlyAmount: number
  renewalDate: string
  autoRenew: boolean
  status: 'ACTIVE' | 'CANCELLED'
  notes: string | null
}

export interface ContractInput {
  name: string
  provider: string
  type: ContractType
  amount: number
  billingPeriod: BillingPeriod
  renewalDate: string
  autoRenew: boolean
  status: 'ACTIVE' | 'CANCELLED'
  notes: string | null
}

export const listContracts = () => api<Contract[]>('/api/contracts')

export const createContract = (input: ContractInput) =>
  api<Contract>('/api/contracts', { method: 'POST', body: JSON.stringify(input) })

export const updateContract = (id: string, input: ContractInput) =>
  api<Contract>(`/api/contracts/${id}`, { method: 'PUT', body: JSON.stringify(input) })

export const deleteContract = (id: string) => api<void>(`/api/contracts/${id}`, { method: 'DELETE' })

export const CONTRACT_TYPE_LABELS: Record<ContractType, string> = {
  SERVER: 'Servidor',
  DOMAIN: 'Dominio',
  OTHER: 'Otro',
}

export const PERIOD_LABELS: Record<BillingPeriod, string> = {
  MONTHLY: 'al mes',
  YEARLY: 'al año',
}
