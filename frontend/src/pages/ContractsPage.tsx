import { useState } from 'react'
import {
  CONTRACT_TYPE_LABELS,
  createContract,
  deleteContract,
  listContracts,
  PERIOD_LABELS,
  updateContract,
  type BillingPeriod,
  type Contract,
  type ContractInput,
  type ContractType,
} from '../api/contracts'
import { Badge, Button, Card, Checkbox, ConfirmModal, EmptyState, ErrorText, Field, Input, Loading, Modal, PageHeader, Select, Textarea } from '../components/ui'
import { daysUntil, formatDate, formatDaysLeft, formatEuros } from '../format'
import { errorMessage, useLoad } from '../hooks'

/** Contratos (solo admin): lo que se paga, cada cuánto y cuándo se renueva. */
export default function ContractsPage() {
  const contracts = useLoad(listContracts)
  const [editing, setEditing] = useState<Contract | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Contract | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const active = contracts.data?.filter((c) => c.status === 'ACTIVE') ?? []
  const monthly = active.reduce((sum, c) => sum + c.monthlyAmount, 0)

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteContract(deleting.id)
      setDeleting(null)
      contracts.reload()
    } catch (err) {
      setDeleteError(errorMessage(err))
    }
  }

  return (
    <>
      <PageHeader title="Contratos">
        <Button variant="primary" onClick={() => setEditing('new')}>
          Nuevo contrato
        </Button>
      </PageHeader>
      {contracts.error && <ErrorText>{contracts.error}</ErrorText>}
      {contracts.loading && !contracts.data && <Loading />}
      {contracts.data?.length === 0 && <EmptyState>Aún no hay contratos.</EmptyState>}
      {active.length > 0 && (
        <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
          {active.length} activos · {formatEuros(monthly)} al mes · {formatEuros(monthly * 12)} al año
        </p>
      )}

      <Card className="divide-y divide-slate-100 dark:divide-slate-800">
        {contracts.data?.map((c) => {
          const days = daysUntil(c.renewalDate)
          const cancelled = c.status === 'CANCELLED'
          return (
            <div key={c.id} className={`flex flex-wrap items-center gap-3 px-5 py-3 ${cancelled ? 'opacity-60' : ''}`}>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{c.name}</span>
                  <Badge>{CONTRACT_TYPE_LABELS[c.type]}</Badge>
                  {cancelled && <Badge>Cancelado</Badge>}
                </div>
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  {c.provider} · {formatEuros(c.amount)} {PERIOD_LABELS[c.billingPeriod]}
                  {c.billingPeriod === 'YEARLY' && ` (${formatEuros(c.monthlyAmount)}/mes)`}
                </p>
              </div>
              <div className="text-right text-sm">
                <p>
                  {c.autoRenew ? 'Se renueva' : 'Vence'} el {formatDate(c.renewalDate)}
                </p>
                {!cancelled && <Badge tone={days < 0 || days <= 7 ? 'red' : days <= 30 ? 'amber' : 'slate'}>{formatDaysLeft(days)}</Badge>}
              </div>
              <div className="flex gap-1">
                <Button variant="ghost" onClick={() => setEditing(c)}>
                  Editar
                </Button>
                <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => setDeleting(c)}>
                  Borrar
                </Button>
              </div>
            </div>
          )
        })}
      </Card>

      {editing && (
        <ContractForm
          contract={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            contracts.reload()
          }}
        />
      )}
      <ConfirmModal
        open={deleting !== null}
        title="Borrar contrato"
        message={`Se borrará «${deleting?.name}». Los servidores y dominios que lo usaban se quedan sin contrato. Si solo has dejado de pagarlo, mejor márcalo como cancelado.`}
        error={deleteError}
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(null)
          setDeleteError(null)
        }}
      />
    </>
  )
}

function ContractForm({ contract, onClose, onSaved }: { contract: Contract | null; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState<ContractInput>(() => ({
    name: contract?.name ?? '',
    provider: contract?.provider ?? '',
    type: contract?.type ?? 'SERVER',
    amount: contract?.amount ?? 0,
    billingPeriod: contract?.billingPeriod ?? 'MONTHLY',
    renewalDate: contract?.renewalDate ?? new Date().toISOString().slice(0, 10),
    autoRenew: contract?.autoRenew ?? true,
    status: contract?.status ?? 'ACTIVE',
    notes: contract?.notes ?? null,
  }))
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const set = <K extends keyof ContractInput>(key: K, value: ContractInput[K]) => setForm((f) => ({ ...f, [key]: value }))

  async function save() {
    setSaving(true)
    setError(null)
    try {
      await (contract ? updateContract(contract.id, form) : createContract(form))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  return (
    <Modal
      title={contract ? 'Editar contrato' : 'Nuevo contrato'}
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={saving || !form.name.trim() || !form.provider.trim()}>
            Guardar
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nombre">
          <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="VPS Contabo" />
        </Field>
        <Field label="Proveedor">
          <Input value={form.provider} onChange={(e) => set('provider', e.target.value)} placeholder="Contabo" />
        </Field>
        <Field label="Tipo">
          <Select value={form.type} onChange={(e) => set('type', e.target.value as ContractType)}>
            {(Object.keys(CONTRACT_TYPE_LABELS) as ContractType[]).map((t) => (
              <option key={t} value={t}>
                {CONTRACT_TYPE_LABELS[t]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Estado">
          <Select value={form.status} onChange={(e) => set('status', e.target.value as ContractInput['status'])}>
            <option value="ACTIVE">Activo</option>
            <option value="CANCELLED">Cancelado (ya no cuenta en los costes)</option>
          </Select>
        </Field>
        <Field label="Importe (€, IVA incluido)">
          <Input type="number" min={0} step="0.01" value={form.amount} onChange={(e) => set('amount', Number(e.target.value))} />
        </Field>
        <Field label="Se paga">
          <Select value={form.billingPeriod} onChange={(e) => set('billingPeriod', e.target.value as BillingPeriod)}>
            <option value="MONTHLY">Cada mes</option>
            <option value="YEARLY">Cada año</option>
          </Select>
        </Field>
        <Field label="Próxima renovación">
          <Input type="date" value={form.renewalDate} onChange={(e) => set('renewalDate', e.target.value)} />
        </Field>
      </div>
      <Checkbox label="Se renueva solo" checked={form.autoRenew} onChange={(e) => set('autoRenew', e.target.checked)} />
      <Field label="Notas">
        <Textarea value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value || null)} />
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
