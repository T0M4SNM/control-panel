# Visitas a tus webs

La ficha de cada proyecto enseña cuánta gente usa su web: quién está **ahora** (últimos 5 minutos), visitantes y páginas vistas, la gráfica de usuarios activos en el tiempo (1 h, 24 h, 7, 30 o 90 días), las páginas más vistas y de dónde llega la gente.

## Cómo se instala

Una línea en el `<head>` de la web del proyecto (la ficha del proyecto la da ya rellenada):

```html
<script defer src="https://panel.t0masdev.es/visitas.js" data-project="ID_DEL_PROYECTO"></script>
```

Funciona con webs normales y con SPAs (React, Vue...): cuenta también los cambios de página con `history.pushState`.

## Privacidad

- **Sin cookies** ni `localStorage`: no hace falta banner de consentimiento.
- **No se guarda la IP** ni el navegador. Cada visitante es un hash SHA-256 de *sal del día + proyecto + IP + navegador*. La sal cambia cada día y la de ayer se borra a las 0:05, así que el hash no se puede deshacer ni sirve para seguir a nadie de un día para otro.
- Solo se guarda la **ruta** de la página: lo que va detrás de `?` o `#` se descarta (puede llevar tokens o datos personales).
- La procedencia es solo el dominio (`google.com`), y solo si viene de otra web.

Consecuencia: en periodos de varios días, alguien que vuelve cuenta **una vez por día**.

## Qué cuenta y qué no

- `VIEW` cada vez que se abre una página; `PING` cada minuto mientras la pestaña está visible, para saber quién sigue conectado.
- Se ignoran robots y herramientas (Googlebot, `curl`, navegadores automatizados...).
- Si el proyecto tiene **URL pública**, solo se aceptan visitas desde ese dominio y sus subdominios (con o sin `www`); si no, de cualquiera. Pon la URL pública para que nadie pueda inflarte las cifras desde otra web.
- Hasta 120 envíos por minuto desde una misma dirección (`app.visits.max-per-address-per-minute`).
- Se guardan **90 días**.

## API

### Envío (desde `visitas.js`)

```http
POST /api/ingest/visits
Content-Type: text/plain

{"project": "<id>", "kind": "VIEW", "path": "/reservar", "referrer": "google.com"}
```

Va como `text/plain` (`navigator.sendBeacon`) para que el navegador no haga la petición previa de CORS. Sin sesión ni clave: el id del proyecto es público. Responde `202`; `403` si el origen no es el de la URL pública, `404` si el proyecto no existe, `429` si se pasa del límite.

### Consulta (desde el panel)

`GET /api/projects/{id}/visits?range=HOUR|DAY|WEEK|MONTH|QUARTER` — lo ve quien ve el proyecto.

```json
{
  "range": "DAY",
  "stepSeconds": 3600,
  "points": [{ "start": "2026-10-31T15:00:00Z", "visitors": 12, "views": 30 }],
  "visitors": 259, "views": 640, "online": 3,
  "topPages": [{ "name": "/reservar", "count": 59 }],
  "topReferrers": [{ "name": "google.com", "count": 12 }]
}
```

Puntos cada 5 minutos (1 h), cada hora (24 h y 7 días) o cada día (30 y 90 días, en días de Madrid). Los huecos sin visitas vienen a cero.
