'use strict'

/**
 * Cliente de errores del Control Panel para Node.js 18 o superior. Un solo archivo, sin dependencias.
 *
 *   const panel = require('./control-panel-errors')
 *   panel.init({ endpoint: 'https://panel.t0masdev.es', key: process.env.CONTROL_PANEL_KEY })
 *   app.use(panel.expressErrorHandler()) // después de las rutas de Express
 *
 * Envía los errores por lotes en segundo plano. Nunca lanza excepciones ni bloquea tu app: si el panel
 * no responde, el error se pierde (y se escribe en consola).
 */

const MAX_QUEUE = 100
const BATCH_SIZE = 50
const FLUSH_INTERVAL_MS = 2000
const REQUEST_TIMEOUT_MS = 5000

let config = null
let queue = []
let timer = null

/**
 * @param {object} options
 * @param {string} options.endpoint URL del panel, p. ej. https://panel.t0masdev.es
 * @param {string} options.key clave de envío del proyecto (cpk_...)
 * @param {string} [options.environment] p. ej. «production»
 * @param {string} [options.release] versión de tu app, p. ej. la de package.json
 * @param {boolean} [options.captureUncaught=true] captura excepciones y promesas rechazadas sin capturar
 */
function init(options) {
  if (!options || !options.endpoint || !options.key) {
    console.warn('[control-panel] Falta endpoint o key: los errores no se enviarán')
    return
  }
  config = {
    url: options.endpoint.replace(/\/+$/, '') + '/api/ingest/errors',
    key: options.key,
    environment: options.environment || process.env.NODE_ENV || undefined,
    release: options.release,
  }
  if (options.captureUncaught !== false) {
    installProcessHandlers()
  }
}

/**
 * Registra un error. {@code context} son datos sueltos de texto (url, method, userId...).
 * @param {unknown} error
 * @param {Record<string, unknown>} [context]
 */
function captureException(error, context) {
  if (!config) return
  const err = error instanceof Error ? error : new Error(String(error))
  if (queue.length >= MAX_QUEUE) {
    // Si el panel no da abasto, se descartan los nuevos en vez de llenar la memoria de tu app
    return
  }
  queue.push({
    type: err.name || 'Error',
    message: err.message || String(error),
    stack: err.stack,
    occurredAt: new Date().toISOString(),
    environment: config.environment,
    release: config.release,
    context: stringValues(context),
  })
  if (queue.length >= BATCH_SIZE) {
    flush()
  } else if (!timer) {
    timer = setTimeout(flush, FLUSH_INTERVAL_MS)
    // No mantiene vivo el proceso solo por esto
    timer.unref()
  }
}

/** Envía lo pendiente. Devuelve una promesa que nunca falla. */
async function flush() {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  if (!config || queue.length === 0) return
  const batch = queue.splice(0, BATCH_SIZE)
  try {
    const response = await fetch(config.url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${config.key}` },
      body: JSON.stringify({ errors: batch }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    })
    if (!response.ok) {
      console.warn(`[control-panel] El panel respondió ${response.status} al enviar ${batch.length} error(es)`)
    }
  } catch (e) {
    console.warn(`[control-panel] No se pudieron enviar ${batch.length} error(es): ${e.message}`)
  }
  if (queue.length > 0) {
    await flush()
  }
}

/**
 * Middleware de errores de Express: regístralo después de tus rutas. Envía el error con la URL y el
 * método, y lo pasa al siguiente manejador (no cambia la respuesta).
 */
function expressErrorHandler() {
  return function controlPanelErrorHandler(err, req, res, next) {
    captureException(err, { url: req.originalUrl || req.url, method: req.method })
    next(err)
  }
}

let handlersInstalled = false

/**
 * Captura lo que haría caer el proceso, lo envía y después sale con código 1, como haría Node sin
 * este cliente (así tu gestor de procesos o Docker lo reinicia igual que antes).
 */
function installProcessHandlers() {
  if (handlersInstalled) return
  handlersInstalled = true
  const crash = (error) => {
    captureException(error, { source: 'process' })
    console.error(error)
    // Hasta 3 s para enviar; después se sale igualmente
    const exit = setTimeout(() => process.exit(1), 3000)
    flush().finally(() => {
      clearTimeout(exit)
      process.exit(1)
    })
  }
  process.on('uncaughtException', crash)
  process.on('unhandledRejection', crash)
}

function stringValues(context) {
  if (!context || typeof context !== 'object') return undefined
  const result = {}
  for (const [key, value] of Object.entries(context)) {
    if (value !== undefined && value !== null) {
      result[key] = typeof value === 'string' ? value : JSON.stringify(value)
    }
  }
  return result
}

/** Solo para los tests: vuelve al estado inicial. */
function _reset() {
  config = null
  queue = []
  if (timer) clearTimeout(timer)
  timer = null
}

module.exports = { init, captureException, flush, expressErrorHandler, _reset }
