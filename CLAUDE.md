# control-panel: contexto del proyecto para Claude Code

Resumen de lo decidido y hecho, para seguir sin repetir conversaciones. Última actualización: 2026-10-31.

## 1. Qué es

App web personal para gestionar proyectos e infraestructura. Responde a: qué tengo en marcha y en qué estado, qué vence y cuándo, cuánto me cuesta y cuánto me deja cada proyecto, si mis webs y servidores funcionan, cuánta gente las usa y qué errores y logs están dando.

- Propietario: Tomás Nieves (GitHub `T0M4SNM`), administrador de todo.
- Preparada para que se sumen más usuarios a los proyectos.

## 2. Cómo trabajar con Tomás

- **Español** en conversación, documentación, commits, PR e issues. Código e identificadores en inglés.
- Domina **Java**: si explicas algo, usa analogías con Java. Trabaja en Windows: comandos para **PowerShell**.
- Tú desarrollas. Entrega en **PRs pequeños y revisables** con descripción rellena. No le pidas ejecutar Git ni copiar código.
- **Puedes fusionar tus propios PRs** (`gh pr merge N --merge`) en cuanto el CI esté en verde. Nunca en rojo ni pendiente.
- Pregunta solo lo que cambie de verdad lo que vas a construir. Las decisiones de abajo están cerradas.

## 3. Infraestructura real

- **IONOS** (2 vCPU, 4 GB, 120 GB): su app **Node.js** en `stock.t0masdev.es`. **No se toca.**
- **Contabo** (4 vCPU, 8 GB, 100 GB), aún por contratar: aquí irá el panel en `panel.t0masdev.es` y una segunda app.
- Dominio `t0masdev.es` en **Piensa Solutions** (DNS ahí).
- Vigilante externo del propio panel: **Healthchecks.io** (gratis, avisa por Telegram) con un `cron` en Contabo. Sustituye al «latido inverso» que se planteó al principio.

## 4. Arquitectura (cerrada)

**Stack**
- Backend: Spring Boot **4.1.2**, Java **21**, Maven (`mvnw`), Spring Security 7, Hibernate 7 (`ddl-auto=validate`), hilos virtuales.
- Base de datos: PostgreSQL **17**, **Flyway**. Tests con **Testcontainers** (hace falta Docker).
- Frontend: Vite, React 19, TypeScript, Tailwind 4, React Router, oxlint. **PWA** con `vite-plugin-pwa` y Web Push.
- Despliegue: imágenes Docker construidas en GitHub Actions (`ghcr.io/t0m4snm/control-panel-{backend,web}`). En el servidor, un **Caddy compartido** (`deploy/proxy`, red `proxy`, un archivo por app en `sites/`) y la pila del panel (`deploy/panel`). Guía completa en `deploy/README.md`.

**Autenticación**: sesión con cookie y CSRF de SPA (`csrf.spa()`), login en `/api/auth/login`. El primer admin y el workspace se crean al arrancar desde variables `APP_BOOTSTRAP_*`.

**Convenciones de datos**
- UUID en todas las tablas (salvo `visit_event` y `log_line`, con `bigint` por volumen).
- Dinero en `numeric(10,2)` y `BigDecimal`, nunca `double`. Euros, importe total sin IVA.
- Enumerados como texto con `CHECK`. Fechas en `timestamptz`; zona de la app `Europe/Madrid` (el `Clock` la lleva).
- Usuarios en `app_user` (`user` es palabra reservada).
- **Nunca se guardan contraseñas de servidores.**

**Visibilidad**
- `workspace` > usuarios (`ADMIN` / `MEMBER`) y proyectos; miembros por proyecto con `OWNER` / `EDITOR` / `VIEWER`.
- `ADMIN` ve todo. `MEMBER` ve sus proyectos y sus servidores, dominios, comprobaciones, errores y visitas; **nunca** importes, contratos ni finanzas.
- Contratos, costes, movimientos, finanzas, usuarios, claves de envío y **logs de servidor**: solo `ADMIN`.

**Costes y tiempo**: el coste recurrente se calcula desde los contratos de los servidores, repartido con `project_server.cost_share_percent`. `movement` es para ingresos y gastos puntuales. La tarifa se copia a `time_entry.hourly_rate` al iniciar el cronómetro. Un solo cronómetro en marcha por usuario (índice único parcial).

**Monitorización y avisos**: comprobaciones `HTTP` / `SSL` / `PING` / `TCP_PORT` cada 5 minutos, alerta tras 3 fallos. Contratos avisan a 30, 15 y 7 días. Los avisos son filas de `notification` con `dedupe_key` única por usuario (`INSERT ... ON CONFLICT DO NOTHING`), y el push (RFC 8291 + VAPID, implementado con el JDK) es otra forma de entregarlas.

**Datos que envían tus apps y servidores** (claves `cpk_...`, solo se guarda su SHA-256):
- **Errores** (`/api/ingest/errors`, clave de proyecto): agrupados por huella, avisan si son nuevos o vuelven. Cliente Node.js en `clients/node/`. Ver `docs/errores.md`.
- **Visitas** (`/api/ingest/visits`, script `visitas.js`, sin clave): sin cookies ni IPs, hash con sal diaria que se borra. Ver `docs/visitas.md`.
- **Logs** (`/api/ingest/logs`, clave de servidor): enviados por **Vector** (`deploy/vector/`). Ver `docs/logs.md`.

Retenciones: resultados de comprobaciones y ocurrencias de errores 30 días, visitas 90 días, logs 7 días. Todas las tareas programadas se apagan con `app.monitoring.enabled=false`.

El modelo de datos completo está en `docs/modelo-de-datos.md`. Si algo cambia, actualízalo en el mismo PR.

## 5. Estado

**Hecho** (hoja de ruta original 1-11 y los cinco bloques acordados después, salvo el despliegue real):
migraciones y tests, CI, autenticación, proyectos y miembros, frontend PWA, servidores/dominios/contratos y reparto de costes, movimientos y finanzas, cronómetro, monitorización, avisos y Web Push, dashboard, despliegue preparado (`deploy/`), pantallas de gestión con flujo «Añadir» (servidor, app o dominio enlazados), errores de las apps, usuarios activos y logs con Vector.

**Pendiente**
- **Desplegar** en Contabo cuando Tomás lo contrate, siguiendo `deploy/README.md`: DNS, `setup-server.sh`, `.env`, arrancar, copias, Healthchecks y Vector.
- Después: conectar la app de `stock.t0masdev.es` (clave de errores, `visitas.js` y Vector en IONOS), **solo con el permiso de Tomás**, porque es su producción.

**Estructura**
```
backend/    Spring Boot. Paquete raíz io.github.t0m4snm.control_panel_backend, un paquete por dominio
frontend/   React. pages/ (una por pantalla), components/ui.tsx (kit), api/ (un archivo por recurso)
clients/    node/: cliente de errores para Node.js (un archivo, sin dependencias, tests con node --test)
deploy/     proxy/ (Caddy), panel/, vector/, scripts/ y README.md con la guía
docs/       un documento por funcionalidad + modelo-de-datos.md
.github/    ci.yml (backend, frontend, cliente Node) y release.yml (imágenes en cada push a main)
```

## 6. Reglas del repositorio

- `main` siempre estable; todo entra por PR (no se puede proteger la rama en una cuenta gratuita con repo privado).
- Ramas cortas: `feat/...`, `fix/...`, `docs/...`, `chore/...`, desde un `main` actualizado.
- **Conventional Commits** en commits y títulos de PR (`feat(errores): ...`). Descripción con `.github/pull_request_template.md`.
- **Nunca secretos en el repo**: variables de entorno, `.env.example` versionado y `.env` ignorado.
- **Una migración Flyway fusionada no se edita nunca**: se crea la siguiente (la última es `V8`). Al añadir una, actualiza también `MigrationsTests` (versiones y tablas).
- Los gráficos usan los tokens `--chart-*` de `frontend/src/index.css`, validados para contraste y daltonismo.

## 7. Desarrollo local

- Backend: en `backend/`, `.\mvnw spring-boot:run` (levanta PostgreSQL con `compose.yaml`). Tests: `.\mvnw verify`, con Docker en marcha.
- Frontend: en `frontend/`, `npm run dev`; las llamadas a `/api` van al backend de `BACKEND_URL` (por defecto `http://localhost:8080`). Antes de un PR: `npm run lint`, `npm run typecheck` y `npm run build`.
- Cliente Node: en `clients/node/`, `node --test`.

## 8. Entorno de Tomás y problemas ya resueltos

- Windows 11, PowerShell, VS Code, Java 21, Git, Docker Desktop, Node 24.
- Docker Desktop no arrancaba: se resolvió con `DISM /Online /Cleanup-Image /StartComponentCleanup` y activando `VirtualMachinePlatform`.
- Rutas largas de Windows con worktrees: usa una carpeta corta.
- `Set-Content` de PowerShell 5 escribe con BOM y rompe `javac`: escribe archivos con `-Encoding utf8` sin BOM o con las herramientas de edición.
- Al pasar JSON con tildes a `curl.exe` desde Windows, envíalo por stdin (`--data-binary @-`).
