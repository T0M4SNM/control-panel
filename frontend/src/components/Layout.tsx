import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth-context'
import NotificationBell from './NotificationBell'

interface NavItem {
  to: string
  label: string
  adminOnly?: boolean
}

const NAV: NavItem[] = [
  { to: '/', label: 'Inicio' },
  { to: '/proyectos', label: 'Proyectos' },
  { to: '/comprobaciones', label: 'Comprobaciones' },
]

/** Cabecera común con la navegación; cada pantalla se pinta en el Outlet. */
export default function Layout() {
  const { user, isAdmin, logout } = useAuth()
  const items = NAV.filter((item) => !item.adminOnly || isAdmin)

  return (
    <div className="mx-auto max-w-6xl px-4 pb-10">
      <header className="flex items-center justify-between gap-3 py-4">
        <NavLink to="/" className="flex shrink-0 items-center gap-3">
          <img src="/icon.svg" alt="" className="size-8" />
          <span className="text-lg font-semibold">Control Panel</span>
        </NavLink>
        <div className="flex shrink-0 items-center gap-3 text-sm">
          <span className="hidden text-slate-600 md:inline dark:text-slate-400">
            {user.displayName}
            {isAdmin && ' · Admin'}
          </span>
          <NotificationBell />
          <button
            onClick={logout}
            className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800"
          >
            Salir
          </button>
        </div>
      </header>

      {/* En móvil la barra se desplaza en horizontal en vez de partirse en varias líneas */}
      <nav className="-mx-4 mb-6 flex gap-1 overflow-x-auto border-b border-slate-200 px-4 pb-px text-sm dark:border-slate-800">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              `shrink-0 border-b-2 px-3 py-2 ${
                isActive
                  ? 'border-sky-600 font-medium text-slate-900 dark:text-white'
                  : 'border-transparent text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
              }`
            }
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <main>
        <Outlet />
      </main>
    </div>
  )
}
