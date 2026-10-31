import type { ReactNode } from 'react'
import type { CurrentUser } from '../api/auth'
import NotificationBell from './NotificationBell'

export type View = 'inicio' | 'proyectos'

const NAV: { view: View; label: string }[] = [
  { view: 'inicio', label: 'Inicio' },
  { view: 'proyectos', label: 'Proyectos' },
]

interface Props {
  user: CurrentUser
  view: View
  onLogout: () => void
  children: ReactNode
}

/** Cabecera común: logo, navegación, usuario, campana de avisos y salir. */
export default function Layout({ user, view, onLogout, children }: Props) {
  return (
    <div className="mx-auto max-w-5xl px-4 pb-10">
      <header className="flex items-center justify-between gap-3 py-5">
        <div className="flex min-w-0 items-center gap-3 sm:gap-6">
          <a href="#/inicio" className="flex shrink-0 items-center gap-3">
            <img src="/icon.svg" alt="" className="size-8" />
            <span className="hidden text-lg font-semibold sm:inline">Control Panel</span>
          </a>
          <nav className="flex gap-1 text-sm">
            {NAV.map((item) => (
              <a
                key={item.view}
                href={`#/${item.view}`}
                aria-current={view === item.view ? 'page' : undefined}
                className={`rounded-lg px-3 py-1.5 ${
                  view === item.view
                    ? 'bg-slate-200 font-medium dark:bg-slate-800'
                    : 'text-slate-600 hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800/60'
                }`}
              >
                {item.label}
              </a>
            ))}
          </nav>
        </div>
        <div className="flex shrink-0 items-center gap-3 text-sm">
          <span className="hidden text-slate-600 md:inline dark:text-slate-400">
            {user.displayName}
            {user.role === 'ADMIN' && ' · Admin'}
          </span>
          <NotificationBell />
          <button
            onClick={onLogout}
            className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800"
          >
            Salir
          </button>
        </div>
      </header>
      <main>{children}</main>
    </div>
  )
}
