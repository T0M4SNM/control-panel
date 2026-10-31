# Tiempo: cronómetro y valoración

Registro del tiempo dedicado a cada proyecto y su valoración en euros con la tarifa por hora de cada usuario.

## Quién puede qué

| Acción | `ADMIN` | `OWNER` / `EDITOR` del proyecto | `VIEWER` | Sin acceso al proyecto |
|---|:-:|:-:|:-:|:-:|
| Registrar su propio tiempo | ✅ | ✅ | 403 | 404 |
| Ver entradas | Las de todos | Solo las suyas | Solo las suyas | — |
| Editar o borrar entradas | Las de todos | Solo las suyas | Solo las suyas | — |
| Ver tarifa y coste | ✅ | Nunca | Nunca | — |
| Fijar la tarifa de un usuario | ✅ | 403 | 403 | — |

Las entradas de otro usuario responden 404 a un miembro, como si no existieran.

## Reglas

- **Un solo cronómetro en marcha por usuario.** Lo garantiza un índice único parcial en la base. Iniciar uno nuevo **para el anterior** automáticamente, como en Toggl.
- La duración no se guarda: se calcula. Mientras corre, `durationSeconds` es el tiempo hasta ahora.
- **La tarifa se copia** de `app_user.hourly_rate` a la entrada al iniciarla. Cambiar después la tarifa del usuario no reescribe el histórico. El admin puede corregir la tarifa de una entrada concreta.
- Una entrada sin tarifa vale 0 €.
- Las entradas manuales necesitan hora de fin (para tiempo en curso está el cronómetro), el fin debe ser posterior al inicio y no se puede registrar tiempo en el futuro.

## API

| Petición | Respuesta |
|---|---|
| `GET /api/timer` | Cronómetro en marcha del usuario; `204` si no hay ninguno |
| `POST /api/timer/start` con `{ "projectId": "…", "description": "opcional" }` | `201` con la entrada nueva; para la anterior si había |
| `POST /api/timer/stop` | Entrada parada; `404` si no había ninguna |
| `GET /api/time-entries?from=2026-10-01&to=2026-10-31&projectId=…&userId=…` | Entradas que empiezan en esas fechas (ambas incluidas, hora de Madrid). Por defecto, los últimos 30 días; máximo 366. `userId` solo para el admin |
| `POST /api/time-entries` | `201`, entrada manual terminada |
| `PUT /api/time-entries/{id}` | Entrada actualizada |
| `DELETE /api/time-entries/{id}` | `204` |
| `GET /api/users` | Solo admin: usuarios del workspace con su tarifa |
| `PUT /api/users/{id}/hourly-rate` con `{ "hourlyRate": 30 }` | Solo admin; `null` la quita |

Cuerpo de una entrada manual: `{ projectId, startedAt, endedAt, description, hourlyRate }`. Las fechas van en ISO-8601 (`2026-10-31T09:00:00Z`) y `hourlyRate` solo lo puede enviar el admin.

Respuesta:

```json
{
  "id": "…", "user": { "id": "…", "name": "Tomás" }, "project": { "id": "…", "name": "Control Panel" },
  "startedAt": "2026-10-31T09:00:00Z", "endedAt": "2026-10-31T11:00:00Z", "running": false,
  "durationSeconds": 7200, "description": "Diseño", "hourlyRate": 30.00, "cost": 60.00
}
```

`hourlyRate` y `cost` solo llegan al admin.
