import { useState } from 'react'
import { listDomains, type Domain } from '../api/domains'
import {
  CHECK_TYPE_LABELS,
  createHealthCheck,
  deleteHealthCheck,
  healthCheckResults,
  listHealthChecks,
  runHealthCheck,
  updateHealthCheck,
  type CheckStatus,
  type CheckType,
  type HealthCheck,
  type HealthCheckInput,
} from '../api/healthChecks'
import { listProjects, type Project } from '../api/projects'
import { listServers, type Server } from '../api/servers'
import { useAuth } from '../auth-context'
import {
  Badge,
  Button,
  Card,
  Checkbox,
  ConfirmModal,
  EmptyState,
  ErrorText,
  Field,
  Input,
  Loading,
  Modal,
  PageHeader,
  Select,
} from '../components/ui'
import { formatDateTime, formatTimeAgo } from '../format'
import { errorMessage, useLoad } from '../hooks'

const STATUS: Record<CheckStatus, { label: string; dot: string; tone: 'green' | 'red' | 'slate' }> = {
  UP: { label: 'Responde', dot: 'bg-green-500', tone: 'green' },
  DOWN: { label: 'Caída', dot: 'bg-red-500', tone: 'red' },
  UNKNOWN: { label: 'Sin datos', dot: 'bg-slate-400', tone: 'slate' },
}

const TARGET_PLACEHOLDER: Record<CheckType, string> = {
  HTTP: 'https://stock.t0masdev.es',
  SSL: 'stock.t0masdev.es',
  PING: 'IP o nombre del servidor',
  TCP_PORT: 'IP o nombre del servidor',
}

const INTERVALS = [1, 5, 10, 15, 30, 60]

/** Comprobaciones de estado: lista, histórico y, para el admin, alta, edición y «ejecutar ahora». */
export default function ChecksPage() {
  const { isAdmin } = useAuth()
  const checks = useLoad(listHealthChecks)
  const [editing, setEditing] = useState<HealthCheck | 'new' | null>(null)
  const [deleting, setDeleting] = useState<HealthCheck | null>(null)

  const list = checks.data ?? []
  const down = list.filter((c) => c.enabled && c.currentStatus === 'DOWN').length

  return (
    <>
      <PageHeader title="Comprobaciones">
        {isAdmin && (
          <Button variant="primary" onClick={() => setEditing('new')}>
            Nueva comprobación
          </Button>
        )}
      </PageHeader>

      {checks.error && <ErrorText>{checks.error}</ErrorText>}
      {checks.loading && !checks.data && <Loading />}
      {checks.data && list.length === 0 && (
        <EmptyState>
          {isAdmin
            ? 'Aún no hay comprobaciones. Añade una para vigilar una web, un certificado o un servidor.'
            : 'No hay comprobaciones en tus proyectos.'}
        </EmptyState>
      )}

      {list.length > 0 && (
        <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
          {down === 0 ? 'Todo responde.' : `${down} caída${down === 1 ? '' : 's'}.`} Se comprueban solas según su
          intervalo.
        </p>
      )}

      <ul className="space-y-3">
        {list.map((check) => (
          <li key={check.id}>
            <CheckCard
              check={check}
              isAdmin={isAdmin}
              onChanged={checks.reload}
              onEdit={() => setEditing(check)}
              onDelete={() => setDeleting(check)}
            />
          </li>
        ))}
      </ul>

      {editing && (
        <CheckForm
          check={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            checks.reload()
          }}
        />
      )}
      <DeleteCheck
        check={deleting}
        onClose={() => setDeleting(null)}
        onDeleted={() => {
          setDeleting(null)
          checks.reload()
        }}
      />
    </>
  )
}

function CheckCard({
  check,
  isAdmin,
  onChanged,
  onEdit,
  onDelete,
}: {
  check: HealthCheck
  isAdmin: boolean
  onChanged: () => void
  onEdit: () => void
  onDelete: () => void
}) {
  const [showHistory, setShowHistory] = useState(false)
  const [running, setRunning] = useState(false)
  const [runError, setRunError] = useState<string | null>(null)
  const status = check.enabled ? STATUS[check.currentStatus] : { label: 'Pausada', dot: 'bg-slate-300', tone: 'slate' as const }
  const links = [check.project, check.server, check.domain].filter((l) => l !== null)

  async function runNow() {
    setRunning(true)
    setRunError(null)
    try {
      await runHealthCheck(check.id)
      onChanged()
    } catch (err) {
      setRunError(errorMessage(err))
    } finally {
      setRunning(false)
    }
  }

  return (
    <Card className="p-4">
      <div className="flex flex-wrap items-start gap-3">
        <span className={`mt-1.5 size-2.5 shrink-0 rounded-full ${status.dot}`} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">{check.name}</span>
            <Badge tone={status.tone}>{status.label}</Badge>
            <Badge>{CHECK_TYPE_LABELS[check.type]}</Badge>
          </div>
          <p className="truncate text-sm text-slate-600 dark:text-slate-400">
            {check.target}
            {check.port && `:${check.port}`}
          </p>
          <p className="mt-1 text-xs text-slate-500">
            {check.lastCheckedAt ? `Comprobada ${formatTimeAgo(check.lastCheckedAt)}` : 'Aún no se ha comprobado'}
            {' · '}cada {check.intervalSeconds / 60} min
            {check.consecutiveFailures > 0 && ` · ${check.consecutiveFailures} fallo(s) seguido(s)`}
            {links.length > 0 && ` · ${links.map((l) => l.name).join(', ')}`}
          </p>
          {runError && <ErrorText>{runError}</ErrorText>}
        </div>
        <div className="flex flex-wrap gap-1">
          <Button variant="ghost" onClick={() => setShowHistory((s) => !s)} aria-expanded={showHistory}>
            Historial
          </Button>
          {isAdmin && (
            <>
              <Button variant="ghost" onClick={runNow} disabled={running}>
                {running ? 'Comprobando…' : 'Comprobar ahora'}
              </Button>
              <Button variant="ghost" onClick={onEdit}>
                Editar
              </Button>
              <Button variant="ghost" onClick={onDelete} className="text-red-700 dark:text-red-400">
                Borrar
              </Button>
            </>
          )}
        </div>
      </div>
      {showHistory && <History checkId={check.id} version={check.lastCheckedAt} />}
    </Card>
  )
}

/** Últimos 20 resultados. {@code version} cambia al comprobar de nuevo, para recargar. */
function History({ checkId, version }: { checkId: string; version: string | null }) {
  const results = useLoad(() => healthCheckResults(checkId), [checkId, version])

  if (results.error) return <ErrorText>{results.error}</ErrorText>
  if (!results.data) return <p className="mt-3 text-sm text-slate-500">Cargando…</p>
  if (results.data.length === 0) return <p className="mt-3 text-sm text-slate-500">Aún no hay resultados.</p>

  return (
    <div className="mt-3 overflow-x-auto border-t border-slate-200 pt-3 dark:border-slate-800">
      <table className="w-full text-left text-sm">
        <thead className="text-xs text-slate-500">
          <tr>
            <th className="py-1 pr-3 font-medium">Cuándo</th>
            <th className="py-1 pr-3 font-medium">Resultado</th>
            <th className="py-1 pr-3 font-medium">Tiempo</th>
            <th className="py-1 font-medium">Detalle</th>
          </tr>
        </thead>
        <tbody>
          {results.data.map((r) => (
            <tr key={r.checkedAt} className="border-t border-slate-100 dark:border-slate-800/60">
              <td className="py-1.5 pr-3 whitespace-nowrap">{formatDateTime(r.checkedAt)}</td>
              <td className="py-1.5 pr-3">
                <Badge tone={r.success ? 'green' : 'red'}>{r.success ? 'OK' : 'Fallo'}</Badge>
              </td>
              <td className="py-1.5 pr-3 whitespace-nowrap">{r.responseMs !== null ? `${r.responseMs} ms` : '—'}</td>
              <td className="py-1.5 text-slate-600 dark:text-slate-400">
                {r.error ??
                  (r.sslExpiresAt
                    ? `Certificado válido hasta ${formatDateTime(r.sslExpiresAt)}`
                    : r.statusCode
                      ? `HTTP ${r.statusCode}`
                      : '')}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Alta o edición. Los desplegables enlazan la comprobación con un proyecto, servidor o dominio. */
function CheckForm({
  check,
  onClose,
  onSaved,
}: {
  check: HealthCheck | null
  onClose: () => void
  onSaved: () => void
}) {
  const options = useLoad(() => Promise.all([listProjects(), listServers(), listDomains()]))
  const [form, setForm] = useState<HealthCheckInput>(() => ({
    name: check?.name ?? '',
    type: check?.type ?? 'HTTP',
    target: check?.target ?? '',
    port: check?.port ?? null,
    projectId: check?.project?.id ?? null,
    serverId: check?.server?.id ?? null,
    domainId: check?.domain?.id ?? null,
    intervalSeconds: check?.intervalSeconds ?? 300,
    failureThreshold: check?.failureThreshold ?? 3,
    enabled: check?.enabled ?? true,
  }))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const set = <K extends keyof HealthCheckInput>(key: K, value: HealthCheckInput[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  async function save() {
    setSaving(true)
    setError(null)
    try {
      const input = { ...form, port: form.type === 'HTTP' || form.type === 'PING' ? null : form.port }
      await (check ? updateHealthCheck(check.id, input) : createHealthCheck(input))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  const [projects, servers, domains] = options.data ?? [[], [], []]

  return (
    <Modal
      title={check ? 'Editar comprobación' : 'Nueva comprobación'}
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={saving || !form.name.trim() || !form.target.trim()}>
            {saving ? 'Guardando…' : 'Guardar'}
          </Button>
        </>
      }
    >
      <Field label="Nombre">
        <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Web de Stock" autoFocus />
      </Field>
      <Field label="Tipo">
        <Select value={form.type} onChange={(e) => set('type', e.target.value as CheckType)}>
          {(Object.keys(CHECK_TYPE_LABELS) as CheckType[]).map((t) => (
            <option key={t} value={t}>
              {CHECK_TYPE_LABELS[t]}
            </option>
          ))}
        </Select>
      </Field>
      <Field
        label={form.type === 'HTTP' ? 'URL' : 'Servidor o dominio'}
        hint={form.type === 'HTTP' ? 'URL completa, con https://' : 'Sin https:// ni rutas'}
      >
        <Input
          value={form.target}
          onChange={(e) => set('target', e.target.value)}
          placeholder={TARGET_PLACEHOLDER[form.type]}
        />
      </Field>
      {(form.type === 'TCP_PORT' || form.type === 'SSL') && (
        <Field label="Puerto" hint={form.type === 'SSL' ? 'Opcional; 443 si se deja vacío' : 'Por ejemplo 22 para SSH'}>
          <Input
            type="number"
            min={1}
            max={65535}
            value={form.port ?? ''}
            onChange={(e) => set('port', e.target.value ? Number(e.target.value) : null)}
          />
        </Field>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <Field label="Proyecto">
          <Select value={form.projectId ?? ''} onChange={(e) => set('projectId', e.target.value || null)}>
            <option value="">—</option>
            {projects.map((p: Project) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Servidor">
          <Select value={form.serverId ?? ''} onChange={(e) => set('serverId', e.target.value || null)}>
            <option value="">—</option>
            {servers.map((s: Server) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Dominio">
          <Select value={form.domainId ?? ''} onChange={(e) => set('domainId', e.target.value || null)}>
            <option value="">—</option>
            {domains.map((d: Domain) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <p className="text-xs text-slate-500">
        Los enlaces deciden quién recibe los avisos: tú y los miembros de los proyectos relacionados.
      </p>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Comprobar cada">
          <Select value={form.intervalSeconds} onChange={(e) => set('intervalSeconds', Number(e.target.value))}>
            {INTERVALS.map((m) => (
              <option key={m} value={m * 60}>
                {m} min
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Avisar tras" hint="Fallos seguidos antes de darla por caída">
          <Select value={form.failureThreshold} onChange={(e) => set('failureThreshold', Number(e.target.value))}>
            {[1, 2, 3, 4, 5].map((n) => (
              <option key={n} value={n}>
                {n} fallo{n === 1 ? '' : 's'}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <Checkbox label="Activada" checked={form.enabled} onChange={(e) => set('enabled', e.target.checked)} />
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}

function DeleteCheck({
  check,
  onClose,
  onDeleted,
}: {
  check: HealthCheck | null
  onClose: () => void
  onDeleted: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function confirm() {
    if (!check) return
    setBusy(true)
    setError(null)
    try {
      await deleteHealthCheck(check.id)
      onDeleted()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <ConfirmModal
      open={check !== null}
      title="Borrar comprobación"
      message={`Se borrará «${check?.name}» y todo su histórico.`}
      busy={busy}
      error={error}
      onConfirm={confirm}
      onClose={onClose}
    />
  )
}
