import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { listProjectDomains } from '../api/domains'
import { listProjectHealthChecks, type CheckStatus } from '../api/healthChecks'
import {
  deleteProject,
  getProject,
  listMembers,
  PROJECT_ROLE_LABELS,
  PROJECT_STATUS_LABELS,
  putMember,
  removeMember,
  updateProject,
  type Project,
  type ProjectRole,
  type ProjectStatus,
} from '../api/projects'
import { linkProjectServer, listProjectServers, listServers, unlinkProjectServer } from '../api/servers'
import { listUsers } from '../api/users'
import { useAuth } from '../auth-context'
import { Badge, Button, Card, ConfirmModal, ErrorText, Field, Input, Loading, Modal, Select, Textarea } from '../components/ui'
import { formatEuros } from '../format'
import { errorMessage, useLoad } from '../hooks'

const CHECK_TONE: Record<CheckStatus, 'green' | 'red' | 'slate'> = { UP: 'green', DOWN: 'red', UNKNOWN: 'slate' }
const CHECK_LABEL: Record<CheckStatus, string> = { UP: 'Responde', DOWN: 'Caída', UNKNOWN: 'Sin datos' }

/** Todo lo de un proyecto: dónde corre, sus dominios, su vigilancia y quién participa. */
export default function ProjectDetailPage() {
  const { id = '' } = useParams()
  const project = useLoad(() => getProject(id), [id])
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const navigate = useNavigate()

  if (project.error) return <ErrorText>{project.error}</ErrorText>
  if (!project.data) return <Loading />
  const p = project.data

  async function confirmDelete() {
    try {
      await deleteProject(p.id)
      navigate('/proyectos')
    } catch (err) {
      setDeleteError(errorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <Link to="/proyectos" className="text-sm text-slate-500 hover:underline">
          ← Proyectos
        </Link>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold">{p.name}</h1>
              <Badge tone={p.status === 'PRODUCTION' ? 'green' : p.status === 'DEVELOPMENT' ? 'sky' : p.status === 'PAUSED' ? 'amber' : 'slate'}>
                {PROJECT_STATUS_LABELS[p.status]}
              </Badge>
              {p.myRole && <Badge>{PROJECT_ROLE_LABELS[p.myRole]}</Badge>}
            </div>
            {p.description && <p className="mt-1 text-slate-600 dark:text-slate-400">{p.description}</p>}
            <div className="mt-2 flex flex-wrap gap-4 text-sm">
              {p.publicUrl && <ExternalLink href={p.publicUrl}>{p.publicUrl}</ExternalLink>}
              {p.repoUrl && <ExternalLink href={p.repoUrl}>Repositorio</ExternalLink>}
            </div>
          </div>
          <div className="flex gap-2">
            {p.permissions.edit && <Button onClick={() => setEditing(true)}>Editar</Button>}
            {p.permissions.delete && (
              <Button variant="danger" onClick={() => setDeleting(true)}>
                Borrar
              </Button>
            )}
          </div>
        </div>
        {p.notes && <p className="mt-3 rounded-lg bg-slate-100 p-3 text-sm whitespace-pre-line dark:bg-slate-800/60">{p.notes}</p>}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ServersSection projectId={p.id} />
        <DomainsSection projectId={p.id} />
        <ChecksSection projectId={p.id} />
        <MembersSection project={p} />
      </div>

      {editing && (
        <ProjectForm
          project={p}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false)
            project.reload()
          }}
        />
      )}
      <ConfirmModal
        open={deleting}
        title="Borrar proyecto"
        message={`Se borrará «${p.name}» con sus miembros, su reparto de servidores y sus entradas de tiempo. Sus dominios y comprobaciones se quedan sin proyecto.`}
        error={deleteError}
        onConfirm={confirmDelete}
        onClose={() => {
          setDeleting(false)
          setDeleteError(null)
        }}
      />
    </div>
  )
}

function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <Card className="p-5">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="font-semibold">{title}</h2>
        {action}
      </div>
      {children}
    </Card>
  )
}

function ExternalLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <a href={href} target="_blank" rel="noreferrer" className="truncate text-sky-700 hover:underline dark:text-sky-400">
      {children} ↗
    </a>
  )
}

/** Servidores del proyecto; el admin cambia el % del coste que carga o lo desvincula. */
function ServersSection({ projectId }: { projectId: string }) {
  const { isAdmin } = useAuth()
  const servers = useLoad(() => listProjectServers(projectId), [projectId])
  const [linking, setLinking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function unlink(serverId: string) {
    setError(null)
    try {
      await unlinkProjectServer(projectId, serverId)
      servers.reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Section title="Servidores" action={isAdmin && <Button variant="ghost" onClick={() => setLinking(true)}>Vincular</Button>}>
      {servers.data?.length === 0 && <p className="text-sm text-slate-500">No está en ningún servidor.</p>}
      <ul className="space-y-2">
        {servers.data?.map((s) => {
          const link = s.projects.find((l) => l.projectId === projectId)
          return (
            <li key={s.id} className="flex items-center justify-between gap-3 text-sm">
              <div className="min-w-0">
                <p className="font-medium">{s.name}</p>
                <p className="truncate text-slate-500">{[s.provider, s.ipAddress].filter(Boolean).join(' · ')}</p>
              </div>
              {link?.costSharePercent !== undefined && (
                <span className="shrink-0 text-right text-slate-600 dark:text-slate-400">
                  {link.costSharePercent} % · {formatEuros(link.monthlyCost ?? 0)}/mes
                </span>
              )}
              {isAdmin && (
                <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => unlink(s.id)}>
                  Quitar
                </Button>
              )}
            </li>
          )
        })}
      </ul>
      {error && <ErrorText>{error}</ErrorText>}
      {linking && (
        <LinkServerForm
          projectId={projectId}
          onClose={() => setLinking(false)}
          onSaved={() => {
            setLinking(false)
            servers.reload()
          }}
        />
      )}
    </Section>
  )
}

function LinkServerForm({ projectId, onClose, onSaved }: { projectId: string; onClose: () => void; onSaved: () => void }) {
  const servers = useLoad(listServers)
  const [serverId, setServerId] = useState('')
  const [share, setShare] = useState('100')
  const [error, setError] = useState<string | null>(null)
  const selected = servers.data?.find((s) => s.id === serverId)
  const current = selected?.projects.find((p) => p.projectId === projectId)?.costSharePercent
  const free = selected?.costs ? 100 - selected.costs.assignedSharePercent + (current ?? 0) : 100

  async function save() {
    setError(null)
    try {
      await linkProjectServer(projectId, serverId, Number(share))
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal
      title="Vincular servidor"
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={!serverId}>
            Guardar
          </Button>
        </>
      }
    >
      <Field label="Servidor">
        <Select
          value={serverId}
          onChange={(e) => {
            setServerId(e.target.value)
            const s = servers.data?.find((x) => x.id === e.target.value)
            const mine = s?.projects.find((p) => p.projectId === projectId)?.costSharePercent
            setShare(String(mine ?? (s?.costs ? 100 - s.costs.assignedSharePercent : 100)))
          }}
        >
          <option value="">— Elige —</option>
          {servers.data?.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Parte del coste que carga este proyecto (%)" hint={serverId ? `Disponible: ${free} %` : undefined}>
        <Input type="number" min={0} max={100} value={share} onChange={(e) => setShare(e.target.value)} />
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}

function DomainsSection({ projectId }: { projectId: string }) {
  const domains = useLoad(() => listProjectDomains(projectId), [projectId])
  return (
    <Section title="Dominios" action={<Link to="/dominios" className="text-sm text-sky-700 hover:underline dark:text-sky-400">Ver todos</Link>}>
      {domains.data?.length === 0 && <p className="text-sm text-slate-500">Ningún dominio sirve a este proyecto.</p>}
      <ul className="space-y-2 text-sm">
        {domains.data?.map((d) => (
          <li key={d.id} className="flex items-center justify-between gap-3">
            <span className="font-medium">{d.name}</span>
            {d.server && <span className="text-slate-500">en {d.server.name}</span>}
          </li>
        ))}
      </ul>
    </Section>
  )
}

function ChecksSection({ projectId }: { projectId: string }) {
  const checks = useLoad(() => listProjectHealthChecks(projectId), [projectId])
  return (
    <Section
      title="Comprobaciones"
      action={<Link to="/comprobaciones" className="text-sm text-sky-700 hover:underline dark:text-sky-400">Gestionar</Link>}
    >
      {checks.data?.length === 0 && <p className="text-sm text-slate-500">Nada vigila este proyecto todavía.</p>}
      <ul className="space-y-2 text-sm">
        {checks.data?.map((c) => (
          <li key={c.id} className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="font-medium">{c.name}</p>
              <p className="truncate text-slate-500">{c.target}</p>
            </div>
            <Badge tone={c.enabled ? CHECK_TONE[c.currentStatus] : 'slate'}>{c.enabled ? CHECK_LABEL[c.currentStatus] : 'Pausada'}</Badge>
          </li>
        ))}
      </ul>
    </Section>
  )
}

/** Miembros y su rol. Gestiona el admin o el propietario del proyecto. */
function MembersSection({ project }: { project: Project }) {
  const { isAdmin, user } = useAuth()
  const members = useLoad(() => listMembers(project.id), [project.id])
  const [adding, setAdding] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const canManage = project.permissions.manageMembers

  async function changeRole(userId: string, role: ProjectRole) {
    setError(null)
    try {
      await putMember(project.id, userId, role)
      members.reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function remove(userId: string) {
    setError(null)
    try {
      await removeMember(project.id, userId)
      members.reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Section
      title="Miembros"
      action={canManage && isAdmin && <Button variant="ghost" onClick={() => setAdding(true)}>Añadir</Button>}
    >
      <ul className="space-y-2 text-sm">
        {members.data?.map((m) => (
          <li key={m.userId} className="flex flex-wrap items-center justify-between gap-2">
            <div className="min-w-0">
              <p className="font-medium">
                {m.displayName}
                {m.userId === user.id && ' (tú)'}
              </p>
              <p className="truncate text-slate-500">{m.email}</p>
            </div>
            {canManage ? (
              <div className="flex items-center gap-1">
                <Select value={m.role} onChange={(e) => changeRole(m.userId, e.target.value as ProjectRole)} className="w-auto py-1 text-sm">
                  {(Object.keys(PROJECT_ROLE_LABELS) as ProjectRole[]).map((r) => (
                    <option key={r} value={r}>
                      {PROJECT_ROLE_LABELS[r]}
                    </option>
                  ))}
                </Select>
                <Button variant="ghost" className="text-red-700 dark:text-red-400" onClick={() => remove(m.userId)}>
                  Quitar
                </Button>
              </div>
            ) : (
              <Badge>{PROJECT_ROLE_LABELS[m.role]}</Badge>
            )}
          </li>
        ))}
      </ul>
      {canManage && !isAdmin && <p className="mt-2 text-xs text-slate-500">Para añadir a alguien nuevo, pídeselo al administrador.</p>}
      {error && <ErrorText>{error}</ErrorText>}
      {adding && (
        <AddMemberForm
          projectId={project.id}
          existing={members.data?.map((m) => m.userId) ?? []}
          onClose={() => setAdding(false)}
          onSaved={() => {
            setAdding(false)
            members.reload()
          }}
        />
      )}
    </Section>
  )
}

function AddMemberForm({ projectId, existing, onClose, onSaved }: { projectId: string; existing: string[]; onClose: () => void; onSaved: () => void }) {
  const users = useLoad(listUsers)
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<ProjectRole>('EDITOR')
  const [error, setError] = useState<string | null>(null)
  const candidates = users.data?.filter((u) => u.active && !existing.includes(u.id)) ?? []

  async function save() {
    setError(null)
    try {
      await putMember(projectId, userId, role)
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal
      title="Añadir miembro"
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={!userId}>
            Añadir
          </Button>
        </>
      }
    >
      {users.data && candidates.length === 0 && <p className="text-sm text-slate-500">Todos los usuarios ya son miembros.</p>}
      <Field label="Usuario">
        <Select value={userId} onChange={(e) => setUserId(e.target.value)}>
          <option value="">— Elige —</option>
          {candidates.map((u) => (
            <option key={u.id} value={u.id}>
              {u.displayName} ({u.email})
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Rol" hint="Propietario: edita y gestiona miembros · Editor: edita y registra tiempo · Lector: solo consulta">
        <Select value={role} onChange={(e) => setRole(e.target.value as ProjectRole)}>
          {(Object.keys(PROJECT_ROLE_LABELS) as ProjectRole[]).map((r) => (
            <option key={r} value={r}>
              {PROJECT_ROLE_LABELS[r]}
            </option>
          ))}
        </Select>
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}

function ProjectForm({ project, onClose, onSaved }: { project: Project; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({
    name: project.name,
    description: project.description ?? '',
    status: project.status,
    publicUrl: project.publicUrl ?? '',
    repoUrl: project.repoUrl ?? '',
    notes: project.notes ?? '',
  })
  const [error, setError] = useState<string | null>(null)
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  async function save() {
    setError(null)
    try {
      await updateProject(project.id, {
        name: form.name.trim(),
        description: form.description.trim() || null,
        status: form.status as ProjectStatus,
        publicUrl: form.publicUrl.trim() || null,
        repoUrl: form.repoUrl.trim() || null,
        notes: form.notes.trim() || null,
      })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <Modal
      title="Editar proyecto"
      open
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Cancelar</Button>
          <Button variant="primary" onClick={save} disabled={!form.name.trim()}>
            Guardar
          </Button>
        </>
      }
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nombre">
          <Input value={form.name} onChange={(e) => set('name', e.target.value)} />
        </Field>
        <Field label="Estado">
          <Select value={form.status} onChange={(e) => set('status', e.target.value)}>
            {(Object.keys(PROJECT_STATUS_LABELS) as ProjectStatus[]).map((s) => (
              <option key={s} value={s}>
                {PROJECT_STATUS_LABELS[s]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="URL de la web">
          <Input value={form.publicUrl} onChange={(e) => set('publicUrl', e.target.value)} />
        </Field>
        <Field label="Repositorio">
          <Input value={form.repoUrl} onChange={(e) => set('repoUrl', e.target.value)} />
        </Field>
      </div>
      <Field label="Descripción">
        <Textarea value={form.description} onChange={(e) => set('description', e.target.value)} />
      </Field>
      <Field label="Notas" hint="Nunca contraseñas">
        <Textarea value={form.notes} onChange={(e) => set('notes', e.target.value)} />
      </Field>
      {error && <ErrorText>{error}</ErrorText>}
    </Modal>
  )
}
