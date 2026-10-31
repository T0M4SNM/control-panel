import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { getError, listErrors, reopenError, resolveError, type ErrorGroup, type ErrorStatus } from '../api/errors'
import { getProject, listProjects } from '../api/projects'
import { Badge, Button, Card, EmptyState, ErrorText, Field, Loading, PageHeader, Select } from '../components/ui'
import { formatDateTime, formatTimeAgo } from '../format'
import { errorMessage, useLoad } from '../hooks'

const FILTERS: { value: ErrorStatus | 'ALL'; label: string }[] = [
  { value: 'OPEN', label: 'Abiertos' },
  { value: 'RESOLVED', label: 'Resueltos' },
  { value: 'ALL', label: 'Todos' },
]

/** Errores agrupados de las apps, por proyecto. */
export default function ErrorsPage() {
  const [params, setParams] = useSearchParams()
  const projects = useLoad(listProjects)
  const projectId = params.get('proyecto') ?? projects.data?.[0]?.id ?? ''
  const status = (params.get('estado') as ErrorStatus | 'ALL' | null) ?? 'OPEN'
  const errors = useLoad(() => (projectId ? listErrors(projectId, status) : Promise.resolve([])), [projectId, status])

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    next.set(key, value)
    if (key === 'proyecto' && !next.get('estado')) next.set('estado', status)
    setParams(next)
  }

  return (
    <>
      <PageHeader title="Errores" />
      <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
        Excepciones que envían tus apps, agrupadas: el mismo error repetido es una sola fila con su contador. Para
        empezar a recibirlas, crea una clave de envío en la ficha del proyecto.
      </p>
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Field label="Proyecto">
          <Select value={projectId} onChange={(e) => update('proyecto', e.target.value)} className="min-w-48">
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </Field>
        <div className="flex gap-1 pb-0.5" role="group" aria-label="Estado">
          {FILTERS.map((f) => (
            <Button key={f.value} variant={status === f.value ? 'primary' : 'secondary'} onClick={() => update('estado', f.value)}>
              {f.label}
            </Button>
          ))}
        </div>
      </div>

      {errors.error && <ErrorText>{errors.error}</ErrorText>}
      {errors.loading && !errors.data && <Loading />}
      {projects.data?.length === 0 && <EmptyState>No hay proyectos.</EmptyState>}
      {errors.data?.length === 0 && projectId && (
        <EmptyState>{status === 'OPEN' ? 'Sin errores abiertos. 🎉' : 'No hay errores aquí.'}</EmptyState>
      )}

      {errors.data && errors.data.length > 0 && (
        <Card className="divide-y divide-slate-100 dark:divide-slate-800">
          {errors.data.map((g) => (
            <ErrorRow key={g.id} group={g} />
          ))}
        </Card>
      )}
    </>
  )
}

function ErrorRow({ group }: { group: ErrorGroup }) {
  return (
    <Link to={`/errores/${group.id}`} className="flex flex-wrap items-center gap-3 px-5 py-3 hover:bg-slate-50 dark:hover:bg-slate-800/50">
      <div className="min-w-0 flex-1">
        <p className="font-medium">
          {group.type}
          {group.status === 'RESOLVED' && (
            <span className="ml-2">
              <Badge tone="green">Resuelto</Badge>
            </span>
          )}
        </p>
        <p className="truncate text-sm text-slate-600 dark:text-slate-400">{group.message}</p>
      </div>
      <div className="text-right text-sm">
        <p className="font-semibold tabular-nums">{group.occurrences.toLocaleString('es-ES')} ×</p>
        <p className="text-xs text-slate-500">{formatTimeAgo(group.lastSeenAt)}</p>
      </div>
    </Link>
  )
}

/** Un error: su resumen, sus últimas ocurrencias con la pila y marcarlo como resuelto. */
export function ErrorDetailPage() {
  const { id = '' } = useParams()
  const detail = useLoad(() => getError(id), [id])
  const project = useLoad(() => (detail.data ? getProject(detail.data.group.projectId) : Promise.resolve(null)), [detail.data?.group.projectId])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (detail.error) return <ErrorText>{detail.error}</ErrorText>
  if (!detail.data) return <Loading />
  const { group, events } = detail.data
  const canEdit = project.data?.permissions.edit ?? false

  async function toggle() {
    setBusy(true)
    setError(null)
    try {
      await (group.status === 'OPEN' ? resolveError(group.id) : reopenError(group.id))
      detail.reload()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-5">
      <Link to={`/errores?proyecto=${group.projectId}`} className="text-sm text-slate-500 hover:underline">
        ← Errores de {group.projectName}
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold break-all">{group.type}</h1>
            <Badge tone={group.status === 'OPEN' ? 'red' : 'green'}>{group.status === 'OPEN' ? 'Abierto' : 'Resuelto'}</Badge>
          </div>
          <p className="mt-1 break-words text-slate-600 dark:text-slate-400">{group.message}</p>
          <p className="mt-2 text-sm text-slate-500">
            {group.occurrences.toLocaleString('es-ES')} veces · primera {formatDateTime(group.firstSeenAt)} · última{' '}
            {formatDateTime(group.lastSeenAt)}
          </p>
        </div>
        {canEdit && (
          <Button variant={group.status === 'OPEN' ? 'primary' : 'secondary'} onClick={toggle} disabled={busy}>
            {group.status === 'OPEN' ? 'Marcar como resuelto' : 'Reabrir'}
          </Button>
        )}
      </div>
      {group.status === 'OPEN' && canEdit && (
        <p className="text-xs text-slate-500">Si lo marcas como resuelto y vuelve a ocurrir, se reabre solo y te aviso.</p>
      )}
      {error && <ErrorText>{error}</ErrorText>}

      <h2 className="font-semibold">Últimas ocurrencias</h2>
      {events.length === 0 && <p className="text-sm text-slate-500">Las ocurrencias de más de 30 días ya se borraron.</p>}
      <div className="space-y-3">
        {events.map((e, i) => (
          <Card key={e.id} className="p-4">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="font-medium">{formatDateTime(e.occurredAt)}</span>
              {e.environment && <Badge>{e.environment}</Badge>}
              {e.release && <Badge tone="sky">v{e.release}</Badge>}
            </div>
            <Context json={e.context} />
            {e.stack && (
              <details open={i === 0} className="mt-2">
                <summary className="cursor-pointer text-sm text-sky-700 dark:text-sky-400">Pila</summary>
                <pre className="mt-2 overflow-x-auto rounded-lg bg-slate-100 p-3 text-xs leading-relaxed dark:bg-slate-950">{e.stack}</pre>
              </details>
            )}
          </Card>
        ))}
      </div>
    </div>
  )
}

function Context({ json }: { json: string | null }) {
  if (!json) return null
  let entries: [string, string][]
  try {
    entries = Object.entries(JSON.parse(json) as Record<string, string>)
  } catch {
    return null
  }
  return (
    <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-sm">
      {entries.map(([key, value]) => (
        <div key={key} className="contents">
          <dt className="text-slate-500">{key}</dt>
          <dd className="break-all">{value}</dd>
        </div>
      ))}
    </dl>
  )
}
