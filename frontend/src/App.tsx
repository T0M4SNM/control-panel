import { useCallback, useEffect, useState } from 'react'
import { fetchCurrentUser, logout, type CurrentUser } from './api/auth'
import Layout, { type View } from './components/Layout'
import DashboardPage from './pages/DashboardPage'
import LoginPage from './pages/LoginPage'
import ProjectsPage from './pages/ProjectsPage'

/** La pantalla sale del hash de la URL (#/proyectos), así recargar no te devuelve al inicio. */
function viewFromHash(): View {
  return window.location.hash === '#/proyectos' ? 'proyectos' : 'inicio'
}

export default function App() {
  // undefined = comprobando la sesión; null = sin sesión
  const [user, setUser] = useState<CurrentUser | null | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [view, setView] = useState<View>(viewFromHash)

  useEffect(() => {
    fetchCurrentUser()
      .then(setUser)
      .catch(() => setError('No se puede conectar con el servidor.'))
  }, [])

  useEffect(() => {
    const onHashChange = () => setView(viewFromHash())
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  const handleSessionExpired = useCallback(() => setUser(null), [])

  async function handleLogout() {
    try {
      await logout()
    } finally {
      setUser(null)
    }
  }

  if (error) {
    return <p className="p-6 text-center text-red-600 dark:text-red-400">{error}</p>
  }
  if (user === undefined) {
    return <p className="p-6 text-center text-slate-500">Cargando…</p>
  }
  if (user === null) {
    return <LoginPage onLogin={setUser} />
  }
  return (
    <Layout user={user} view={view} onLogout={handleLogout}>
      {view === 'proyectos' ? (
        <ProjectsPage user={user} onSessionExpired={handleSessionExpired} />
      ) : (
        <DashboardPage user={user} onSessionExpired={handleSessionExpired} />
      )}
    </Layout>
  )
}
