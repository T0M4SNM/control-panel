# control-panel

Panel personal para gestionar proyectos, servidores, dominios, contratos,
costes y tiempo, con monitorización automática y avisos de vencimientos.

## Qué hace

- Proyectos con sus miembros, servidores, dominios y contratos, y el coste de cada proyecto.
- Ingresos, gastos, tiempo con cronómetro y beneficio real.
- Comprobaciones automáticas (web, SSL, ping, puertos) y avisos de vencimientos y caídas, en el panel y por push.
- Errores de tus apps agrupados, usuarios activos en tus webs y logs de tus servidores.

## Estado

Funcionalidad completa y probada; pendiente del primer despliegue (guía en [`deploy/README.md`](deploy/README.md)).

## Stack

- **Backend:** Spring Boot (Java 21, Maven)
- **Frontend:** React + Vite + Tailwind + TypeScript (PWA)
- **Base de datos:** PostgreSQL (migraciones con Flyway)
- **Despliegue:** Docker Compose con Caddy; imágenes publicadas por GitHub Actions

## Estructura

| Carpeta     | Contenido                                  |
|-------------|--------------------------------------------|
| `backend/`  | API REST con Spring Boot                   |
| `frontend/` | Aplicación web React                       |
| `clients/`  | Cliente de errores para tus apps Node.js   |
| `deploy/`   | Docker Compose, Caddy, Vector y scripts de despliegue |
| `docs/`     | Un documento por funcionalidad y el modelo de datos |

