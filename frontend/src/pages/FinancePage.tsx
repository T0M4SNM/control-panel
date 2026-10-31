import { useState } from 'react'
import {
  createMovement,
  deleteMovement,
  financeSummary,
  listMovements,
  RECURRENCE_LABELS,
  updateMovement,
  type Movement,
  type MovementInput,
  type MovementType,
  type Recurrence,
  type Totals,
} from '../api/finance'
import { listProjects } from '../api/projects'
import ProfitChart from '../components/ProfitChart'
import { Badge, Button, Card, ConfirmModal, EmptyState, ErrorText, Field, Input, Loading, Modal, PageHeader, Select } from '../components/ui'
import { formatDate, formatEuros } from '../format'
import { errorMessage, useLoad } from '../hooks'

/** Finanzas (solo admin): resumen del año, beneficio por proyecto e ingresos y gastos. */
export default function FinancePage() {
  const [year, setYear] = useState(() => new Date().getFullYear())
  const summary = useLoad(() => financeSummary(year), [year])
  const movements = useLoad(listMovements)
  const [editing, setEditing] = useState<Movement | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Movement | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const reloadAll = () => {
    summary.reload()
    movements.reload()
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteMovement(deleting.id)
      setDeleting(null)
      reloadAll()
    } catch (err) {
      setDeleteError(errorMessage(err))
    }
  }

  const s = summary.data

  return (
    <div className="space-y-6">
      <PageHeader title="Finanzas">
        <div className="flex items-center gap-1">
          <Button variant="ghost" onClick={() => setYear((y) => y - 1)} aria-label="Año anterior">
            ←
          </Button>
          <span className="w-14 text-center font-semibold tabular-nums">{year}</span>
          <Button variant="ghost" onClick={() => setYear((y) => y + 1)} aria-label="Año siguiente">
            →
          </Button>
        </div>
        <Button variant="primary" onClick={() => setEditing('new')}>
          Nuevo ingreso o gasto
        </Button>
      </PageHeader>

      {summary.error && <ErrorText>{summary.error}</ErrorText>}
      {!s && !summary.error && <Loading />}
      {s && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat label="Ingresos" value={s.totals.income} />
            <Stat label="Gastos y contratos" value={-(s.totals.expenses + s.totals.contractCosts)} />
            <Stat label="Beneficio" value={s.totals.profit} emphasis />
            <Stat label="Con tu tiempo" value={s.totals.realProfit} hint={`${formatEuros(s.totals.timeCosts)} en horas`} />
          </div>

          <Card className="p-5">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <h2 className="font-semibold">Beneficio por mes</h2>
              <div className="flex gap-3 text-xs text-slate-500">
                <Legend color="var(--chart-positive)" label="Gana" />
                <Legend color="var(--chart-negative)" label="Pierde" />
                <Legend color="var(--chart-positive)" label="Previsión" faded />
              </div>
            </div>
            <ProfitChart months={s.months} />
            <p className="mt-3 text-xs text-slate-500">
              El coste de contratos de cada mes es el actual: en meses pasados es una aproximación.
            </p>
          </Card>

          <Card className="overflow-x-auto p-5">
            <h2 className="mb-3 font-semibold">Por proyecto</h2>
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-slate-500">
                <tr>
                  <th className="py-1 pr-3 font-medium">Proyecto</th>
                  <Th>Ingresos</Th>
                  <Th>Gastos</Th>
                  <Th>Contratos</Th>
                  <Th>Tiempo</Th>
                  <Th>Beneficio</Th>
                </tr>
              </thead>
              <tbody>
                {s.projects.map((p) => (
                  <ProjectRow key={p.projectId} name={p.projectName} totals={p} />
                ))}
                <ProjectRow name="Sin asignar" totals={s.unassigned} muted />
              </tbody>
            </table>
          </Card>
        </>
      )}

      <section>
        <h2 className="mb-3 font-semibold">Ingresos y gastos</h2>
        <p className="mb-3 text-sm text-slate-600 dark:text-slate-400">
          Lo que no es un contrato: cobros a clientes, compras puntuales, licencias… Los contratos ya cuentan solos.
        </p>
        {movements.error && <ErrorText>{movements.error}</ErrorText>}
        {movements.data?.length === 0 && <EmptyState>Aún no hay ingresos ni gastos.</EmptyState>}
        {movements.data && movements.data.length > 0 && (
          <Card className="divide-y divide-slate-100 dark:divide-slate-800">
            {movements.data.map((m) => (
              <div key={m.id} className="flex flex-wrap items-center gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">{m.description}</p>
                  <p className="text-sm text-slate-500">
                    {formatDate(m.date)}
                    {m.recurrence !== 'ONE_TIME' && ` · ${RECURRENCE_LABELS[m.recurrence].toLowerCase()}`}
                    {m.endsOn && ` hasta ${formatDate(m.endsOn)}`}
                    {m.project && ` · ${m.project.name}`}
                  </p>
                </div>
                <span className={`font-semibold tabular-nums ${m.type === 'INCOME' ? 'text-green-700 dark:text-green-400' : ''}`}>
                  {m.type === 'INCOME' ? '+' : '−'}
                  {formatEuros(m.amount)}
                </span>
                <Badge tone={m.type === 'INCOME' ? 'green' : 'slate'}>{m.type === 'INCOME' ? 'Ingreso' : 'Gasto'}</Badge>
                <div className="flex gap-1">
                  <Button variant="ghost" onClick={() => setEditing(m)}>
                    Editar
                  </Button>
                  <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => setDeleting(m)}>
                    Borrar
                  </Button>
                </div>
              </div>
            ))}
          </Card>
        )}
      </section>

      {editing && (
        <MovementForm
          movement={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reloadAll()
          }}
        />
      )}
      <ConfirmModal
        open={deleting !== null}
        title="Borrar"
        message={`Se borrará «${deleting?.description}».`}
        error={deleteError}
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(null)
          setDeleteError(null)
        }}
      />
    </div>
  )
}

function Stat({ label, value, hint, emphasis }: { label: string; value: number; hint?: string; emphasis?: boolean }) {
  return (
    <Card className="p-4">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${emphasis && value < 0 ? 'text-red-600 dark:text-red-400' : ''}`}>
        {formatEuros(value)}
      </p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </Card>
  )
}

function Legend({ color, label, faded }: { color: string; label: string; faded?: boolean }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className="inline-block size-2.5 rounded-sm" style={{ background: color, opacity: faded ? 0.4 : 1 }} />
      {label}
    </span>
  )
}

function Th({ children }: { children: string }) {
  return <th className="py-1 pl-3 text-right font-medium">{children}</th>
}

function ProjectRow({ name, totals, muted }: { name: string; totals: Totals; muted?: boolean }) {
  const cell = 'py-2 pl-3 text-right tabular-nums'
  return (
    <tr className={`border-t border-slate-100 dark:border-slate-800 ${muted ? 'text-slate-500' : ''}`}>
      <td className="py-2 pr-3">{name}</td>
      <td className={cell}>{formatEuros(totals.income)}</td>
      <td className={cell}>{formatEuros(totals.expenses)}</td>
      <td className={cell}>{formatEuros(totals.contractCosts)}</td>
      <td className={cell}>{formatEuros(totals.timeCosts)}</td>
      <td className={`${cell} font-semibold ${totals.profit < 0 ? 'text-red-600 dark:text-red-400' : ''}`}>{formatEuros(totals.profit)}</td>
    </tr>
  )
}

function MovementForm({ movement, onClose, onSaved }: { movement: Movement | null; onClose: () => void; onSaved: () => void }) {
  const projects = useLoad(listProjects)
  const [form, setForm] = useState<MovementInput>(() => ({
    type: movement?.type ?? 'INCOME',
    amount: movement?.amount ?? 0,
    date: movement?.date ?? new Date().toISOString().slice(0, 10),
    description: movement?.description ?? '',
    projectId: movement?.project?.id ?? null,
    contractId: movement?.contract?.id ?? null,
    recurrence: movement?.recurrence ?? 'ONE_TIME',
    endsOn: movement?.endsOn ?? null,
  }))
  const [error, setError] = useState<string | null>(null)
  const set = <K extends keyof MovementInput>(key: K, value: MovementInput[K]) => setForm((f) => ({ ...f, [key]: value }))

  async function save() {
    setError(null)
    try {
      const input = { ...form, endsOn: form.recurrence === 'ONE_TIME' ? null : form.endsOn }
      await (movement ? updateMovement(movement.id, input) : createMovement(input))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal
      title={movement ? 'Editar' : 'Nuevo ingreso o gasto'}
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={!form.description.trim() || form.amount <= 0}>
            Guardar
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Tipo">
          <Select value={form.type} onChange={(e) => set('type', e.target.value as MovementType)}>
            <option value="INCOME">Ingreso</option>
            <option value="EXPENSE">Gasto</option>
          </Select>
        </Field>
        <Field label="Importe (€, IVA incluido)">
          <Input type="number" min={0} step="0.01" value={form.amount || ''} onChange={(e) => set('amount', Number(e.target.value))} />
        </Field>
      </div>
      <Field label="Concepto">
        <Input value={form.description} onChange={(e) => set('description', e.target.value)} placeholder="Cuota mensual del cliente" />
      </Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Fecha">
          <Input type="date" value={form.date} onChange={(e) => set('date', e.target.value)} />
        </Field>
        <Field label="Proyecto">
          <Select value={form.projectId ?? ''} onChange={(e) => set('projectId', e.target.value || null)}>
            <option value="">— General —</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Se repite">
          <Select value={form.recurrence} onChange={(e) => set('recurrence', e.target.value as Recurrence)}>
            {(Object.keys(RECURRENCE_LABELS) as Recurrence[]).map((r) => (
              <option key={r} value={r}>
                {RECURRENCE_LABELS[r]}
              </option>
            ))}
          </Select>
        </Field>
        {form.recurrence !== 'ONE_TIME' && (
          <Field label="Hasta (opcional)">
            <Input type="date" value={form.endsOn ?? ''} onChange={(e) => set('endsOn', e.target.value || null)} />
          </Field>
        )}
      </div>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
