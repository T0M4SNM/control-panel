# Avisos

Avisos de vencimientos y caídas. Viven en la tabla `notification`, que es el panel dentro de la app. Web Push es solo otra forma de entregar esa misma fila al móvil u ordenador.

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

## Web Push

Notificaciones del sistema en los dispositivos donde el usuario las haya activado. Cada usuario puede tener varios dispositivos, y cada uno es una fila de `push_subscription`.

### Configuración

Se necesitan unas claves VAPID, que identifican a este servidor ante los servicios de push de Google, Mozilla, Apple y Microsoft. Se generan una sola vez. En PowerShell:

```powershell
npx web-push generate-vapid-keys
```

Y van en las variables de entorno (ver `.env.example`):

| Variable | Para qué |
|---|---|
| `APP_PUSH_VAPID_PUBLIC_KEY` | Clave pública; se da al navegador al suscribirse |
| `APP_PUSH_VAPID_PRIVATE_KEY` | Clave privada; **secreta**, nunca en el repo |
| `APP_PUSH_SUBJECT` | Contacto, `mailto:tu@email.com` |

- **Sin claves, Web Push queda desactivado** y los avisos solo se ven en el panel.
- Si cambias las claves, todos los dispositivos tienen que volver a suscribirse.

### Cómo funciona

- Cuando se confirma la transacción que creó los avisos, se envían en un hilo virtual aparte. Así, si un servicio de push va lento, no retrasa nada más.
- El contenido va cifrado de extremo a extremo según RFC 8291 (`aes128gcm`): el servicio de push no puede leerlo. Está implementado con la criptografía del JDK, sin librerías externas, y se comprueba contra el ejemplo oficial del RFC.
- Cada envío lleva una firma VAPID (RFC 8292). El servicio guarda el aviso hasta 1 día si el dispositivo está apagado (`TTL`). Los `CRITICAL` van con `Urgency: high`.
- Si el servicio responde 404 o 410, el navegador ha anulado la suscripción y se borra.
- **Solo se aceptan endpoints `https` de servicios de push conocidos**:
  - `fcm.googleapis.com`;
  - `push.services.mozilla.com`;
  - `push.apple.com`;
  - `notify.windows.com`.

  Así un usuario no puede hacer que el servidor llame a cualquier dirección (SSRF). La lista se puede cambiar con `app.push.allowed-endpoint-hosts`.
- En iOS, Web Push solo funciona con la PWA **instalada en la pantalla de inicio** e iOS 16.4 o superior.

### API

| Petición | Respuesta |
|---|---|
| `GET /api/push/config` | `{ enabled, publicKey, subscriptions }`, donde `subscriptions` es cuántos dispositivos tiene suscritos el usuario |
| `POST /api/push/subscriptions` con el `PushSubscription.toJSON()` del navegador: `{ endpoint, keys: { p256dh, auth } }` | `204`; si el endpoint ya existía, lo actualiza |
| `POST /api/push/subscriptions/unsubscribe` con `{ endpoint }` | `204`; solo da de baja dispositivos propios |

Lo que recibe el service worker (descifrado): `{ id, type, severity, title, body, url }`.

### En el frontend

- La **campana** de la cabecera muestra los avisos sin leer. El contador se actualiza cada minuto y al volver a la pestaña.
- Al pulsarla se abre el panel con los últimos 20 avisos, desde donde se marcan como leídos uno a uno o todos.
- Al pie del panel, **«Activar notificaciones en este dispositivo»** pide permiso al navegador y registra la suscripción. Si el navegador no las admite o el usuario las bloqueó, lo explica.
- `public/push-sw.js` recibe el push y muestra la notificación. Workbox lo carga dentro del service worker de la PWA. Los avisos `CRITICAL` se quedan en pantalla hasta que se tocan, y tocar uno abre la app.
- El service worker solo existe en el build de producción. Para probar el push en local hay que usar `npm run build` y luego `npm run preview` (no `npm run dev`), con las claves VAPID puestas en el backend.
