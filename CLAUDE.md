# control-panel: contexto del proyecto para Claude Code

Este archivo resume todo lo decidido y hecho hasta ahora para poder continuar el desarrollo sin repetir conversaciones. Última actualización: 2026-10-31.

## 1. Qué es

App web personal de gestión de proyectos e infraestructura. Responde a tres preguntas: qué tengo en marcha y en qué estado, qué vence y cuándo, y cuánto me cuesta y cuánto me deja cada proyecto.

- Propietario: Tomás Nieves (GitHub: `T0M4SNM`). Es el administrador de todo.
- Situación de partida: 2 proyectos en marcha, 2 VPS (IONOS y Contabo), 2 subdominios. Ningún proyecto genera ingresos todavía.
- Preparada desde el diseño para que se sumen más usuarios a los proyectos.

## 2. Cómo trabajar con Tomás

- Idioma: **español** para conversación, documentación, commits, PR e issues. Código e identificadores técnicos en inglés (como ya están las tablas).
- Domina **Java**. Si explicas algo, usa analogías con Java.
- Ahora quiere que **tú desarrolles** y no le interesa conocer la app a fondo. Entrégale trabajo en **PRs pequeños y revisables** con descripción rellena; a él le toca revisar por encima y fusionar. No le pidas ejecutar comandos de Git ni copiar código salvo que sea imprescindible.
- Es estudiante de Ingeniería Informática y trabaja en Windows. Si le pides ejecutar algo, dáselo para **PowerShell** (no bash).
- Pregunta solo lo que cambie de verdad lo que vas a construir. Las decisiones de abajo ya están tomadas: no las reabras.

## 3. Decisiones de arquitectura (cerradas)

**Stack**
- Backend: Spring Boot **4.1.2**, Java **21**, Maven (con `mvnw`), API REST, Spring Security.
- Base de datos: PostgreSQL **17**, migraciones con **Flyway** (`flyway-database-postgresql`).
- Frontend: **React + Vite + Tailwind + TypeScript**, como **PWA** (instalable, con notificaciones push). Sin Next.js. Se compila fuera del servidor y se sirve estático con Nginx.
- Despliegue: Docker Compose en el VPS de IONOS (1 vCore, 2 GB RAM, 60 GB NVMe, Ubuntu 24, vacío).

**Convenciones de datos**
- Identificadores **UUID** en todas las tablas.
- Dinero en `numeric(10,2)` y `BigDecimal` en Java, **nunca `double`**. Siempre euros, importe total sin desglose de IVA.
- Enumerados guardados como texto (`@Enumerated(EnumType.STRING)`), con `CHECK` en la base.
- Fechas con zona horaria (`timestamptz`).
- La tabla de usuarios es **`app_user`** porque `user` es palabra reservada en PostgreSQL.
- **Nunca se guardan contraseñas de servidores** en la app.

**Multiusuario y visibilidad**
- Jerarquía: `workspace` > usuarios (`ADMIN` / `MEMBER`) y proyectos. Un usuario pertenece a **un único workspace**.
- Acceso por proyecto mediante `project_member` con rol `OWNER` / `EDITOR` / `VIEWER`.
- `ADMIN`: ve todo el workspace, incluidos contratos, costes, ingresos y tarifas.
- `MEMBER`: ve solo los proyectos donde es miembro y, de ellos, los servidores vinculados (`project_server`), sus dominios y sus comprobaciones de estado. **Nunca** importes, contratos ni finanzas.
- Servidores, dominios y contratos son recursos del workspace gestionados por admins.
- Finanzas: solo visibles para `ADMIN` por ahora.

**Costes**
- El coste recurrente de un proyecto se **calcula** a partir de los contratos de sus servidores, repartido según `project_server.cost_share_percent`. No se registra un `movement` por cada pago.
- `movement` es para ingresos y gastos puntuales o reales fuera de contrato.
- Categoría de gasto = tipo de contrato (`SERVER` / `DOMAIN` / `OTHER`). Periodos de facturación: solo `MONTHLY` y `YEARLY`.
- Valoración del tiempo: `app_user.hourly_rate` es la tarifa por defecto y se **copia** a `time_entry.hourly_rate` al iniciar el cronómetro, para que cambiar la tarifa no reescriba el histórico.
- Los subdominios no tienen contrato propio: heredan del dominio padre (la base lo impone con un `CHECK`).

**Cronómetro**: vive en el servidor; un índice único parcial garantiza un solo cronómetro en marcha por usuario. La duración se calcula, no se guarda.

**Monitorización y avisos**
- Estado automático desde el principio, con tareas programadas (`@Scheduled`): comprobación cada **5 minutos** (`interval_seconds = 300`), alerta tras **3 fallos consecutivos**. Tipos: `HTTP`, `SSL`, `PING`, `TCP_PORT`. El vencimiento de dominio se introduce a mano.
- Avisos de contratos a **30, 15 y 7 días** del `renewal_date`, umbrales **globales** de la aplicación.
- Los avisos viven en la tabla `notification` (panel en la app); el push (Web Push con claves VAPID) es solo otra forma de entregar esa misma fila. Cada aviso lleva una `dedupe_key` única, por ejemplo `contract:{id}:30d:{renewal_date}`, para que la tarea no lo repita.
- Contratos y costes avisan solo a `ADMIN`. Caídas y SSL avisan al `ADMIN` y a los miembros de los proyectos afectados.
- Los `check_result` se purgan a los **30 días** con una tarea programada.
- Vigilancia de la propia app: la app en IONOS vigila Contabo. Para detectar la caída de IONOS, un **latido inverso** desde Contabo (`cron` + `curl` a `/actuator/health`) avisa por **Telegram** tras varios fallos seguidos.

**Recursos del servidor (1 vCore, 2 GB)**: limitar la JVM (~`-Xmx512m`), añadir 1-2 GB de swap, comprobaciones ligeras y espaciadas, **no compilar el frontend en el servidor**. Reparto de RAM estimado: Spring Boot 400-600 MB, PostgreSQL 150-300 MB, Nginx 20-50 MB.

El detalle de las 14 tablas está en `docs/modelo-de-datos.md` (con diagrama Mermaid). Es la referencia: si algo cambia, actualiza ese documento en el mismo PR.

## 4. Estado actual del repositorio

Repo privado `T0M4SNM/control-panel`, monorepo.

```
backend/   Spring Boot (pom.xml directamente aquí)
frontend/  vacío (.gitkeep)
deploy/    vacío (.gitkeep)
docs/      modelo-de-datos.md
.github/   plantillas de PR y de issues (bug, feature); workflows/ vacío
CLAUDE.md  este archivo
```

**Pull requests**
- Fusionados: #1 plantilla de PR, #2 plantilla de issue de bugs, #3 plantilla de issue de funcionalidades, #4 modelo de datos, #5 proyecto Spring Boot inicial.
- Abierto: **#6** `feat/esquema-base-de-datos`, con las migraciones Flyway `V1` a `V5` (14 tablas). Validadas contra PostgreSQL 16 real con pruebas de restricciones. **Al empezar, comprueba si ya está fusionado.**

**Backend hoy**
- Paquete raíz: `io.github.t0m4snm.control_panel_backend` (con guion bajo; es lo que generó Initializr). Solo existe la clase `ControlPanelBackendApplication` y su test de contexto.
- Dependencias: Actuator, Data JPA, Flyway, Security, Validation, Web MVC (starters de Boot 4: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`), PostgreSQL, Docker Compose support, y los `*-test` correspondientes.
- `backend/compose.yaml`: PostgreSQL 17 con credenciales **solo de desarrollo** (`myuser` / `secret` / `mydatabase`), puerto aleatorio. Spring Boot lo arranca solo gracias a `spring-boot-docker-compose`.
- Arranca correctamente en el PC de Tomás (`.\mvnw spring-boot:run` en `backend/`), Tomcat en el 8080.
- Spring Security está con la configuración por defecto: todos los endpoints piden login con el usuario `user` y una contraseña generada en consola. Aún no hay autenticación propia.
- Actuator expone un solo endpoint (`health`).
- `src/main/resources/application.properties` solo tiene `spring.application.name`.
- Aún no hay entidades JPA, repositorios, controladores, tests de migraciones ni CI. El frontend no existe.

## 5. Reglas del repositorio

- `main` siempre estable. La protección de rama **no se puede activar** (cuenta gratuita con repo privado): la disciplina es no hacer commits directos a `main`, todo entra por PR.
- Ramas cortas por tarea: `feat/...`, `fix/...`, `docs/...`, `chore/...`. Cada rama sale de un `main` actualizado.
- Mensajes de commit y título de PR en **Conventional Commits** (`feat:`, `fix:`, `docs:`, `chore:`, con ámbito opcional, p. ej. `feat(backend):`).
- Descripción del PR siguiendo `.github/pull_request_template.md` (Qué cambia, Por qué, Cómo probarlo, Lista de comprobación).
- **Nunca secretos en el repo.** Variables de entorno, con `.env.example` versionado y `.env` ignorado.
- **Una migración Flyway ya fusionada no se edita nunca**: se crea otra (`V6`, `V7`...). Nombres `V<n>__descripcion_con_guiones_bajos.sql`.
- Si cambias el modelo, actualiza `docs/modelo-de-datos.md` en el mismo PR.

## 6. Entorno de Tomás y problemas ya resueltos

- Windows 11, PowerShell, VS Code. Java 21 (21.0.12), Git 2.55, Docker Desktop 29, Node 24. Repo local en `C:\Users\tfnma\Desktop\desktop\productividad\ControlPanel\control-panel`.
- Docker Desktop no arrancaba (`Virtual Machine Platform not enabled`, luego error DISM 14098 por almacén de componentes). Se resolvió con `DISM /Online /Cleanup-Image /StartComponentCleanup` y `dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart`, y reiniciando. Docker funciona ya.
- Windows no admite `mkdir a b c` ni `mkdir -p` en PowerShell; usar un comando por carpeta.
- `git: 'credential-manager-core' is not a git command` es un aviso inofensivo (arreglo: `git config --global credential.helper manager`).

**Si trabajas desde una sesión en la nube de Claude (no desde el PC de Tomás):**
- El egress bloqueaba `repo.maven.apache.org` (403 por política): no se podía compilar ni testear el Java. Hay que permitir `repo.maven.apache.org` y `repo1.maven.org` en la configuración de red del entorno. Maven además necesita proxy en `~/.m2/settings.xml` (`127.0.0.1` y el puerto de `HTTPS_PROXY`).
- No hay Docker en la nube. Hay un servidor PostgreSQL 16 en `/usr/lib/postgresql/16/bin` (hay que arrancarlo con un usuario no root, p. ej. `claude`, en un directorio accesible por él).
- `gh pr create` falla (usa GraphQL, bloqueado): usar la API REST con `gh api repos/T0M4SNM/control-panel/pulls ...`.
- La app de Claude para GitHub ya está instalada en la cuenta `T0M4SNM` con acceso al repo.
- El clon es superficial: antes de empujar ramas, `git fetch origin main`. Un aviso de "unpushed commits" del hook puede ser un falso positivo si el clon no registra la rama remota.
- La carpeta `.github` no se puede escribir a través de las herramientas del equipo local de Tomás; desde un clon propio con GitHub conectado sí.

## 7. Hoja de ruta

Orden acordado, de menor a mayor riesgo. Cada punto = uno o varios PRs pequeños con sus tests.

1. **Fusionar PR #6** (migraciones). Añadir un test de migraciones con PostgreSQL real (Testcontainers o `embedded-postgres`) que compruebe tablas, restricciones y el índice del cronómetro.
2. **CI en GitHub Actions** (`.github/workflows`): en cada PR, `./mvnw verify` en `backend/` y lint, tipos y build en `frontend/`. El despliegue automático queda para más adelante.
3. **Autenticación y usuarios**: login por email y contraseña (hash con BCrypt o Argon2), roles `ADMIN` / `MEMBER`, creación del primer admin y del workspace (seed o comando de arranque). Decisión pendiente: sesión con cookie (+ CSRF) o JWT. Recomendación: **sesión con cookie**, porque SPA y API irán bajo el mismo origen detrás de Nginx y es más simple y más seguro.
4. **Proyectos y miembros**: primer CRUD completo con la regla de visibilidad por proyecto (admin ve todo; member solo lo suyo), con tests de autorización.
5. **Frontend mínimo**: Vite + React + Tailwind + TypeScript, pantalla de login y lista de proyectos, ya como PWA (manifest + service worker).
6. **Servidores, dominios, contratos y reparto de costes** (`cost_share_percent`), con resumen de coste mensual por proyecto y total.
7. **Movimientos (ingresos y gastos) y resúmenes** mensual/anual y beneficio por proyecto; solo admin.
8. **Cronómetro de tiempo** con valoración (`hourly_rate`) y coste real de cada proyecto.
9. **Monitorización**: ejecutor de `health_check` con `@Scheduled`, histórico, purga a 30 días, estado actual.
10. **Avisos**: generación idempotente (`dedupe_key`), panel en la app, **Web Push** (VAPID, `push_subscription`; en iOS solo funciona con la PWA instalada, iOS 16.4 o superior).
11. **Dashboard**: próximos vencimientos, coste mensual total, estado general.
12. **Despliegue** en IONOS: `deploy/` con Docker Compose (Postgres 17, backend, Nginx), HTTPS, JVM limitada, swap, y el **latido inverso** desde Contabo con aviso por Telegram.

## 8. Decisiones menores que aún no se han tomado

Decídelas tú con criterio razonable y déjalas anotadas en el PR correspondiente:
- Sesión con cookie o JWT (ver punto 3 de la hoja de ruta; recomendado: cookie).
- Dominio y HTTPS definitivos de la app, y certificados (Let's Encrypt).
- Cómo se crea el primer usuario administrador.
- Bot de Telegram para el latido inverso (hay que crearlo con BotFather; Tomás aportará el token como variable de entorno, nunca en el repo).
