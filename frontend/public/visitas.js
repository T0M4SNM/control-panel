/*
 * Contador de visitas del Control Panel. Sin cookies, sin guardar IPs y sin dependencias.
 *
 *   <script defer src="https://panel.t0masdev.es/visitas.js" data-project="ID_DEL_PROYECTO"></script>
 *
 * Avisa al panel cuando se abre una página (también al navegar dentro de una SPA) y, cada minuto,
 * mientras la pestaña sigue visible, para saber quién está conectado ahora.
 */
;(function () {
  var script = document.currentScript
  if (!script || navigator.webdriver) return
  var project = script.getAttribute('data-project')
  if (!project) return
  var endpoint = new URL('/api/ingest/visits', script.src).href
  var lastPath = null
  var referrer = null
  try {
    var from = document.referrer && new URL(document.referrer)
    if (from && from.host !== location.host) referrer = from.host
  } catch {
    // Procedencia no válida: se ignora
  }

  function send(kind) {
    var body = { project: project, kind: kind }
    if (kind === 'VIEW') {
      body.path = location.pathname
      if (referrer) body.referrer = referrer
      // La procedencia solo cuenta en la primera página de la visita
      referrer = null
    }
    var data = JSON.stringify(body)
    // Una cadena viaja como text/plain: sin petición previa de CORS
    if (navigator.sendBeacon && navigator.sendBeacon(endpoint, data)) return
    fetch(endpoint, { method: 'POST', body: data, keepalive: true, mode: 'no-cors' }).catch(function () {})
  }

  function view() {
    if (location.pathname === lastPath) return
    lastPath = location.pathname
    send('VIEW')
  }

  var pushState = history.pushState
  history.pushState = function () {
    pushState.apply(this, arguments)
    view()
  }
  addEventListener('popstate', view)
  setInterval(function () {
    if (document.visibilityState === 'visible') send('PING')
  }, 60000)
  view()
})()
