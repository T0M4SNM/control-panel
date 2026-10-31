import { useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { createContract, type BillingPeriod } from '../api/contracts'
import { createDomain, listDomains, updateDomain, type Domain } from '../api/domains'
import { createHealthCheck } from '../api/healthChecks'
import { createProject, type ProjectStatus, PROJECT_STATUS_LABELS } from '../api/projects'
import { createServer, linkProjectServer, listServers } from '../api/servers'
import { Button, Card, Checkbox, ErrorText, Field, Input, PageHeader, Select, Textarea } from '../components/ui'
import { errorMessage, useLoad } from '../hooks'

type Kind = 'servidor' | 'app' | 'dominio'

const KINDS: { kind: Kind; title: string; description: string }[] = [
  { kind: 'servidor', title: 'Servidor', description: 'Un VPS: su coste, su renovación y vigilancia por ping y SSH.' },
  { kind: 'app', title: 'App web', description: 'Un proyecto con su web: en qué servidor está, su subdominio y su vigilancia.' },
  { kind: 'dominio', title: 'Dominio', description: 'Un dominio comprado: su registrador, su precio y cuándo renovarlo.' },
]

/**
 * «Añadir»: un asistente por tipo que crea en orden todas las piezas (contrato, servidor, proyecto,
 * subdominio, comprobaciones...) y las enlaza entre sí. Solo admin.
 */
export default function AddPage() {
  const [params, setParams] = useSearchParams()
  const kind = params.get('tipo') as Kind | null

  return (
    <>
      <PageHeader title="Añadir" />
      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        {KINDS.map((k) => (
          <button
            key={k.kind}
            onClick={() => setParams({ tipo: k.kind })}
            aria-pressed={kind === k.kind}
            className={`rounded-xl border p-4 text-left transition ${
              kind === k.kind
                ? 'border-sky-500 bg-sky-50 ring-2 ring-sky-500/30 dark:bg-sky-950/40'
                : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700'
            }`}
          >
            <span className="block font-semibold">{k.title}</span>
            <span className="mt-1 block text-sm text-slate-600 dark:text-slate-400">{k.description}</span>
          </button>
        ))}
      </div>

      {kind === 'servidor' && <AddServer key="servidor" />}
      {kind === 'app' && <AddApp key="app" />}
      {kind === 'dominio' && <AddDomain key="dominio" />}
    </>
  )
}

/** Ejecuta los pasos en orden y deja constancia de cada uno para mostrarlo. */
function useSteps() {
  const [steps, setSteps] = useState<{ label: string; ok: boolean }[]>([])
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const [done, setDone] = useState(false)

  async function run(plan: (step: <T>(label: string, action: () => Promise<T>) => Promise<T>) => Promise<void>) {
    setSteps([])
    setError(null)
    setRunning(true)
    const step = async <T,>(label: string, action: () => Promise<T>): Promise<T> => {
      try {
        const result = await action()
        setSteps((s) => [...s, { label, ok: true }])
        return result
      } catch (err) {
        setSteps((s) => [...s, { label, ok: false }])
        throw err
      }
    }
    try {
      await plan(step)
      setDone(true)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setRunning(false)
    }
  }

  return { steps, error, running, done, run }
}

function Progress({ steps, error, done, children }: { steps: { label: string; ok: boolean }[]; error: string | null; done: boolean; children?: ReactNode }) {
  if (steps.length === 0 && !error) return null
  return (
    <div className="space-y-2 rounded-lg bg-slate-50 p-4 text-sm dark:bg-slate-800/50">
      <ul className="space-y-1">
        {steps.map((s, i) => (
          <li key={i} className={s.ok ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'}>
            {s.ok ? '✓' : '✗'} {s.label}
          </li>
        ))}
      </ul>
      {error && (
        <ErrorText>
          {error.replace(/[.\s]*$/, '.')} Los pasos marcados con ✓ ya se han guardado; corrige el dato y complétalo
          desde su pantalla.
        </ErrorText>
      )}
      {done && children}
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset className="space-y-4 border-t border-slate-200 pt-4 dark:border-slate-800">
      <legend className="pr-2 text-sm font-semibold">{title}</legend>
      {children}
    </fieldset>
  )
}

const today = () => new Date().toISOString().slice(0, 10)

/** Precio y renovación, compartido por servidor y dominio. */
interface CostForm {
  amount: string
  period: BillingPeriod
  renewalDate: string
  autoRenew: boolean
}

function CostFields({ value, onChange, periods = ['MONTHLY', 'YEARLY'] }: { value: CostForm; onChange: (v: CostForm) => void; periods?: BillingPeriod[] }) {
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <Field label="Precio (€, IVA incluido)">
        <Input type="number" min={0} step="0.01" value={value.amount} onChange={(e) => onChange({ ...value, amount: e.target.value })} />
      </Field>
      <Field label="Se paga">
        <Select value={value.period} onChange={(e) => onChange({ ...value, period: e.target.value as BillingPeriod })}>
          {periods.map((p) => (
            <option key={p} value={p}>
              {p === 'MONTHLY' ? 'Cada mes' : 'Cada año'}
            </option>
          ))}
        </Select>
      </Field>
      <Field label="Próxima renovación">
        <Input type="date" value={value.renewalDate} onChange={(e) => onChange({ ...value, renewalDate: e.target.value })} />
      </Field>
      <div className="sm:col-span-3">
        <Checkbox label="Se renueva solo" checked={value.autoRenew} onChange={(e) => onChange({ ...value, autoRenew: e.target.checked })} />
      </div>
    </div>
  )
}

const blank = (v: string) => (v.trim() === '' ? null : v.trim())

function AddServer() {
  const [form, setForm] = useState({ name: '', provider: '', ip: '', os: 'Ubuntu 24.04', cpu: '', ramGb: '', diskGb: '' })
  const [hasCost, setHasCost] = useState(true)
  const [cost, setCost] = useState<CostForm>({ amount: '', period: 'MONTHLY', renewalDate: today(), autoRenew: true })
  const [watchPing, setWatchPing] = useState(true)
  const [watchSsh, setWatchSsh] = useState(true)
  const { steps, error, running, done, run } = useSteps()
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  const submit = () =>
    run(async (step) => {
      const contract = hasCost
        ? await step('Contrato con su coste y renovación', () =>
            createContract({
              name: `Servidor ${form.name.trim()}`,
              provider: form.provider.trim() || form.name.trim(),
              type: 'SERVER',
              amount: Number(cost.amount),
              billingPeriod: cost.period,
              renewalDate: cost.renewalDate,
              autoRenew: cost.autoRenew,
              status: 'ACTIVE',
              notes: null,
            }),
          )
        : null
      const server = await step('Servidor', () =>
        createServer({
          name: form.name.trim(),
          provider: blank(form.provider),
          ipAddress: blank(form.ip),
          os: blank(form.os),
          cpuCores: form.cpu ? Number(form.cpu) : null,
          ramMb: form.ramGb ? Math.round(Number(form.ramGb) * 1024) : null,
          diskGb: form.diskGb ? Number(form.diskGb) : null,
          notes: null,
          contractId: contract?.id ?? null,
        }),
      )
      const ip = form.ip.trim()
      if (ip && watchPing) {
        await step('Vigilancia por ping', () =>
          createHealthCheck({ name: `Ping ${server.name}`, type: 'PING', target: ip, port: null, serverId: server.id, domainId: null, projectId: null, intervalSeconds: 300, failureThreshold: 3, enabled: true }),
        )
      }
      if (ip && watchSsh) {
        await step('Vigilancia del puerto SSH (22)', () =>
          createHealthCheck({ name: `SSH ${server.name}`, type: 'TCP_PORT', target: ip, port: 22, serverId: server.id, domainId: null, projectId: null, intervalSeconds: 300, failureThreshold: 3, enabled: true }),
        )
      }
    })

  return (
    <Card className="space-y-4 p-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nombre">
          <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Contabo" autoFocus />
        </Field>
        <Field label="Proveedor">
          <Input value={form.provider} onChange={(e) => set('provider', e.target.value)} placeholder="Contabo" />
        </Field>
        <Field label="IP" hint="Necesaria para vigilarlo">
          <Input value={form.ip} onChange={(e) => set('ip', e.target.value)} placeholder="203.0.113.10" />
        </Field>
        <Field label="Sistema operativo">
          <Input value={form.os} onChange={(e) => set('os', e.target.value)} />
        </Field>
      </div>
      <div className="grid grid-cols-3 gap-4">
        <Field label="vCPU">
          <Input type="number" min={1} value={form.cpu} onChange={(e) => set('cpu', e.target.value)} />
        </Field>
        <Field label="RAM (GB)">
          <Input type="number" min={1} value={form.ramGb} onChange={(e) => set('ramGb', e.target.value)} />
        </Field>
        <Field label="Disco (GB)">
          <Input type="number" min={1} value={form.diskGb} onChange={(e) => set('diskGb', e.target.value)} />
        </Field>
      </div>

      <Section title="Coste">
        <Checkbox label="Tiene coste" checked={hasCost} onChange={(e) => setHasCost(e.target.checked)} />
        {hasCost && <CostFields value={cost} onChange={setCost} />}
      </Section>

      <Section title="Vigilancia">
        <Checkbox label="Comprobar que responde al ping" checked={watchPing} onChange={(e) => setWatchPing(e.target.checked)} />
        <Checkbox label="Comprobar que el puerto SSH (22) está abierto" checked={watchSsh} onChange={(e) => setWatchSsh(e.target.checked)} />
        {!form.ip.trim() && (watchPing || watchSsh) && <p className="text-xs text-slate-500">Sin IP no se puede vigilar.</p>}
      </Section>

      <Progress steps={steps} error={error} done={done}>
        <p>
          Servidor añadido. <Link to="/servidores" className="text-sky-700 underline dark:text-sky-400">Ver servidores</Link>
        </p>
      </Progress>
      {!done && (
        <div className="flex justify-end">
          <Button variant="primary" onClick={submit} disabled={running || !form.name.trim() || (hasCost && !cost.amount)}>
            {running ? 'Añadiendo…' : 'Añadir servidor'}
          </Button>
        </div>
      )}
    </Card>
  )
}

/** Dominio raíz registrado al que pertenece un host (stock.t0masdev.es → t0masdev.es). */
function rootDomainFor(host: string, domains: Domain[]): Domain | undefined {
  return domains.find((d) => d.parent === null && (host === d.name || host.endsWith(`.${d.name}`)))
}

function hostOf(url: string): string | null {
  try {
    return new URL(url).hostname.toLowerCase()
  } catch {
    return null
  }
}

function AddApp() {
  const options = useLoad(() => Promise.all([listServers(), listDomains()]))
  const [form, setForm] = useState({ name: '', description: '', status: 'PRODUCTION' as ProjectStatus, url: '', repoUrl: '' })
  const [serverId, setServerId] = useState('')
  const [share, setShare] = useState('100')
  const [watchHttp, setWatchHttp] = useState(true)
  const [watchSsl, setWatchSsl] = useState(true)
  const { steps, error, running, done, run } = useSteps()
  const set = (key: keyof typeof form, value: string) => setForm((f) => ({ ...f, [key]: value }))

  const [servers, domains] = options.data ?? [[], []]
  const host = hostOf(form.url.trim())
  const root = host ? rootDomainFor(host, domains) : undefined
  const existing = host ? domains.find((d) => d.name === host) : undefined
  const isHttps = form.url.trim().toLowerCase().startsWith('https://')

  const submit = () =>
    run(async (step) => {
      const project = await step('Proyecto', () =>
        createProject({ name: form.name.trim(), description: blank(form.description), status: form.status, repoUrl: blank(form.repoUrl), publicUrl: blank(form.url), notes: null }),
      )
      if (serverId) {
        await step('Enlace con su servidor y reparto del coste', () => linkProjectServer(project.id, serverId, Number(share)))
      }
      let domainId: string | null = null
      if (host && root) {
        const domain = existing
          ? await step(`Dominio ${host} enlazado al proyecto`, () =>
              updateDomain(existing.id, {
                name: existing.name,
                parentId: existing.parent?.id ?? null,
                contractId: existing.parent ? null : (existing.contract?.id ?? null),
                serverId: serverId || existing.server?.id || null,
                projectId: project.id,
                registrar: existing.registrar,
                notes: existing.notes,
              }),
            )
          : await step(`Subdominio ${host}`, () =>
              createDomain({ name: host, parentId: root.id, contractId: null, serverId: serverId || null, projectId: project.id, registrar: null, notes: null }),
            )
        domainId = domain.id
      }
      if (form.url.trim() && watchHttp) {
        await step('Vigilancia de la web', () =>
          createHealthCheck({ name: `Web ${project.name}`, type: 'HTTP', target: form.url.trim(), port: null, serverId: serverId || null, domainId, projectId: project.id, intervalSeconds: 300, failureThreshold: 3, enabled: true }),
        )
      }
      if (host && isHttps && watchSsl) {
        await step('Vigilancia del certificado SSL', () =>
          createHealthCheck({ name: `SSL ${host}`, type: 'SSL', target: host, port: null, serverId: null, domainId, projectId: project.id, intervalSeconds: 3600, failureThreshold: 2, enabled: true }),
        )
      }
    })

  return (
    <Card className="space-y-4 p-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nombre">
          <Input value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Stock" autoFocus />
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
          <Input value={form.url} onChange={(e) => set('url', e.target.value)} placeholder="https://stock.t0masdev.es" />
        </Field>
        <Field label="Repositorio">
          <Input value={form.repoUrl} onChange={(e) => set('repoUrl', e.target.value)} placeholder="https://github.com/..." />
        </Field>
      </div>
      <Field label="Descripción">
        <Textarea value={form.description} onChange={(e) => set('description', e.target.value)} />
      </Field>

      <Section title="Dónde está">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Servidor">
            <Select value={serverId} onChange={(e) => setServerId(e.target.value)}>
              <option value="">— Ninguno todavía —</option>
              {servers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </Field>
          {serverId && (
            <Field label="Parte del coste del servidor (%)" hint="Si comparte servidor con otra app, repártelo">
              <Input type="number" min={0} max={100} value={share} onChange={(e) => setShare(e.target.value)} />
            </Field>
          )}
        </div>
        {host && (
          <p className="text-sm text-slate-600 dark:text-slate-400">
            {root
              ? existing
                ? `Se enlazará el dominio ${host}, que ya existe, con este proyecto.`
                : `Se creará el subdominio ${host} dentro de ${root.name}.`
              : `El dominio de ${host} no está dado de alta; añádelo antes como «Dominio» para enlazarlo y vigilar su renovación.`}
          </p>
        )}
      </Section>

      <Section title="Vigilancia">
        <Checkbox label="Comprobar que la web responde" checked={watchHttp} onChange={(e) => setWatchHttp(e.target.checked)} />
        <Checkbox label="Vigilar el certificado SSL y su caducidad" checked={watchSsl} disabled={!isHttps} onChange={(e) => setWatchSsl(e.target.checked)} />
      </Section>

      <Progress steps={steps} error={error} done={done}>
        <p>
          App añadida. <Link to="/proyectos" className="text-sky-700 underline dark:text-sky-400">Ver proyectos</Link>
        </p>
      </Progress>
      {!done && (
        <div className="flex justify-end">
          <Button variant="primary" onClick={submit} disabled={running || !form.name.trim() || (!!form.url.trim() && !host)}>
            {running ? 'Añadiendo…' : 'Añadir app'}
          </Button>
        </div>
      )}
    </Card>
  )
}

function AddDomain() {
  const [name, setName] = useState('')
  const [registrar, setRegistrar] = useState('')
  const [cost, setCost] = useState<CostForm>({ amount: '', period: 'YEARLY', renewalDate: today(), autoRenew: true })
  const { steps, error, running, done, run } = useSteps()

  const submit = () =>
    run(async (step) => {
      const domainName = name.trim().toLowerCase()
      const contract = await step('Contrato con su precio y renovación', () =>
        createContract({
          name: `Dominio ${domainName}`,
          provider: registrar.trim() || 'Registrador',
          type: 'DOMAIN',
          amount: Number(cost.amount),
          billingPeriod: cost.period,
          renewalDate: cost.renewalDate,
          autoRenew: cost.autoRenew,
          status: 'ACTIVE',
          notes: null,
        }),
      )
      await step('Dominio', () =>
        createDomain({ name: domainName, parentId: null, contractId: contract.id, serverId: null, projectId: null, registrar: blank(registrar), notes: null }),
      )
    })

  return (
    <Card className="space-y-4 p-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Dominio" hint="Solo el dominio, sin www ni subdominios">
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="t0masdev.es" autoFocus />
        </Field>
        <Field label="Registrador">
          <Input value={registrar} onChange={(e) => setRegistrar(e.target.value)} placeholder="Piensa Solutions" />
        </Field>
      </div>
      <Section title="Precio y renovación">
        <CostFields value={cost} onChange={setCost} periods={['YEARLY', 'MONTHLY']} />
        <p className="text-xs text-slate-500">Te avisaré 30, 15 y 7 días antes de la renovación.</p>
      </Section>
      <Progress steps={steps} error={error} done={done}>
        <p>
          Dominio añadido. Ahora, al añadir una app con una URL de este dominio, su subdominio se crea solo.{' '}
          <Link to="/dominios" className="text-sky-700 underline dark:text-sky-400">Ver dominios</Link>
        </p>
      </Progress>
      {!done && (
        <div className="flex justify-end">
          <Button variant="primary" onClick={submit} disabled={running || !name.trim() || !cost.amount}>
            {running ? 'Añadiendo…' : 'Añadir dominio'}
          </Button>
        </div>
      )}
    </Card>
  )
}
