# Despliegue

El panel vive en el VPS de Contabo, junto a otras apps. Así está organizado el servidor:

```
/opt/stacks/
├── proxy/     Caddy compartido: único que escucha en 80/443, saca los certificados HTTPS
│   └── sites/ un archivo por app (panel.caddy, y los de tus otras apps)
├── panel/     el panel: PostgreSQL + backend + web
└── scripts/   preparar el servidor y copias de seguridad
```

- Las imágenes del panel las construye GitHub Actions en cada cambio de `main` (`.github/workflows/release.yml`) y las publica en `ghcr.io/t0m4snm/`. El servidor solo las descarga: nunca compila.
- Cada app es una pila de Docker Compose independiente, conectada al proxy por la red `proxy`.

Todos los comandos de tu PC son para **PowerShell**. Los del servidor se ejecutan dentro de la sesión SSH.

## Antes de empezar

- El VPS de Contabo con **Ubuntu 24.04**, su IP y la contraseña de root que te da Contabo.
- Acceso al panel de DNS de Piensa Solutions para `t0masdev.es`.
- Tu repo local de `control-panel` actualizado a `main` (`git pull`).

En los comandos, cambia `IP` por la IP del VPS.

## 1. DNS

En Piensa Solutions, crea un registro **A**:

| Nombre | Tipo | Valor |
|---|---|---|
| `panel` | A | la IP del VPS |

Comprueba desde tu PC que ya apunta. Puede tardar unos minutos:

```powershell
Resolve-DnsName panel.t0masdev.es
```

## 2. Entrar por SSH con una clave

Es más seguro que la contraseña, y luego no tendrás que escribirla. Si ya tienes una clave en `~\.ssh\id_ed25519.pub`, sáltate el primer comando.

```powershell
ssh-keygen -t ed25519
```

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@IP "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

Te pedirá la contraseña de root una última vez. A partir de aquí, `ssh root@IP` entra sin contraseña.

## 3. Subir los archivos y preparar el servidor

Desde la carpeta del repo en tu PC:

```powershell
ssh root@IP "mkdir -p /opt/stacks"
```

```powershell
scp -r deploy\proxy deploy\panel deploy\scripts root@IP:/opt/stacks/
```

```powershell
ssh root@IP "bash /opt/stacks/scripts/setup-server.sh"
```

El script tarda unos minutos y hace lo siguiente:

- actualiza el sistema y activa las actualizaciones de seguridad automáticas;
- crea 2 GB de swap;
- activa el cortafuegos, abriendo solo los puertos 22, 80 y 443;
- instala fail2ban, que bloquea a quien prueba contraseñas por SSH;
- instala Docker.

## 4. Configuración (`.env`)

Primero, genera en tu PC las claves de Web Push:

```powershell
npx web-push generate-vapid-keys
```

Después, entra en el servidor:

```powershell
ssh root@IP
```

Y ya dentro, prepara los dos `.env`:

```bash
cd /opt/stacks/proxy && cp .env.example .env && nano .env
```

En el del proxy solo va `ACME_EMAIL`: tu email, al que Let's Encrypt avisa si hay problemas con un certificado.

```bash
cd /opt/stacks/panel && cp .env.example .env && openssl rand -hex 24
```

```bash
nano .env
```

| Variable | Valor |
|---|---|
| `DB_PASSWORD` | la cadena que ha generado `openssl` |
| `APP_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | tu usuario de administrador (contraseña de 12 caracteres o más) |
| `APP_PUSH_VAPID_PUBLIC_KEY` / `_PRIVATE_KEY` | las claves de `web-push` |
| `APP_PUSH_SUBJECT` | `mailto:tu@email.com` |

En `nano`, guardas con `Ctrl+O` y `Enter`, y sales con `Ctrl+X`. Después protege los archivos:

```bash
chmod 600 /opt/stacks/*/.env
```

## 5. Permiso para descargar las imágenes

Las imágenes son privadas, como el repo. El servidor necesita un token de solo lectura:

1. En GitHub, ve a **Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token (classic)**.
2. Márcale solo el permiso `read:packages` y una caducidad larga.
3. En el servidor, inicia sesión en el registro de imágenes. Cuando pida la contraseña, pega el token:

```bash
docker login ghcr.io -u T0M4SNM
```

## 6. Arrancar

```bash
cd /opt/stacks/proxy && docker compose up -d
```

```bash
cd /opt/stacks/panel && docker compose pull && docker compose up -d
```

Para ver el arranque del backend (sales con `Ctrl+C`):

```bash
docker compose logs -f backend
```

Debe aparecer `Creado el primer administrador ...`. Abre **https://panel.t0masdev.es** y entra. El certificado HTTPS lo saca Caddy en la primera visita, y tarda unos segundos.

Una vez dentro, borra `APP_BOOTSTRAP_ADMIN_PASSWORD` del `.env`: ya no hace falta.

## 7. Copias de seguridad diarias

```bash
chmod +x /opt/stacks/scripts/*.sh && crontab -e
```

Añade esta línea al final. Cada día a las 4:15 guarda un volcado de la base de datos en `/var/backups/panel` y conserva 14 días.

```
15 4 * * * /opt/stacks/scripts/backup.sh >> /var/log/panel-backup.log 2>&1
```

Pruébalo una vez a mano con `/opt/stacks/scripts/backup.sh`. De vez en cuando, bájate una copia a tu PC: si el VPS desaparece, se van con él.

```powershell
scp root@IP:/var/backups/panel/*.sql.gz .
```

## 8. Vigilante externo (Healthchecks.io)

El panel vigila tus servidores, pero si cae el propio Contabo, no puede avisarte. Para eso está [Healthchecks.io](https://healthchecks.io). Es gratis, y su integración con Telegram ya trae el bot, así que no hace falta crear uno.

1. Crea una cuenta y un **check** con *Period* de 5 minutos y *Grace* de 10.
2. En **Integrations**, añade **Telegram** y sigue sus pasos.
3. Copia la URL de ping del check (`https://hc-ping.com/...`). En el servidor, `crontab -e` y añade:

```
*/5 * * * * curl -fsS -m 20 https://panel.t0masdev.es/actuator/health > /dev/null && curl -fsS -m 10 https://hc-ping.com/TU-UUID > /dev/null
```

Cada 5 minutos, el servidor comprueba el panel por su URL pública, recorriendo DNS, HTTPS, proxy y backend. Si responde, avisa a Healthchecks. Si se cae el panel o el servidor entero, los avisos dejan de llegar y Healthchecks te escribe por Telegram.

## 9. Logs del servidor (Vector)

Para ver en el panel los logs de un servidor (**Logs** en el menú), instala Vector en él. Sirve para el Contabo y para cualquier otro servidor que vigiles, también el de IONOS.

1. En el panel, **Logs** → elige el servidor → **Conectar servidor** → **Crear clave**. Cópiala: solo se ve una vez.
2. Sube la plantilla desde tu PC:

   ```powershell
   scp -r deploy\vector root@IP:/opt/stacks/
   ```

3. En el servidor, crea el `.env`:

   ```bash
   cd /opt/stacks/vector && cp .env.example .env && nano .env
   ```

   Pon `CONTROL_PANEL_URL=https://panel.t0masdev.es` y la clave en `CONTROL_PANEL_KEY`.

4. Arranca:

   ```bash
   docker compose up -d
   ```

En unos segundos aparecen las líneas en el panel. Si no, mira qué dice Vector:

```bash
docker compose logs --tail 50
```

Vector necesita Docker. Si en un servidor tu app no corre en Docker (por ejemplo, con `pm2`), sus logs no salen en la salida de los contenedores: añade su archivo de log a `include` en `vector.yaml` (montando su carpeta en `compose.yaml`).

## Actualizar y volver atrás

Cada cambio en `main` publica imágenes nuevas. Para ponerlas en marcha:

```bash
cd /opt/stacks/panel && docker compose pull && docker compose up -d
```

Si una versión da problemas, vuelve a la anterior fijando en el `.env` el hash del commit que funcionaba. Lo tienes en la pestaña Actions o con `git log` en tu PC.

```bash
IMAGE_TAG=0123abc...
```

Y después:

```bash
docker compose up -d
```

Las migraciones de Flyway solo avanzan. Volver a una versión anterior a una migración nueva no la deshace, así que antes de actualizar, ten la copia del día.

## Añadir otra app al servidor

1. Su pila en `/opt/stacks/<app>/`, con su `compose.yaml`. El contenedor que sirve la web va en la red `proxy` y tiene un `container_name` fijo, como en `panel/compose.yaml`.
2. Un archivo `/opt/stacks/proxy/sites/<app>.caddy`:

   ```
   miapp.t0masdev.es {
   	reverse_proxy miapp-web:3000
   }
   ```

3. Su registro A en Piensa Solutions.
4. Recargar Caddy:

   ```bash
   cd /opt/stacks/proxy && docker compose exec caddy caddy reload --config /etc/caddy/Caddyfile
   ```

## Recursos

El VPS tiene 4 vCPU, 8 GB de RAM y 100 GB de disco. Límites de memoria del panel:

| Servicio | Límite |
|---|---|
| PostgreSQL | 768 MB |
| Backend | 1 GB (la JVM usa el 75 %) |
| Web | 64 MB |
| Caddy | 128 MB |
| Vector | 256 MB |

Quedan unos 5,5 GB para tus otras apps. Los logs de cada contenedor rotan a 3 archivos de 10 MB, así que no pueden llenar el disco.
