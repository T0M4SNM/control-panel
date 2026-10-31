import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { revokeIngestKey, type IngestKey } from '../api/errors'
import { createServerKey, listLogs, listLogSources, listServerKeys, type LogLevel, type LogLine } from '../api/logs'
import { listServers } from '../api/servers'
import { Badge, Button, Card, ConfirmModal, EmptyState, ErrorText, Field, Input, Loading, Modal, PageHeader, Select } from '../components/ui'
import { formatTimeAgo } from '../format'
import { errorMessage, useLoad } from '../hooks'

const LEVEL_TONE = { ERROR: 'red', WARN: 'amber', INFO: 'sky', DEBUG: 'slate' } as const

const LEVEL_FILTERS: { value: LogLevel | ''; label: string }[] = [
  { value: '', label: 'Todos' },
  { value: 'INFO', label: 'Info o más' },
  { value: 'WARN', label: 'Avisos y errores' },
  { value: 'ERROR', label: 'Solo errores' },
]

const timeFormat = new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })

/** Logs de los servidores que envía Vector: contenedores Docker y logs del sistema. Solo el admin. */
export default function LogsPage() {
  const [params, setParams] = useSearchParams()
  const servers = useLoad(listServers)
  const serverId = params.get('servidor') ?? servers.data?.[0]?.id ?? ''
  const source = params.get('origen') ?? ''
  const level = (params.get('nivel') ?? '') as LogLevel | ''
  const q = params.get('q') ?? ''
  const [search, setSearch] = useState(q)
  const [live, setLive] = useState(true)
  const [older, setOlder] = useState<LogLine[]>([])
  const [olderDone, setOlderDone] = useState(false)
  const [connecting, setConnecting] = useState(false)

  const sources = useLoad(() => (serverId ? listLogSources(serverId) : Promise.resolve([])), [serverId])
  const lines = useLoad(
    () => (serverId ? listLogs(serverId, { source, level: level || undefined, q }) : Promise.resolve([])),
    [serverId, source, level, q],
  )

  // En directo: las últimas líneas cada 5 segundos
  const { reload } = lines
  useEffect(() => {
    if (!live) return
    const timer = setInterval(reload, 5000)
    return () => clearInterval(timer)
  }, [live, reload])

  function update(key: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    if (key === 'servidor') next.delete('origen')
    setParams(next)
    setOlder([])
    setOlderDone(false)
  }

  function submitSearch(e: FormEvent) {
    e.preventDefault()
    update('q', search.trim())
  }

  async function loadOlder() {
    const all = [...(lines.data ?? []), ...older]
    const last = all[all.length - 1]
    if (!last) return
    const page = await listLogs(serverId, { source, level: level || undefined, q, before: last })
    setOlder((current) => [...current, ...page])
    if (page.length < 200) setOlderDone(true)
  }

  const shown = [...(lines.data ?? []), ...older]
  const noSources = sources.data?.length === 0

  return (
    <>
      <PageHeader title="Logs">
        {serverId && (
          <Button variant={noSources ? 'primary' : 'secondary'} onClick={() => setConnecting(true)}>
            Conectar servidor
          </Button>
        )}
      </PageHeader>
      <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
        Lo que escriben los contenedores y el sistema de cada servidor, enviado por Vector. Se guarda 7 días.
      </p>

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <Field label="Servidor">
          <Select value={serverId} onChange={(e) => update('servidor', e.target.value)} className="min-w-40">
            {servers.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Origen">
          <Select value={source} onChange={(e) => update('origen', e.target.value)} className="min-w-40">
            <option value="">Todos</option>
            {sources.data?.map((s) => (
              <option key={s.name} value={s.name}>
                {s.name} {s.errors > 0 ? `(${s.errors} errores)` : ''}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Nivel">
          <Select value={level} onChange={(e) => update('nivel', e.target.value)}>
            {LEVEL_FILTERS.map((f) => (
              <option key={f.value} value={f.value}>
                {f.label}
              </option>
            ))}
          </Select>
        </Field>
        <form onSubmit={submitSearch} className="flex items-end gap-2">
          <Field label="Buscar">
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Texto en el mensaje" />
          </Field>
          <Button type="submit">Buscar</Button>
        </form>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input type="checkbox" checked={live} onChange={(e) => setLive(e.target.checked)} />
          En directo
        </label>
      </div>

      {servers.data?.length === 0 && <EmptyState>Añade primero un servidor.</EmptyState>}
      {lines.error && <ErrorText>{lines.error}</ErrorText>}
      {lines.loading && !lines.data && <Loading />}
      {noSources && (
        <EmptyState>Este servidor aún no ha enviado logs. Pulsa «Conectar servidor» para ver cómo instalar Vector.</EmptyState>
      )}
      {!noSources && lines.data?.length === 0 && <EmptyState>No hay líneas con estos filtros.</EmptyState>}

      {shown.length > 0 && (
        <Card className="overflow-hidden">
          <ol className="divide-y divide-slate-100 font-mono text-xs dark:divide-slate-800">
            {shown.map((line) => (
              <li key={line.id} className="flex flex-wrap items-start gap-x-3 gap-y-1 px-4 py-1.5 sm:flex-nowrap">
                <time className="shrink-0 text-slate-500 tabular-nums" dateTime={line.occurredAt}>
                  {timeFormat.format(new Date(line.occurredAt))}
                </time>
                <span className="w-28 shrink-0 truncate text-slate-500" title={line.source}>
                  {line.source}
                </span>
                <span className="w-14 shrink-0">{line.level && <Badge tone={LEVEL_TONE[line.level]}>{line.level}</Badge>}</span>
                <span className="min-w-0 basis-full wrap-anywhere whitespace-pre-wrap sm:basis-auto">{line.message}</span>
              </li>
            ))}
          </ol>
          {!olderDone && (lines.data?.length ?? 0) >= 200 && (
            <div className="border-t border-slate-100 p-3 text-center dark:border-slate-800">
              <Button variant="ghost" onClick={loadOlder}>
                Cargar más antiguas
              </Button>
            </div>
          )}
        </Card>
      )}

      {connecting && <ConnectServer serverId={serverId} serverName={servers.data?.find((s) => s.id === serverId)?.name ?? ''} onClose={() => setConnecting(false)} />}
    </>
  )
}

/** Claves del servidor y los pasos para instalar Vector con la nueva. */
function ConnectServer({ serverId, serverName, onClose }: { serverId: string; serverName: string; onClose: () => void }) {
  const keys = useLoad(() => listServerKeys(serverId), [serverId])
  const [key, setKey] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [revoking, setRevoking] = useState<IngestKey | null>(null)
  const [error, setError] = useState<string | null>(null)
  const active = keys.data?.filter((k) => !k.revokedAt) ?? []

  async function create() {
    setError(null)
    try {
      setKey((await createServerKey(serverId, 'Vector')).key)
      keys.reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function confirmRevoke() {
    if (!revoking) return
    try {
      await revokeIngestKey(revoking.id)
      setRevoking(null)
      keys.reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function copy() {
    if (!key) return
    await navigator.clipboard.writeText(key)
    setCopied(true)
  }

  return (
    <Modal title={`Conectar ${serverName}`} open onClose={onClose} footer={<Button onClick={onClose}>Cerrar</Button>}>
      <div className="space-y-3 text-sm">
        {key ? (
          <>
            <p>
              Clave creada. Cópiala ahora: <strong>no se vuelve a mostrar</strong>.
            </p>
            <div className="flex items-center gap-2">
              <code className="min-w-0 flex-1 truncate rounded-lg bg-slate-100 px-3 py-2 dark:bg-slate-800">{key}</code>
              <Button onClick={copy}>{copied ? 'Copiada ✓' : 'Copiar'}</Button>
            </div>
          </>
        ) : (
          <>
            <p>Vector es un programa pequeño que corre en el servidor, lee sus logs y los envía al panel con una clave propia del servidor.</p>
            <Button variant="primary" onClick={create}>
              Crear clave
            </Button>
          </>
        )}
        <ol className="list-decimal space-y-2 pl-5">
          <li>
            Sube la carpeta <code>deploy/vector</code> del repositorio a <code>/opt/stacks/vector</code> del servidor.
          </li>
          <li>
            Crea <code>/opt/stacks/vector/.env</code> a partir de <code>.env.example</code>, con <code>CONTROL_PANEL_URL={window.location.origin}</code> y la
            clave en <code>CONTROL_PANEL_KEY</code>.
          </li>
          <li>
            Arranca: <code>cd /opt/stacks/vector && docker compose up -d</code>. En unos segundos verás aquí las líneas.
          </li>
        </ol>
        <p className="text-xs text-slate-500">Los pasos detallados están en «Logs del servidor» de deploy/README.md.</p>

        {active.length > 0 && (
          <div>
            <h3 className="mb-1 font-medium">Claves activas</h3>
            <ul className="space-y-1">
              {active.map((k) => (
                <li key={k.id} className="flex items-center justify-between gap-2">
                  <span className="min-w-0 truncate text-slate-500">
                    <code>{k.keyPrefix}…</code> · {k.lastUsedAt ? `usada ${formatTimeAgo(k.lastUsedAt)}` : 'sin usar'}
                  </span>
                  <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => setRevoking(k)}>
                    Revocar
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {error && <ErrorText>{error}</ErrorText>}
      </div>
      <ConfirmModal
        open={revoking !== null}
        title="Revocar clave"
        message="El Vector que use esta clave dejará de poder enviar logs al momento. No se puede deshacer."
        confirmLabel="Revocar"
        onConfirm={confirmRevoke}
        onClose={() => setRevoking(null)}
      />
    </Modal>
  )
}
