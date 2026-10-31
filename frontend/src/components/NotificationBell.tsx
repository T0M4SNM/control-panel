import { useCallback, useEffect, useRef, useState } from 'react'
import {
  listNotifications,
  markAllRead,
  markRead,
  unreadCount,
  type AppNotification,
  type Severity,
} from '../api/notifications'
import { disablePush, enablePush, getPushState, type PushState } from '../api/push'

const POLL_MS = 60_000

const SEVERITY_DOT: Record<Severity, string> = {
  INFO: 'bg-sky-500',
  WARNING: 'bg-amber-500',
  CRITICAL: 'bg-red-500',
}

/** Campana con el número de avisos sin leer y su panel desplegable. */
export default function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [count, setCount] = useState(0)
  const [items, setItems] = useState<AppNotification[] | null>(null)
  const panel = useRef<HTMLDivElement>(null)

  const refreshCount = useCallback(() => {
    unreadCount().then(setCount).catch(() => {})
  }, [])

  // El contador se refresca cada minuto y al volver a la pestaña
  useEffect(() => {
    refreshCount()
    const timer = window.setInterval(refreshCount, POLL_MS)
    window.addEventListener('focus', refreshCount)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('focus', refreshCount)
    }
  }, [refreshCount])

  useEffect(() => {
    if (!open) {
      return
    }
    listNotifications().then(setItems).catch(() => setItems([]))
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (panel.current && !panel.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', closeOnOutsideClick)
    return () => document.removeEventListener('mousedown', closeOnOutsideClick)
  }, [open])

  async function handleRead(item: AppNotification) {
    if (item.readAt) {
      return
    }
    const updated = await markRead(item.id)
    setItems((current) => current?.map((n) => (n.id === item.id ? updated : n)) ?? null)
    setCount((c) => Math.max(0, c - 1))
  }

  async function handleReadAll() {
    await markAllRead()
    const now = new Date().toISOString()
    setItems((current) => current?.map((n) => ({ ...n, readAt: n.readAt ?? now })) ?? null)
    setCount(0)
  }

  return (
    <div className="relative" ref={panel}>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label={count > 0 ? `Avisos: ${count} sin leer` : 'Avisos'}
        aria-expanded={open}
        className="relative rounded-lg border border-slate-300 p-1.5 hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-5" aria-hidden>
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        {count > 0 && (
          <span className="absolute -top-1.5 -right-1.5 min-w-5 rounded-full bg-red-600 px-1 text-center text-xs leading-5 font-semibold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {open && (
        // En móvil ocupa el ancho de la pantalla; en escritorio cuelga de la campana
        <div className="fixed inset-x-4 top-16 z-10 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg sm:absolute sm:inset-x-auto sm:top-auto sm:right-0 sm:mt-2 sm:w-96 dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3 dark:border-slate-800">
            <span className="font-semibold">Avisos</span>
            {count > 0 && (
              <button onClick={handleReadAll} className="text-sm text-sky-700 hover:underline dark:text-sky-400">
                Marcar todo como leído
              </button>
            )}
          </div>

          <ul className="max-h-96 overflow-y-auto">
            {items === null && <li className="px-4 py-6 text-center text-sm text-slate-500">Cargando…</li>}
            {items?.length === 0 && (
              <li className="px-4 py-6 text-center text-sm text-slate-500">No hay avisos.</li>
            )}
            {items?.map((item) => (
              <li key={item.id}>
                <button
                  onClick={() => handleRead(item)}
                  className={`flex w-full gap-3 px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-slate-800/60 ${
                    item.readAt ? 'opacity-60' : ''
                  }`}
                >
                  <span className={`mt-1.5 size-2 shrink-0 rounded-full ${SEVERITY_DOT[item.severity]}`} />
                  <span className="min-w-0 flex-1">
                    <span className={`block text-sm ${item.readAt ? '' : 'font-semibold'}`}>{item.title}</span>
                    <span className="mt-0.5 block text-sm text-slate-600 dark:text-slate-400">{item.message}</span>
                    <span className="mt-1 block text-xs text-slate-500">{timeAgo(item.createdAt)}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>

          <PushToggle />
        </div>
      )}
    </div>
  )
}

/** Activar o desactivar las notificaciones del sistema en este dispositivo. */
function PushToggle() {
  const [state, setState] = useState<PushState | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    getPushState()
      .then(setState)
      .catch(() => setState('unsupported'))
  }, [])

  async function toggle() {
    setBusy(true)
    try {
      setState(state === 'on' ? await disablePush() : await enablePush())
    } catch {
      setState(await getPushState().catch(() => 'unsupported' as const))
    } finally {
      setBusy(false)
    }
  }

  if (state === null || state === 'disabled') {
    return null
  }
  return (
    <div className="border-t border-slate-200 px-4 py-3 text-sm dark:border-slate-800">
      {state === 'unsupported' && (
        <p className="text-slate-500">
          Este navegador no admite notificaciones. En iPhone, instala la app en la pantalla de inicio.
        </p>
      )}
      {state === 'denied' && (
        <p className="text-slate-500">Las notificaciones están bloqueadas en los ajustes del navegador.</p>
      )}
      {(state === 'on' || state === 'off') && (
        <button onClick={toggle} disabled={busy} className="text-sky-700 hover:underline disabled:opacity-60 dark:text-sky-400">
          {state === 'on' ? 'Desactivar notificaciones en este dispositivo' : 'Activar notificaciones en este dispositivo'}
        </button>
      )}
    </div>
  )
}

const relative = new Intl.RelativeTimeFormat('es', { numeric: 'auto' })

function timeAgo(iso: string): string {
  const minutes = Math.round((new Date(iso).getTime() - Date.now()) / 60_000)
  if (Math.abs(minutes) < 60) {
    return relative.format(minutes, 'minute')
  }
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) {
    return relative.format(hours, 'hour')
  }
  return relative.format(Math.round(hours / 24), 'day')
}
