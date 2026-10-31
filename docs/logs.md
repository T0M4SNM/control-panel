# Logs de los servidores

La pantalla **Logs** (solo el admin) enseña lo que escriben los contenedores Docker y el sistema de cada servidor: peticiones, errores de las apps, intentos de entrar por SSH, el kernel matando procesos por falta de memoria... Se puede filtrar por origen, nivel y texto, y en **directo** se actualiza cada 5 segundos.

Solo el admin, porque en un servidor puede haber contenedores de varios proyectos y sus logs no deben cruzarse entre miembros.

## Cómo llegan

En cada servidor corre [Vector](https://vector.dev), un programa pequeño (unos 50 MB de RAM) que lee los logs y los envía al panel por HTTPS con una **clave del servidor**. La plantilla está en `deploy/vector/` y los pasos en «Logs del servidor» de `deploy/README.md`.

Vector lee:

- la salida de **todos los contenedores Docker** (menos él mismo);
- `/var/log/syslog`, `/var/log/auth.log` y `/var/log/kern.log`.

Si el panel no responde, Vector guarda lo pendiente en disco (hasta 256 MB) y reintenta.

## Qué guarda el panel

- **Origen**: nombre del contenedor, unidad de systemd o archivo (`auth.log`).
- **Nivel**: el que traiga el evento (`level`, `severity` o `PRIORITY` de journald). Si no trae ninguno, el primero que aparezca al principio del mensaje: `ERROR`, `[warn]`, `level=info`... Si no hay ninguno, queda sin nivel.
- **Mensaje**: hasta 8.000 caracteres.
- **Hora**: la del evento. Si falta o viene del futuro (por un reloj desajustado), la de llegada.

Se guardan **7 días**: se purgan cada hora.

## Límites

- Hasta 1.000 líneas por petición.
- **6.000 líneas por minuto y clave** (`app.logs.max-lines-per-minute`). Si se pasa, el panel responde 429 y Vector reintenta más tarde.

## API

### Envío (desde Vector)

```http
POST /api/ingest/logs
Authorization: Bearer cpk_...

[{"message": "GET /api 200", "container_name": "/stock-app", "timestamp": "2026-10-31T10:00:00Z"}]
```

Acepta un array JSON (lo que envía el sink `http` de Vector con `codec: json`), un solo objeto o un objeto JSON por línea. Responde `202 {"accepted": n}`. Con una clave de proyecto responde `400`.

### Consulta (desde el panel)

- `GET /api/servers/{id}/logs?source=&level=&q=&limit=200`: de la más reciente a la más antigua. `level` es el mínimo: `WARN` incluye `ERROR`. Para la página siguiente se añaden `beforeTime` y `beforeId` de la última línea.
- `GET /api/servers/{id}/log-sources`: orígenes con su número de líneas, de errores y la última.
- `GET` y `POST /api/servers/{id}/ingest-keys`: claves del servidor. Se revocan con `DELETE /api/ingest-keys/{id}`.
