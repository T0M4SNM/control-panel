# Autenticación

Login con email y contraseña y **sesión con cookie**. El frontend (SPA) y la API se sirven desde el mismo origen detrás de Nginx, así que no hace falta JWT: el navegador envía la cookie de sesión solo.

## Decisiones

| Tema | Decisión | Motivo |
|---|---|---|
| Sesión | Cookie `JSESSIONID`, `HttpOnly`, `SameSite=Lax`, 7 días | Mismo origen; el JavaScript no puede leer la sesión, así que un XSS no la roba |
| CSRF | `csrf.spa()` de Spring Security: cookie `XSRF-TOKEN` + cabecera `X-XSRF-TOKEN` | Es necesario con sesiones por cookie |
| Contraseñas | BCrypt mediante `DelegatingPasswordEncoder` (hash con prefijo `{bcrypt}`) | Permite cambiar de algoritmo más adelante sin invalidar los hashes |
| Email | Se guarda en minúsculas y el login no distingue mayúsculas | |
| Usuario desactivado | `active = false` impide el login | |
| Primer admin | Variables de entorno `APP_BOOTSTRAP_*` (ver `.env.example`) | Solo actúa si la base no tiene usuarios; no hay contraseñas en el repo |

## Contrato para el frontend

Todas las respuestas de error son solo un código de estado, sin cuerpo.

| Petición | Respuesta |
|---|---|
| `GET /api/auth/csrf` | `204`, deja la cookie `XSRF-TOKEN`. Llamarlo al arrancar la app y tras el login/logout |
| `POST /api/auth/login`, formulario `email` y `password` (`application/x-www-form-urlencoded`), cabecera `X-XSRF-TOKEN` | `204` y cookie de sesión; `401` si las credenciales no valen; `403` si falta el token CSRF |
| `POST /api/auth/logout`, cabecera `X-XSRF-TOKEN` | `204` |
| `GET /api/auth/me` | `200` con `{ id, workspaceId, email, displayName, role }`; `401` sin sesión |
| Cualquier otra ruta de `/api` sin sesión | `401` (nunca redirige a una página de login) |
| `GET /actuator/health` | Público |

Toda petición que modifica algo (`POST`, `PUT`, `PATCH`, `DELETE`) debe llevar la cabecera `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`. Spring Security renueva el token al hacer login, por eso hay que volver a leerlo.

## Crear el primer administrador en local

En PowerShell, desde `backend/`:

```powershell
$env:APP_BOOTSTRAP_ADMIN_EMAIL = "tu@email.com"
$env:APP_BOOTSTRAP_ADMIN_PASSWORD = "una-contraseña-de-12-o-más"
$env:APP_BOOTSTRAP_ADMIN_NAME = "Tomás"
.\mvnw spring-boot:run
```

En la consola aparece `Creado el primer administrador ...`. En los siguientes arranques ya no hace nada.

## Pendiente para más adelante

- En producción, marcar la cookie como `Secure` (`server.servlet.session.cookie.secure=true`) y confiar en las cabeceras del proxy (`server.forward-headers-strategy=native`). Se hará en el PR de despliegue.
- Gestión de usuarios (invitar miembros, cambiar contraseña) y límite de intentos de login.
- Las sesiones viven en memoria: un reinicio del backend cierra todas las sesiones.
