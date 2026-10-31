import { useState } from 'react'
import { listContracts } from '../api/contracts'
import { deleteServer, listServers, updateServer, type Server, type ServerInput } from '../api/servers'
import { useAuth } from '../auth-context'
import { Badge, Button, ButtonLink, Card, ConfirmModal, EmptyState, ErrorText, Field, Input, Loading, Modal, PageHeader, Select, Textarea } from '../components/ui'
import { formatEuros } from '../format'
import { errorMessage, useLoad } from '../hooks'

/** Servidores. El admin ve su contrato, su coste y cómo se reparte; un miembro, solo los de sus proyectos. */
export default function ServersPage() {
  const { isAdmin } = useAuth()
  const servers = useLoad(listServers)
  const [editing, setEditing] = useState<Server | null>(null)
  const [deleting, setDeleting] = useState<Server | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteServer(deleting.id)
      setDeleting(null)
      servers.reload()
    } catch (err) {
      setDeleteError(errorMessage(err))
    }
  }

  return (
    <>
      <PageHeader title="Servidores">
        {isAdmin && (
          <ButtonLink to="/anadir?tipo=servidor" variant="primary">
            Añadir servidor
          </ButtonLink>
        )}
      </PageHeader>
      {servers.error && <ErrorText>{servers.error}</ErrorText>}
      {servers.loading && !servers.data && <Loading />}
      {servers.data?.length === 0 && <EmptyState>Aún no hay servidores.</EmptyState>}

      <ul className="grid gap-4 lg:grid-cols-2">
        {servers.data?.map((server) => (
          <li key={server.id}>
            <Card className="h-full p-5">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="font-semibold">{server.name}</h2>
                  <p className="text-sm text-slate-600 dark:text-slate-400">
                    {[server.provider, server.ipAddress, server.os].filter(Boolean).join(' · ') || 'Sin datos'}
                  </p>
                  <p className="text-xs text-slate-500">{specs(server)}</p>
                </div>
                {server.costs && (
                  <div className="shrink-0 text-right">
                    <p className="font-semibold">{formatEuros(server.costs.monthlyAmount)}</p>
                    <p className="text-xs text-slate-500">al mes</p>
                  </div>
                )}
              </div>

              <div className="mt-3 space-y-1 text-sm">
                {server.projects.length === 0 && <p className="text-slate-500">Sin proyectos.</p>}
                {server.projects.map((p) => (
                  <div key={p.projectId} className="flex items-center justify-between gap-3">
                    <span className="truncate">{p.projectName}</span>
                    {p.costSharePercent !== undefined && (
                      <span className="shrink-0 text-slate-500">
                        {p.costSharePercent} % · {formatEuros(p.monthlyCost ?? 0)}
                      </span>
                    )}
                  </div>
                ))}
                {server.costs && server.costs.assignedSharePercent < 100 && server.costs.monthlyAmount > 0 && (
                  <p className="text-xs text-amber-700 dark:text-amber-400">
                    {100 - server.costs.assignedSharePercent} % del coste sin asignar a ningún proyecto
                  </p>
                )}
                {server.costs && !server.costs.contractId && <Badge tone="amber">Sin contrato</Badge>}
              </div>

              {isAdmin && (
                <div className="mt-4 flex gap-1 border-t border-slate-200 pt-3 dark:border-slate-800">
                  <Button variant="ghost" onClick={() => setEditing(server)}>
                    Editar
                  </Button>
                  <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => setDeleting(server)}>
                    Borrar
                  </Button>
                </div>
              )}
            </Card>
          </li>
        ))}
      </ul>

      {editing && (
        <ServerForm
          server={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            servers.reload()
          }}
        />
      )}
      <ConfirmModal
        open={deleting !== null}
        title="Borrar servidor"
        message={`Se borrará «${deleting?.name}» y su reparto entre proyectos. Su contrato se mantiene.`}
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

function specs(s: Server): string {
  const parts = [
    s.cpuCores && `${s.cpuCores} vCPU`,
    s.ramMb && `${Math.round((s.ramMb / 1024) * 10) / 10} GB RAM`,
    s.diskGb && `${s.diskGb} GB disco`,
  ].filter(Boolean)
  return parts.join(' · ')
}

function ServerForm({ server, onClose, onSaved }: { server: Server; onClose: () => void; onSaved: () => void }) {
  const contracts = useLoad(listContracts)
  const [form, setForm] = useState({
    name: server.name,
    provider: server.provider ?? '',
    ip: server.ipAddress ?? '',
    os: server.os ?? '',
    cpu: server.cpuCores?.toString() ?? '',
    ramGb: server.ramMb ? String(server.ramMb / 1024) : '',
    diskGb: server.diskGb?.toString() ?? '',
    notes: server.notes ?? '',
    contractId: server.costs?.contractId ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function save() {
    setSaving(true)
    setError(null)
    const input: ServerInput = {
      name: form.name.trim(),
      provider: form.provider.trim() || null,
      ipAddress: form.ip.trim() || null,
      os: form.os.trim() || null,
      cpuCores: form.cpu ? Number(form.cpu) : null,
      ramMb: form.ramGb ? Math.round(Number(form.ramGb) * 1024) : null,
      diskGb: form.diskGb ? Number(form.diskGb) : null,
      notes: form.notes.trim() || null,
      contractId: form.contractId || null,
    }
    try {
      await updateServer(server.id, input)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Editar servidor"
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={saving || !form.name.trim()}>
            Guardar
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nombre">
          <Input value={form.name} onChange={(e) => set('name', e.target.value)} />
        </Field>
        <Field label="Proveedor">
          <Input value={form.provider} onChange={(e) => set('provider', e.target.value)} />
        </Field>
        <Field label="IP">
          <Input value={form.ip} onChange={(e) => set('ip', e.target.value)} />
        </Field>
        <Field label="Sistema operativo">
          <Input value={form.os} onChange={(e) => set('os', e.target.value)} />
        </Field>
      </div>
      <div className="grid grid-cols-3 gap-4">
        <Field label="vCPU">
          <Input type="number" value={form.cpu} onChange={(e) => set('cpu', e.target.value)} />
        </Field>
        <Field label="RAM (GB)">
          <Input type="number" value={form.ramGb} onChange={(e) => set('ramGb', e.target.value)} />
        </Field>
        <Field label="Disco (GB)">
          <Input type="number" value={form.diskGb} onChange={(e) => set('diskGb', e.target.value)} />
        </Field>
      </div>
      <Field label="Contrato" hint="De él sale el coste del servidor">
        <Select value={form.contractId} onChange={(e) => set('contractId', e.target.value)}>
          <option value="">— Sin contrato —</option>
          {contracts.data
            ?.filter((c) => c.type !== 'DOMAIN')
            .map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({formatEuros(c.monthlyAmount)}/mes)
              </option>
            ))}
        </Select>
      </Field>
      <Field label="Notas" hint="Nunca contraseñas">
        <Textarea value={form.notes} onChange={(e) => set('notes', e.target.value)} />
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
