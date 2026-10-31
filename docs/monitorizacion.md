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

## API

| Petición | Quién | Respuesta |
|---|---|---|
| `GET /api/health-checks` | Todos | Comprobaciones visibles, por nombre |
| `GET /api/health-checks/{id}` | Todos | Una; 404 si no es visible |
| `GET /api/health-checks/{id}/results?limit=50` | Todos | Últimos resultados, del más reciente al más antiguo (máximo 500) |
| `GET /api/projects/{id}/health-checks` | Quien vea el proyecto | Comprobaciones del proyecto, de sus servidores y de sus dominios |
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
