/**
 * Cliente HTTP de la API. La sesión viaja en una cookie que gestiona el navegador; aquí solo hay
 * que reenviar el token CSRF (cookie XSRF-TOKEN) en la cabecera X-XSRF-TOKEN de cada petición que
 * modifica algo. Ver docs/autenticacion.md.
 */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

function readCookie(name: string): string | undefined {
  const prefix = `${name}=`
  const cookie = document.cookie.split('; ').find((c) => c.startsWith(prefix))
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : undefined
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (typeof init.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (!SAFE_METHODS.includes(method)) {
    const token = readCookie('XSRF-TOKEN')
    if (token) {
      headers.set('X-XSRF-TOKEN', token)
    }
  }

  const response = await fetch(path, { ...init, method, headers, credentials: 'same-origin' })

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response))
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }
  return (await response.json()) as T
}

/** Los errores llegan como application/problem+json; si no, se usa un texto genérico. */
async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string; title?: string }
    return problem.detail ?? problem.title ?? `Error ${response.status}`
  } catch {
    return `Error ${response.status}`
  }
}
