# control-panel

Panel personal para gestionar proyectos, servidores, dominios, contratos,
costes y tiempo, con monitorización automática y avisos de vencimientos.

## Estado

En fase de diseño. Todavía no hay código de aplicación.

## Stack

- **Backend:** Spring Boot (Java 21, Maven)
- **Frontend:** React + Vite + Tailwind + TypeScript (PWA)
- **Base de datos:** PostgreSQL (migraciones con Flyway)
- **Despliegue:** Docker Compose

## Estructura

| Carpeta     | Contenido                                  |
|-------------|--------------------------------------------|
| `backend/`  | API REST con Spring Boot                   |
| `frontend/` | Aplicación web React                       |
| `deploy/`   | Docker Compose, Nginx y scripts de despliegue |
| `docs/`     | Modelo de datos, arquitectura y decisiones |

