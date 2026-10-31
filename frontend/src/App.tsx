import { useCallback, useEffect, useMemo, useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { fetchCurrentUser, logout, type CurrentUser } from './api/auth'
import { AuthContext, type Auth } from './auth-context'
import Layout from './components/Layout'
import ChecksPage from './pages/ChecksPage'
import DashboardPage from './pages/DashboardPage'
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

  const sessionExpired = useCallback(() => setUser(null), [])

  const auth = useMemo<Auth | null>(
    () =>
      user
        ? {
            user,
            isAdmin: user.role === 'ADMIN',
            sessionExpired,
            logout: () => {
              logout().finally(() => setUser(null))
            },
          }
        : null,
    [user, sessionExpired],
  )

  if (error) {
    return <p className="p-6 text-center text-red-600 dark:text-red-400">{error}</p>
  }
  if (user === undefined) {
    return <p className="p-6 text-center text-slate-500">Cargando…</p>
  }
  if (!auth) {
    return <LoginPage onLogin={setUser} />
  }
  return (
    <AuthContext.Provider value={auth}>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<DashboardPage />} />
            <Route path="proyectos" element={<ProjectsPage />} />
            <Route path="comprobaciones" element={<ChecksPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthContext.Provider>
  )
}
