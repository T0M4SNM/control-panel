# Avisos

Avisos de vencimientos y caídas. Viven en la tabla `notification`, que es el panel dentro de la app. El envío por Web Push se añadirá como otra forma de entregar esa misma fila.

## Qué avisa y a quién

| Tipo | Cuándo | Gravedad | Destinatarios |
|---|---|---|---|
| `CONTRACT_EXPIRING` | A 30, 15 y 7 días del `renewal_date` de un contrato activo | 30: `INFO`, 15: `WARNING`, 7: `CRITICAL` | Admins |
| `SSL_EXPIRING` | A 30, 15 y 7 días de que caduque el certificado (último resultado con éxito de cada comprobación `SSL`) | Igual | Admins + miembros de los proyectos afectados |
| `CHECK_DOWN` | Una comprobación pasa a `DOWN` | `CRITICAL` | Admins + miembros de los proyectos afectados |
| `CHECK_RECOVERED` | Una comprobación vuelve a `UP` tras estar en `DOWN` | `INFO` | Admins + miembros de los proyectos afectados |

- Solo reciben avisos los usuarios **activos**.
- Los **proyectos afectados** por una comprobación son tres: el suyo, el de su dominio y los que usan su servidor.
- Pasar de `UNKNOWN` a `UP` (la primera comprobación con éxito) no avisa.
- Los umbrales de 30, 15 y 7 días son globales de la aplicación.

## Cómo se evita repetir avisos

- Cada aviso lleva una `dedupe_key` única por usuario. Por ejemplo:
  - `contract:{id}:7d:{renewal_date}`;
  - `ssl:{checkId}:30d:{fecha de caducidad}`;
  - `check:{id}:down:{instante del cambio}`.
- Se insertan con `INSERT ... ON CONFLICT (user_id, dedupe_key) DO NOTHING`: aunque la tarea se ejecute dos veces, la base solo deja pasar uno.
- Si cambia la fecha de renovación de un contrato, la clave cambia y vuelve a avisar en su momento.
- **Solo se manda el umbral más cercano ya alcanzado.** Si el servidor estuvo apagado y a un contrato le quedan 5 días, llega el aviso de 7, no los tres a la vez.

## Cuándo se generan

- **Vencimientos**: cada día a las 8:00 (hora de Madrid) y también al arrancar la aplicación, por si estaba apagada a esa hora.
- **Caídas y recuperaciones**: en el momento, dentro de la misma transacción que guarda el resultado de la comprobación.

Configuración:

| Propiedad | Por defecto | Para qué |
|---|---|---|
| `app.notifications.enabled` | `true` | `false` desactiva la tarea diaria |
| `app.notifications.expiry-cron` | `0 0 8 * * *` | Cuándo se revisan los vencimientos |

## API del panel

Cada usuario solo ve sus propios avisos.

| Petición | Respuesta |
|---|---|
| `GET /api/notifications?unreadOnly=false&limit=50` | Avisos, del más reciente al más antiguo (máximo 200) |
| `GET /api/notifications/unread-count` | `{ "count": 3 }`, para el contador de la campana |
| `POST /api/notifications/{id}/read` | Marca uno como leído; 404 si no es tuyo |
| `POST /api/notifications/read-all` | Marca todos como leídos |

Un aviso: `{ id, type, severity, title, message, contractId, healthCheckId, createdAt, readAt }`. Si `readAt` es nulo, está sin leer.
