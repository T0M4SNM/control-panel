# Proyectos y miembros

## Quién puede hacer qué

| Acción | `ADMIN` | `OWNER` | `EDITOR` | `VIEWER` | Usuario sin acceso al proyecto |
|---|:-:|:-:|:-:|:-:|:-:|
| Ver el proyecto y sus miembros | ✅ | ✅ | ✅ | ✅ | 404 |
| Editar el proyecto | ✅ | ✅ | ✅ | 403 | 404 |
| Añadir, cambiar o quitar miembros | ✅ | ✅ | 403 | 403 | 404 |
| Crear un proyecto | ✅ | 403 | 403 | 403 | 403 |
| Borrar un proyecto | ✅ | 403 | 403 | 403 | 404 |

- `ADMIN` es el rol del usuario en el workspace; `OWNER`, `EDITOR` y `VIEWER` son roles dentro de un proyecto (`project_member`).
- El admin ve todos los proyectos de **su** workspace, sea o no miembro. Los proyectos de otro workspace responden 404 también para él.
- Un proyecto que el usuario no puede ver responde **404 y no 403**, para no revelar que existe.
- Quien crea un proyecto queda como su `OWNER`.
- Solo se pueden añadir como miembros usuarios activos del mismo workspace.

## API

Todas las rutas requieren sesión (ver `docs/autenticacion.md`), y las que modifican algo requieren además la cabecera `X-XSRF-TOKEN`. Los errores llegan en formato estándar `application/problem+json`.

| Petición | Respuesta |
|---|---|
| `GET /api/projects` | Proyectos visibles, ordenados por nombre |
| `GET /api/projects/{id}` | Un proyecto |
| `POST /api/projects` | `201` con el proyecto creado |
| `PUT /api/projects/{id}` | Proyecto actualizado |
| `DELETE /api/projects/{id}` | `204` |
| `GET /api/projects/{id}/members` | Miembros: `{ userId, displayName, email, role, addedAt }` |
| `PUT /api/projects/{id}/members/{userId}` con `{ "role": "EDITOR" }` | Añade el miembro o cambia su rol |
| `DELETE /api/projects/{id}/members/{userId}` | `204` |

Cuerpo de `POST` y `PUT` de proyecto:

```json
{
  "name": "Obligatorio, máx. 150",
  "description": "opcional",
  "status": "IDEA | DEVELOPMENT | PRODUCTION | PAUSED",
  "repoUrl": "URL opcional",
  "publicUrl": "URL opcional",
  "notes": "opcional"
}
```

`PUT` reemplaza el proyecto entero: los campos opcionales que no se envíen se borran. La excepción es `status`: si no llega, al crear vale `IDEA` y al editar se mantiene el que había.

Cada proyecto de la respuesta incluye `myRole` (el rol del usuario actual, nulo si es un admin que no es miembro) y `permissions: { edit, manageMembers, delete }`, para que el frontend sepa qué botones mostrar.
