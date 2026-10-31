# Monitorización

Comprobaciones automáticas de que webs, certificados, servidores y puertos responden.

## Quién ve qué

- **`ADMIN`**: ve y gestiona todas las comprobaciones del workspace.
- **`MEMBER`**: ve, sin poder cambiarlas, las comprobaciones ligadas a sus proyectos. Una comprobación está ligada a un proyecto si cumple alguna de estas condiciones:
  - su `project` es ese proyecto;
  - su `server` está vinculado al proyecto (`project_server`);
  - su `domain` está asignado al proyecto.

  Las que no ve responden 404.

## Tipos

| Tipo | `target` | `port` | Éxito si… |
|---|---|---|---|
| `HTTP` | URL completa `http://` o `https://` | No se admite (va en la URL) | responde con 2xx o 3xx |
| `SSL` | Host o IP | Opcional, 443 por defecto | el certificado es válido; guarda cuándo caduca |
| `PING` | Host o IP | No se admite | el host responde |
| `TCP_PORT` | Host o IP | Obligatorio | el puerto acepta conexiones |

## Estado

- Cada comprobación empieza en `UNKNOWN`.
- Un éxito la pone en `UP` y reinicia el contador de fallos.
- Pasa a `DOWN` solo tras `failureThreshold` fallos **seguidos** (3 por defecto), para no alertar por un fallo puntual de red.
- `statusChangedAt` guarda cuándo cambió de estado por última vez.
- Si se edita el tipo, el destino o el puerto, el estado vuelve a `UNKNOWN`: el anterior era de otra cosa.

## Ejecución

- Una tarea programada mira **cada 30 s** qué comprobaciones activas tocan según su `intervalSeconds` y las ejecuta. Con el intervalo por defecto (5 min), cada una se ejecuta como mucho 30 s tarde.
- Se ejecutan **en paralelo con hilos virtuales** (Java 21): una web lenta no retrasa a las demás y casi no consume memoria.
- Cada resultado se guarda en su propia transacción. Si falla al guardar una comprobación, las demás no se pierden.
- Cuando una comprobación cambia de estado, se publica un evento `HealthCheckStatusChanged`, que usarán los avisos del paso 10.
- **Purga:** cada noche a las 3:30 (hora de Madrid) se borran los resultados de más de 30 días con una sola sentencia `DELETE`.

Detalles de cada tipo:

- **`HTTP`**: hace un `GET` con `User-Agent: control-panel-monitor`. Sigue redirecciones, salvo de https a http.
- **`SSL`**: valida el certificado como un navegador: autoridad de confianza, fecha y nombre del host (SNI).
- **`PING`**: usa ICMP si el proceso puede. En Docker hace falta la capacidad `NET_RAW`, que Docker da por defecto. Si no puede, prueba el puerto TCP 7 y cuenta como vivo un host que rechaza la conexión.
- **`TCP_PORT`**: abre la conexión y la cierra sin enviar nada.

Configuración (`application.properties` o variables de entorno):

| Propiedad | Por defecto | Para qué |
|---|---|---|
| `app.monitoring.enabled` | `true` | `false` desactiva las tareas programadas, p. ej. en desarrollo |
| `app.monitoring.tick` | `30s` | Cada cuánto se miran las comprobaciones pendientes |
| `app.monitoring.purge-cron` | `0 30 3 * * *` | Cuándo se purgan los resultados antiguos |

## API

| Petición | Quién | Respuesta |
|---|---|---|
| `GET /api/health-checks` | Todos | Comprobaciones visibles, por nombre |
| `GET /api/health-checks/{id}` | Todos | Una; 404 si no es visible |
| `GET /api/health-checks/{id}/results?limit=50` | Todos | Últimos resultados, del más reciente al más antiguo (máximo 500) |
| `GET /api/projects/{id}/health-checks` | Quien vea el proyecto | Comprobaciones del proyecto, de sus servidores y de sus dominios |
| `POST /api/health-checks/{id}/run` | Admin | La ejecuta ahora mismo y devuelve el resultado; actualiza el estado igual que la tarea programada |
| `POST /api/health-checks` | Admin | `201` |
| `PUT /api/health-checks/{id}` | Admin | Comprobación actualizada |
| `DELETE /api/health-checks/{id}` | Admin | `204`; borra también su histórico |

Cuerpo de una comprobación:

```json
{
  "name": "Web de reservas", "type": "HTTP", "target": "https://reservas.ejemplo.com", "port": null,
  "serverId": null, "domainId": null, "projectId": "…",
  "intervalSeconds": 300, "timeoutMs": 10000, "failureThreshold": 3, "enabled": true
}
```

Límites:

| Campo | Mínimo | Máximo | Por defecto |
|---|---|---|---|
| `intervalSeconds` | 60 | 86400 | 300 |
| `timeoutMs` | 1000 | 30000 | 10000 |
| `failureThreshold` | 1 | 10 | 3 |

`enabled` vale `true` por defecto.

Un resultado: `{ checkedAt, success, responseMs, statusCode, error, sslExpiresAt }`. `statusCode` solo existe en `HTTP` y `sslExpiresAt` solo en `SSL`.
