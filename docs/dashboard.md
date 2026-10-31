# Dashboard

La pantalla de inicio. `GET /api/dashboard` junta en una sola petición todo lo que necesita.

Reutiliza los servicios de cada sección, así que aplica sus mismas reglas: un miembro solo ve sus proyectos y sus comprobaciones, y **nunca** dinero. Las secciones con importes ni siquiera aparecen en su JSON.

| Campo | Quién | Contenido |
|---|---|---|
| `projects` | Todos | `{ total, byStatus: { IDEA, DEVELOPMENT, PRODUCTION, PAUSED } }` de los proyectos visibles |
| `checks` | Todos | Comprobaciones **activas** visibles: `{ total, byStatus: { UP, DOWN, UNKNOWN }, down: [{ id, name, target, since }] }` |
| `sslExpiring` | Todos | Certificados que caducan en 30 días o menos: `[{ checkId, name, target, expiresAt, daysLeft }]` |
| `unreadNotifications` | Todos | Avisos sin leer |
| `runningTimer` | Todos | Su cronómetro en marcha, o ausente |
| `renewals` | Admin | Contratos activos que vencen en los próximos 60 días, o que vencieron hace menos de 30 y siguen activos (`daysLeft` negativo): `[{ id, name, provider, type, renewalDate, daysLeft, amount, billingPeriod, autoRenew }]` |
| `costs` | Admin | `{ totalMonthly, totalYearly }` de los contratos activos |
| `thisMonth` | Admin | Mes en curso del resumen económico: `{ income, expenses, contractCosts, timeCosts, profit, realProfit }` |

Los recuentos por estado incluyen siempre todos los valores, con 0 si no hay ninguno, para que el frontend no tenga que comprobar si existen.
