import { useEffect, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth-context'
import { getDashboard, type Dashboard, type RunningTimer } from '../api/dashboard'
import { formatDate, formatDateTime, formatDaysLeft, formatDuration, formatEuros } from '../format'

const REFRESH_MS = 60_000

/** Pantalla de inicio: qué está en marcha, qué vence y cuánto cuesta, de un vistazo. */
export default function DashboardPage() {
  const { user, isAdmin, sessionExpired: onSessionExpired } = useAuth()
  const [data, setData] = useState<Dashboard | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const load = () =>
      getDashboard()
        .then((d) => {
          setData(d)
          setError(null)
        })
        .catch((err) => {
          if (err instanceof ApiError && err.status === 401) {
            onSessionExpired()
          } else {
            setError('No se ha podido cargar el resumen.')
          }
        })
    load()
    const timer = window.setInterval(load, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [onSessionExpired])

  if (error && !data) {
    return <p className="text-red-600 dark:text-red-400">{error}</p>
  }
  if (!data) {
    return <p className="text-slate-500">Cargando…</p>
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Hola, {user.displayName.split(' ')[0]}</h1>

      {data.runningTimer && <TimerBanner timer={data.runningTimer} />}

      <div className={`grid gap-4 ${isAdmin ? 'grid-cols-2 lg:grid-cols-4' : 'grid-cols-2'}`}>
        <Stat label="Proyectos" value={String(data.projects.total)}>
          {data.projects.byStatus.PRODUCTION} en producción · {data.projects.byStatus.DEVELOPMENT} en desarrollo
        </Stat>
        <Stat label="Comprobaciones" value={`${data.checks.byStatus.UP}/${data.checks.total}`}>
          {data.checks.total === 0
            ? 'Ninguna configurada'
            : data.checks.byStatus.DOWN > 0
              ? `${data.checks.byStatus.DOWN} caída${data.checks.byStatus.DOWN === 1 ? '' : 's'}`
              : 'Todas responden'}
        </Stat>
        {data.costs && (
          <Stat label="Coste mensual" value={formatEuros(data.costs.totalMonthly)}>
            {formatEuros(data.costs.totalYearly)} al año
          </Stat>
        )}
        {data.thisMonth && (
          <Stat
            label="Beneficio este mes"
            value={formatEuros(data.thisMonth.profit)}
            tone={data.thisMonth.profit < 0 ? 'bad' : 'neutral'}
          >
            {formatEuros(data.thisMonth.income)} ingresos · {formatEuros(data.thisMonth.realProfit)} con tu tiempo
          </Stat>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Section title="Estado">
          {data.checks.down.length === 0 ? (
            <p className="text-sm text-slate-600 dark:text-slate-400">
              {data.checks.total === 0 ? 'Aún no hay comprobaciones.' : '✓ Todo responde.'}
            </p>
          ) : (
            <ul className="divide-y divide-slate-200 dark:divide-slate-800">
              {data.checks.down.map((check) => (
                <li key={check.id} className="flex items-start gap-3 py-2">
                  <span className="mt-1.5 size-2 shrink-0 rounded-full bg-red-500" />
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{check.name}</p>
                    <p className="truncate text-sm text-slate-500">{check.target}</p>
                    {check.since && <p className="text-xs text-slate-500">Caída desde {formatDateTime(check.since)}</p>}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {data.sslExpiring.length > 0 && (
            <ul className="mt-3 space-y-2 border-t border-slate-200 pt-3 dark:border-slate-800">
              {data.sslExpiring.map((ssl) => (
                <li key={ssl.checkId} className="flex items-center justify-between gap-3 text-sm">
                  <span className="truncate">Certificado de {ssl.target}</span>
                  <DaysBadge days={ssl.daysLeft} />
                </li>
              ))}
            </ul>
          )}
        </Section>

        {data.renewals && (
          <Section title="Próximos vencimientos">
            {data.renewals.length === 0 ? (
              <p className="text-sm text-slate-600 dark:text-slate-400">Nada vence en los próximos 60 días.</p>
            ) : (
              <ul className="divide-y divide-slate-200 dark:divide-slate-800">
                {data.renewals.map((r) => (
                  <li key={r.id} className="flex items-center justify-between gap-3 py-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{r.name}</p>
                      <p className="text-sm text-slate-500">
                        {formatDate(r.renewalDate)} · {formatEuros(r.amount)}
                        {r.billingPeriod === 'MONTHLY' ? '/mes' : '/año'}
                        {!r.autoRenew && ' · renovación manual'}
                      </p>
                    </div>
                    <DaysBadge days={r.daysLeft} />
                  </li>
                ))}
              </ul>
            )}
          </Section>
        )}
      </div>
    </div>
  )
}

function Stat({
  label,
  value,
  tone = 'neutral',
  children,
}: {
  label: string
  value: string
  tone?: 'neutral' | 'bad'
  children: ReactNode
}) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${tone === 'bad' ? 'text-red-600 dark:text-red-400' : ''}`}>{value}</p>
      <p className="mt-1 text-xs text-slate-500">{children}</p>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <h2 className="mb-3 font-semibold">{title}</h2>
      {children}
    </section>
  )
}

/** Rojo si ya pasó o queda una semana, ámbar hasta 15 días, gris después. */
function DaysBadge({ days }: { days: number }) {
  const style =
    days <= 7
      ? 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
      : days <= 15
        ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
        : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
  return <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${style}`}>{formatDaysLeft(days)}</span>
}

/** Cronómetro en marcha, con el tiempo actualizándose cada segundo. */
function TimerBanner({ timer }: { timer: RunningTimer }) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const tick = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(tick)
  }, [])

  const seconds = Math.max(0, Math.floor((now - new Date(timer.startedAt).getTime()) / 1000))
  return (
    <div className="flex items-center gap-3 rounded-xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm dark:border-sky-900 dark:bg-sky-950/50">
      <span className="size-2 animate-pulse rounded-full bg-sky-500" />
      <span className="min-w-0 flex-1 truncate">
        Cronómetro en marcha en <strong>{timer.project.name}</strong>
        {timer.description && ` · ${timer.description}`}
      </span>
      <span className="font-mono tabular-nums">{formatDuration(seconds)}</span>
    </div>
  )
}
