# Infraestructura y costes

Contratos, servidores y cómo se reparte su coste entre proyectos. Los dominios se documentarán aquí cuando se añadan.

## Quién ve qué

| Recurso | `ADMIN` | `MEMBER` |
|---|---|---|
| Contratos (`/api/contracts`) | Todo | 403 |
| Resumen de costes (`/api/costs/summary`) | Todo | 403 |
| Servidores | Todos los del workspace, con contrato y costes | Solo los vinculados a sus proyectos, **sin datos económicos** (los campos ni aparecen en el JSON) y solo con los proyectos en los que participa |
| Crear, editar o borrar servidores y repartos | Sí | 403 |

## Cómo se calcula el coste

- Solo cuentan los contratos `ACTIVE`. Un contrato `YEARLY` cuenta como `importe / 12` al mes.
- Un servidor tiene como mucho un contrato. Se vincula a proyectos con `cost_share_percent`: el proyecto carga ese porcentaje del coste mensual del contrato.
- La suma de porcentajes de un servidor **no puede pasar de 100** (409 si se intenta). Lo que falte hasta 100 queda como coste sin asignar.
- `unassignedMonthly` = total − suma de proyectos. Incluye contratos sin servidor (p. ej. dominios por ahora), servidores sin proyecto y repartos que no llegan al 100 %.
- Los cálculos se hacen con 4 decimales y se redondean a céntimos al final (`HALF_UP`). El sin asignar se calcula sobre las cifras ya redondeadas, para que lo que ve el usuario cuadre.

## API

Todas requieren sesión, y las que modifican algo también la cabecera `X-XSRF-TOKEN`.

### Contratos (solo `ADMIN`)

| Petición | Respuesta |
|---|---|
| `GET /api/contracts` | Contratos ordenados por `renewalDate` |
| `GET /api/contracts/{id}` | Un contrato |
| `POST /api/contracts` | `201` |
| `PUT /api/contracts/{id}` | Contrato actualizado |
| `DELETE /api/contracts/{id}` | `204`; sus servidores se quedan sin contrato |

```json
{
  "name": "VPS IONOS", "provider": "IONOS", "type": "SERVER | DOMAIN | OTHER",
  "amount": 12.00, "billingPeriod": "MONTHLY | YEARLY", "renewalDate": "2027-01-15",
  "autoRenew": true, "status": "ACTIVE | CANCELLED", "notes": null
}
```

`autoRenew` (por defecto `true`) y `status` (por defecto `ACTIVE`) son opcionales. La respuesta añade `id` y `monthlyAmount`.

### Servidores

| Petición | Quién | Respuesta |
|---|---|---|
| `GET /api/servers` | Todos | Servidores visibles, por nombre |
| `GET /api/servers/{id}` | Todos | Uno; 404 si no es visible |
| `POST /api/servers` | Admin | `201` |
| `PUT /api/servers/{id}` | Admin | Servidor actualizado |
| `DELETE /api/servers/{id}` | Admin | `204`; borra también sus repartos |
| `GET /api/projects/{id}/servers` | Quien vea el proyecto | Servidores del proyecto |
| `PUT /api/projects/{id}/servers/{serverId}` con `{ "costSharePercent": 60 }` | Admin | Vincula o cambia el porcentaje; 409 si el servidor pasaría de 100 % |
| `DELETE /api/projects/{id}/servers/{serverId}` | Admin | `204` |

Cuerpo de servidor: `name` (obligatorio), `provider`, `ipAddress`, `os`, `cpuCores`, `ramMb`, `diskGb`, `notes`, `contractId`. **Nunca contraseñas.**

Respuesta para el admin:

```json
{
  "id": "…", "name": "IONOS", "provider": "IONOS", "ipAddress": "…", "os": "Ubuntu 24.04",
  "cpuCores": 1, "ramMb": 2048, "diskGb": 60, "notes": null,
  "costs": { "contractId": "…", "contractName": "VPS IONOS", "monthlyAmount": 10.00, "assignedSharePercent": 100.00 },
  "projects": [ { "projectId": "…", "projectName": "A", "costSharePercent": 60.00, "monthlyCost": 6.00 } ]
}
```

Para un miembro, el mismo objeto sin `costs` ni `costSharePercent`/`monthlyCost`.

### Resumen de costes (solo `ADMIN`)

`GET /api/costs/summary`:

```json
{
  "totalMonthly": 16.00, "totalYearly": 192.00,
  "byType": { "SERVER": 15.00, "DOMAIN": 1.00, "OTHER": 0.00 },
  "projects": [ { "projectId": "…", "projectName": "A", "monthlyCost": 6.00 } ],
  "unassignedMonthly": 1.00
}
```
