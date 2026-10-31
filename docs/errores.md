# Errores de tus apps

Tus apps envían sus excepciones al panel. El panel las agrupa como hace Sentry: el mismo error 500 veces es **un** grupo con un contador. Además te avisa cuando aparece un error **nuevo** o **vuelve** uno que habías marcado como resuelto.

## Cómo funciona

1. El admin crea una **clave de envío** para el proyecto: `cpk_...`, que solo se ve una vez.
2. La app envía sus errores a `POST /api/ingest/errors` con `Authorization: Bearer cpk_...`. En Node.js hay un cliente listo (ver `clients/node/`).
3. El panel calcula la **huella** de cada error: el tipo más las 5 primeras líneas de la pila, **sin números de línea ni columna**, que cambian en cada despliegue. Sin pila, usa el tipo y el mensaje con los números sustituidos («pedido 123» y «pedido 456» son el mismo error).
4. Si la huella es nueva, crea el grupo y **avisa** a los admins y a los miembros del proyecto: aparece en el panel y llega por push. Si el grupo estaba resuelto, lo reabre y vuelve a avisar.

## Límites

- Hasta 50 errores por petición y **600 por minuto y clave**; si te pasas, responde 429. Protege la base de datos de un bucle de errores. Se puede cambiar con `app.ingest.max-events-per-minute`.
- Tamaños máximos:
  - `type`: 200 caracteres;
  - `message`: 4.000;
  - `stack`: 32 KB;
  - `context`: 20 campos de 500 caracteres.

  Lo que pase de ahí se recorta.
- Las ocurrencias se borran a los 30 días; los grupos y sus contadores se quedan.

## API

### Envío (desde tus apps)

```http
POST /api/ingest/errors
Authorization: Bearer cpk_...
Content-Type: application/json

{ "errors": [ {
  "type": "TypeError",
  "message": "Cannot read properties of undefined (reading 'id')",
  "stack": "TypeError: ...\n    at getStock (/app/src/stock.js:42:17)\n    ...",
  "occurredAt": "2026-10-31T10:00:00Z",
  "environment": "production",
  "release": "1.4.0",
  "context": { "url": "/api/stock/7", "method": "GET" }
} ] }
```

Responde `202` con `{ "accepted": 1 }`. Errores:

- `401`: falta la clave, no es válida o está revocada;
- `400`: lote vacío, más de 50 errores o clave de servidor;
- `429`: se ha pasado el límite de envíos.

Este punto de entrada no usa sesión ni CSRF: se autentica con la clave.

### Consulta (desde el panel)

| Petición | Quién | Respuesta |
|---|---|---|
| `GET /api/projects/{id}/errors?status=OPEN` | Quien vea el proyecto | Grupos, del más reciente al más antiguo; `status` puede ser `OPEN`, `RESOLVED` o `ALL` |
| `GET /api/errors/{groupId}` | Quien vea el proyecto | El grupo y sus últimas 20 ocurrencias, con pila y contexto |
| `POST /api/errors/{groupId}/resolve` / `reopen` | Quien pueda editar el proyecto | Grupo actualizado |
| `GET /api/projects/{id}/ingest-keys` | Admin | Claves del proyecto (sin la clave en claro) |
| `POST /api/projects/{id}/ingest-keys` con `{ "name": "Producción" }` | Admin | `201` con `{ info, key }`; **`key` solo aparece aquí** |
| `DELETE /api/ingest-keys/{id}` | Admin | `204`; la clave deja de valer |
