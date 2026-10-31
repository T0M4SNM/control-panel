import { useEffect, useState } from 'react'
import { listProjects } from '../api/projects'
import {
  createTimeEntry,
  currentTimer,
  deleteTimeEntry,
  listTimeEntries,
  startTimer,
  stopTimer,
  updateTimeEntry,
  type TimeEntry,
} from '../api/time'
import { useAuth } from '../auth-context'
import { Button, Card, ConfirmModal, EmptyState, ErrorText, Field, Input, Loading, Modal, PageHeader, Select } from '../components/ui'
import { formatDuration, formatEuros } from '../format'
import { errorMessage, useLoad } from '../hooks'

const dayFormat = new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })
const timeFormat = new Intl.DateTimeFormat('es-ES', { hour: '2-digit', minute: '2-digit' })

/** «2026-10-31» en hora local (no UTC, para no cambiar de día de noche). */
function localDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function daysAgo(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() - days)
  return localDate(d)
}

/** Cronómetro y registro de tiempo. Cada uno ve el suyo; el admin, el de todos y su coste. */
export default function TimePage() {
  const { isAdmin, user } = useAuth()
  const [range, setRange] = useState(() => ({ from: daysAgo(30), to: localDate(new Date()) }))
  const timer = useLoad(currentTimer)
  const entries = useLoad(() => listTimeEntries(range.from, range.to), [range.from, range.to])
  const [editing, setEditing] = useState<TimeEntry | 'new' | null>(null)
  const [deleting, setDeleting] = useState<TimeEntry | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const reloadAll = () => {
    timer.reload()
    entries.reload()
  }

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteTimeEntry(deleting.id)
      setDeleting(null)
      reloadAll()
    } catch (err) {
      setDeleteError(errorMessage(err))
    }
  }

  const list = entries.data ?? []
  const totalSeconds = list.reduce((sum, e) => sum + e.durationSeconds, 0)
  const totalCost = list.reduce((sum, e) => sum + (e.cost ?? 0), 0)
  const byDay = new Map<string, TimeEntry[]>()
  for (const e of list) {
    const day = localDate(new Date(e.startedAt))
    byDay.set(day, [...(byDay.get(day) ?? []), e])
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Tiempo">
        <Button onClick={() => setEditing('new')}>Añadir a mano</Button>
      </PageHeader>

      <TimerCard running={timer.data ?? null} loading={timer.loading && timer.data === null} onChanged={reloadAll} />

      <div className="flex flex-wrap items-end gap-3">
        <Field label="Desde">
          <Input type="date" value={range.from} onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} />
        </Field>
        <Field label="Hasta">
          <Input type="date" value={range.to} onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} />
        </Field>
        <p className="pb-2 text-sm text-slate-600 dark:text-slate-400">
          Total: <strong>{formatDuration(totalSeconds)}</strong>
          {isAdmin && totalCost > 0 && <> · {formatEuros(totalCost)}</>}
        </p>
      </div>

      {entries.error && <ErrorText>{entries.error}</ErrorText>}
      {entries.loading && !entries.data && <Loading />}
      {entries.data?.length === 0 && <EmptyState>No hay tiempo registrado en estas fechas.</EmptyState>}

      {[...byDay.entries()].map(([day, dayEntries]) => (
        <section key={day}>
          <h2 className="mb-2 flex justify-between text-sm font-semibold">
            <span className="first-letter:uppercase">{dayFormat.format(new Date(`${day}T00:00:00`))}</span>
            <span className="tabular-nums">{formatDuration(dayEntries.reduce((s, e) => s + e.durationSeconds, 0))}</span>
          </h2>
          <Card className="divide-y divide-slate-100 dark:divide-slate-800">
            {dayEntries.map((e) => (
              <div key={e.id} className="flex flex-wrap items-center gap-3 px-5 py-3 text-sm">
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {e.project.name}
                    {isAdmin && e.user.id !== user.id && <span className="font-normal text-slate-500"> · {e.user.name}</span>}
                  </p>
                  {e.description && <p className="truncate text-slate-500">{e.description}</p>}
                </div>
                <span className="text-slate-500 tabular-nums">
                  {timeFormat.format(new Date(e.startedAt))}–{e.endedAt ? timeFormat.format(new Date(e.endedAt)) : 'ahora'}
                </span>
                <span className="w-20 text-right font-medium tabular-nums">{formatDuration(e.durationSeconds)}</span>
                {e.cost !== undefined && <span className="w-20 text-right text-slate-500 tabular-nums">{formatEuros(e.cost)}</span>}
                <div className="flex gap-1">
                  <Button variant="ghost" onClick={() => setEditing(e)} disabled={e.running}>
                    Editar
                  </Button>
                  <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => setDeleting(e)}>
                    Borrar
                  </Button>
                </div>
              </div>
            ))}
          </Card>
        </section>
      ))}

      {editing && (
        <EntryForm
          entry={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reloadAll()
          }}
        />
      )}
      <ConfirmModal
        open={deleting !== null}
        title="Borrar tiempo"
        message="Se borrará esta entrada de tiempo."
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

/** Iniciar o parar el cronómetro. Iniciar otro con uno en marcha para el anterior. */
function TimerCard({ running, loading, onChanged }: { running: TimeEntry | null; loading: boolean; onChanged: () => void }) {
  const projects = useLoad(listProjects)
  const [projectId, setProjectId] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const editable = projects.data?.filter((p) => p.permissions.edit) ?? []

  useEffect(() => {
    if (!running) return
    const tick = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(tick)
  }, [running])

  async function act(action: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setDescription('')
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Loading />

  return (
    <Card className="p-5">
      {running ? (
        <div className="flex flex-wrap items-center gap-4">
          <span className="size-2.5 animate-pulse rounded-full bg-sky-500" />
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{running.project.name}</p>
            {running.description && <p className="text-sm text-slate-500">{running.description}</p>}
          </div>
          <span className="font-mono text-2xl tabular-nums">
            {clock(Math.max(0, Math.floor((now - new Date(running.startedAt).getTime()) / 1000)))}
          </span>
          <Button variant="danger" onClick={() => act(stopTimer)} disabled={busy}>
            Parar
          </Button>
        </div>
      ) : (
        <div className="flex flex-wrap items-end gap-3">
          <Field label="Proyecto">
            <Select value={projectId} onChange={(e) => setProjectId(e.target.value)} className="min-w-48">
              <option value="">— Elige —</option>
              {editable.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </Field>
          <div className="min-w-48 flex-1">
            <Field label="En qué trabajas">
              <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Opcional" />
            </Field>
          </div>
          <Button variant="primary" onClick={() => act(() => startTimer(projectId, description.trim() || null))} disabled={busy || !projectId}>
            Iniciar
          </Button>
        </div>
      )}
      {projects.data && editable.length === 0 && !running && (
        <p className="mt-2 text-xs text-slate-500">Solo se registra tiempo en proyectos donde eres propietario o editor.</p>
      )}
      {error && <ErrorText>{error}</ErrorText>}
    </Card>
  )
}

/** 3725 → «1:02:05» */
function clock(seconds: number): string {
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

/** «2026-10-31» + «09:30» en hora local → instante ISO */
function toInstant(date: string, time: string): string {
  return new Date(`${date}T${time}:00`).toISOString()
}

function EntryForm({ entry, onClose, onSaved }: { entry: TimeEntry | null; onClose: () => void; onSaved: () => void }) {
  const projects = useLoad(listProjects)
  const start = entry ? new Date(entry.startedAt) : null
  const end = entry?.endedAt ? new Date(entry.endedAt) : null
  const hhmm = (d: Date) => `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  const [form, setForm] = useState(() => ({
    projectId: entry?.project.id ?? '',
    date: start ? localDate(start) : localDate(new Date()),
    from: start ? hhmm(start) : '09:00',
    to: end ? hhmm(end) : '10:00',
    description: entry?.description ?? '',
  }))
  const [error, setError] = useState<string | null>(null)
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function save() {
    setError(null)
    const input = {
      projectId: form.projectId,
      startedAt: toInstant(form.date, form.from),
      endedAt: toInstant(form.date, form.to),
      description: form.description.trim() || null,
    }
    try {
      await (entry ? updateTimeEntry(entry.id, input) : createTimeEntry(input))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal
      title={entry ? 'Editar tiempo' : 'Añadir tiempo a mano'}
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={!form.projectId}>
            Guardar
          </Button>
        </>
      }
    >
      <Field label="Proyecto">
        <Select value={form.projectId} onChange={(e) => set('projectId', e.target.value)}>
          <option value="">— Elige —</option>
          {projects.data
            ?.filter((p) => p.permissions.edit)
            .map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
        </Select>
      </Field>
      <div className="grid grid-cols-3 gap-4">
        <Field label="Día">
          <Input type="date" value={form.date} onChange={(e) => set('date', e.target.value)} />
        </Field>
        <Field label="De">
          <Input type="time" value={form.from} onChange={(e) => set('from', e.target.value)} />
        </Field>
        <Field label="A">
          <Input type="time" value={form.to} onChange={(e) => set('to', e.target.value)} />
        </Field>
      </div>
      <Field label="Descripción">
        <Input value={form.description} onChange={(e) => set('description', e.target.value)} />
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
