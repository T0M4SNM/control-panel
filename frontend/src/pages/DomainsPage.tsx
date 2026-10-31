import { useState } from 'react'
import { listContracts } from '../api/contracts'
import { deleteDomain, listDomains, updateDomain, type Domain } from '../api/domains'
import { listProjects } from '../api/projects'
import { listServers } from '../api/servers'
import { useAuth } from '../auth-context'
import { Badge, Button, ButtonLink, Card, ConfirmModal, EmptyState, ErrorText, Field, Input, Loading, Modal, PageHeader, Select, Textarea } from '../components/ui'
import { daysUntil, formatDate, formatDaysLeft } from '../format'
import { errorMessage, useLoad } from '../hooks'

/** Dominios con sus subdominios debajo. El admin ve contrato y renovación. */
export default function DomainsPage() {
  const { isAdmin } = useAuth()
  const domains = useLoad(listDomains)
  const [editing, setEditing] = useState<Domain | null>(null)
  const [deleting, setDeleting] = useState<Domain | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const all = domains.data ?? []
  // Raíces y huérfanos (subdominios cuyo dominio raíz el miembro no ve) en primer nivel
  const topLevel = all.filter((d) => d.parent === null || !all.some((p) => p.id === d.parent?.id))
  const childrenOf = (id: string) => all.filter((d) => d.parent?.id === id)

  async function confirmDelete() {
    if (!deleting) return
    try {
      await deleteDomain(deleting.id)
      setDeleting(null)
      domains.reload()
    } catch (err) {
      setDeleteError(errorMessage(err))
    }
  }

  const row = (domain: Domain, nested: boolean) => (
    <div key={domain.id} className={`flex flex-wrap items-center gap-3 py-3 ${nested ? 'pl-6' : ''}`}>
      <div className="min-w-0 flex-1">
        <p className={nested ? '' : 'font-semibold'}>
          {nested && <span className="text-slate-400">└ </span>}
          {domain.name}
        </p>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          {[
            domain.registrar,
            domain.server && `en ${domain.server.name}`,
            domain.project && `para ${domain.project.name}`,
          ]
            .filter(Boolean)
            .join(' · ') || (nested ? 'Sin enlazar' : '')}
        </p>
      </div>
      {domain.contract && !domain.contract.inherited && (
        <div className="text-right text-sm">
          <p>Renueva el {formatDate(domain.contract.renewalDate)}</p>
          <RenewalBadge date={domain.contract.renewalDate} />
        </div>
      )}
      {isAdmin && !domain.parent && !domain.contract && <Badge tone="amber">Sin contrato</Badge>}
      {isAdmin && (
        <div className="flex gap-1">
          <Button variant="ghost" onClick={() => setEditing(domain)}>
            Editar
          </Button>
          <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => setDeleting(domain)}>
            Borrar
          </Button>
        </div>
      )}
    </div>
  )

  return (
    <>
      <PageHeader title="Dominios">
        {isAdmin && (
          <ButtonLink to="/anadir?tipo=dominio" variant="primary">
            Añadir dominio
          </ButtonLink>
        )}
      </PageHeader>
      {domains.error && <ErrorText>{domains.error}</ErrorText>}
      {domains.loading && !domains.data && <Loading />}
      {domains.data?.length === 0 && <EmptyState>Aún no hay dominios.</EmptyState>}

      <div className="space-y-4">
        {topLevel.map((root) => (
          <Card key={root.id} className="divide-y divide-slate-100 px-5 dark:divide-slate-800">
            {row(root, false)}
            {childrenOf(root.id).map((child) => row(child, true))}
          </Card>
        ))}
      </div>

      {editing && (
        <DomainForm
          domain={editing}
          hasChildren={childrenOf(editing.id).length > 0}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            domains.reload()
          }}
        />
      )}
      <ConfirmModal
        open={deleting !== null}
        title="Borrar dominio"
        message={`Se borrará «${deleting?.name}»${
          deleting && childrenOf(deleting.id).length > 0 ? ' y todos sus subdominios' : ''
        }. Su contrato se mantiene.`}
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

function RenewalBadge({ date }: { date: string }) {
  const days = daysUntil(date)
  const tone = days <= 7 ? 'red' : days <= 30 ? 'amber' : 'slate'
  return <Badge tone={tone}>{formatDaysLeft(days)}</Badge>
}

function DomainForm({
  domain,
  hasChildren,
  onClose,
  onSaved,
}: {
  domain: Domain
  hasChildren: boolean
  onClose: () => void
  onSaved: () => void
}) {
  const options = useLoad(() => Promise.all([listServers(), listProjects(), listContracts()]))
  const [form, setForm] = useState({
    name: domain.name,
    registrar: domain.registrar ?? '',
    serverId: domain.server?.id ?? '',
    projectId: domain.project?.id ?? '',
    contractId: domain.contract && !domain.contract.inherited ? domain.contract.id : '',
    notes: domain.notes ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))
  const [servers, projects, contracts] = options.data ?? [[], [], []]

  async function save() {
    setSaving(true)
    setError(null)
    try {
      await updateDomain(domain.id, {
        name: form.name.trim(),
        parentId: domain.parent?.id ?? null,
        contractId: domain.parent ? null : form.contractId || null,
        serverId: form.serverId || null,
        projectId: form.projectId || null,
        registrar: form.registrar.trim() || null,
        notes: form.notes.trim() || null,
      })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Editar dominio"
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
      <Field label="Nombre" hint={hasChildren ? 'No se puede renombrar mientras tenga subdominios' : undefined}>
        <Input value={form.name} disabled={hasChildren} onChange={(e) => set('name', e.target.value)} />
      </Field>
      {!domain.parent && (
        <>
          <Field label="Registrador">
            <Input value={form.registrar} onChange={(e) => set('registrar', e.target.value)} />
          </Field>
          <Field label="Contrato" hint="Precio y fecha de renovación del dominio">
            <Select value={form.contractId} onChange={(e) => set('contractId', e.target.value)}>
              <option value="">— Sin contrato —</option>
              {contracts
                .filter((c) => c.type === 'DOMAIN')
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
            </Select>
          </Field>
        </>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Apunta al servidor">
          <Select value={form.serverId} onChange={(e) => set('serverId', e.target.value)}>
            <option value="">—</option>
            {servers.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Sirve al proyecto">
          <Select value={form.projectId} onChange={(e) => set('projectId', e.target.value)}>
            <option value="">—</option>
            {projects.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
        </Field>
      </div>
      <Field label="Notas">
        <Textarea value={form.notes} onChange={(e) => set('notes', e.target.value)} />
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
