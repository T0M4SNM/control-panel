import { useCallback, useEffect, useState } from 'react'
import { fetchCurrentUser, logout, type CurrentUser } from './api/auth'
import LoginPage from './pages/LoginPage'
import ProjectsPage from './pages/ProjectsPage'

export default function App() {
  // undefined = comprobando la sesión; null = sin sesión
  const [user, setUser] = useState<CurrentUser | null | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchCurrentUser()
      .then(setUser)
      .catch(() => setError('No se puede conectar con el servidor.'))
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
  return <ProjectsPage user={user} onLogout={handleLogout} onSessionExpired={handleSessionExpired} />
}
