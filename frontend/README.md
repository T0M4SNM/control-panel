# Frontend

React + Vite + Tailwind + TypeScript, como PWA instalable. Se compila fuera del servidor y Nginx sirve `dist/` como estático, en el mismo origen que la API.

## Desarrollo

Con el backend arrancado en el puerto 8080 (`.\mvnw spring-boot:run` en `backend/`), en PowerShell desde `frontend/`:

```powershell
npm install
npm run dev
```

Abre `http://localhost:5173`. Vite reenvía `/api` y `/actuator` al backend, así que las cookies de sesión y de CSRF funcionan igual que en producción. Si el backend está en otro puerto: `$env:BACKEND_URL = "http://localhost:8081"` antes de `npm run dev`.

## Scripts

| Script | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo con recarga en caliente |
| `npm run lint` | Lint con oxlint |
| `npm run typecheck` | Comprobación de tipos |
| `npm run build` | Tipos + build de producción en `dist/` (incluye service worker y manifest) |
| `npm run generate-pwa-assets` | Regenera los iconos PNG de la PWA a partir de `public/icon.svg` |

## Estructura

- `src/api/`: cliente HTTP (`client.ts` añade la cabecera CSRF) y funciones por recurso.
- `src/pages/`: pantallas.
- El contrato de la API está en `docs/autenticacion.md` y `docs/proyectos.md`.
