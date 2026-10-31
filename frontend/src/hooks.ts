import { useCallback, useEffect, useState } from 'react'
import { useAuth } from './auth-context'
import { ApiError } from './api/client'

export interface Loaded<T> {
  data: T | null
  error: string | null
  loading: boolean
  /** Vuelve a pedir los datos, p. ej. después de guardar algo */
  reload: () => void
}

/**
 * Carga datos de la API al montar el componente. Si la sesión caducó (401) vuelve al login; otros
 * errores quedan en {@code error} para mostrarlos.
 */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[] = []): Loaded<T> {
  const { sessionExpired } = useAuth()
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const stableLoad = useCallback(load, deps)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    stableLoad()
      .then((result) => {
        if (!cancelled) {
          setData(result)
          setError(null)
        }
      })
      .catch((err) => {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 401) {
          sessionExpired()
        } else {
          setError(errorMessage(err))
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [stableLoad, version, sessionExpired])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  return { data, error, loading, reload }
}

/** Mensaje para el usuario a partir de un error de la API o de red. */
export function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 403) return 'No tienes permiso para hacer esto.'
    if (err.status === 404) return 'No existe o no tienes acceso.'
    if (err.status >= 500) return 'Error del servidor. Inténtalo de nuevo.'
    return err.message
  }
  return 'No se puede conectar con el servidor.'
}
