# Cliente de errores para Node.js

Envía las excepciones de tu app Node.js al Control Panel. Es un solo archivo, sin dependencias, y funciona con Node 18 o superior.

## Instalación en tu app

1. En el panel, abre el proyecto, ve a **Claves de envío → Nueva clave** y copia la clave `cpk_...`. Solo se muestra una vez.
2. Copia `control-panel-errors.js` a tu proyecto, por ejemplo a `src/control-panel-errors.js`.
3. Pon la clave en una variable de entorno de tu app. **Nunca** en el código ni en el repo:

   ```
   CONTROL_PANEL_KEY=cpk_...
   ```

4. Al arrancar la app, lo antes posible:

   ```js
   const panel = require('./control-panel-errors')

   panel.init({
     endpoint: 'https://panel.t0masdev.es',
     key: process.env.CONTROL_PANEL_KEY,
     environment: process.env.NODE_ENV || 'production',
     release: require('../package.json').version,
   })
   ```

5. Si usas **Express**, registra el middleware **después** de tus rutas y antes de tu propio manejador de errores:

   ```js
   app.use(panel.expressErrorHandler())
   ```

6. Para los errores que capturas tú y quieres enviar igualmente:

   ```js
   try {
     await guardarPedido(pedido)
   } catch (err) {
     panel.captureException(err, { pedidoId: pedido.id })
     throw err
   }
   ```

Con ES modules (`import`) también funciona:

```js
import panel from './control-panel-errors.js'
```

## Qué hace

- **Excepciones sin capturar y promesas rechazadas sin `catch`**: las envía y después termina el proceso con código 1, igual que haría Node sin el cliente. Así pm2, systemd o Docker reinician tu app como siempre. Si no quieres este comportamiento, usa `captureUncaught: false`.
- **Envío**: agrupa los errores y los manda cada 2 segundos o al juntar 50. No bloquea tu app ni lanza excepciones; si el panel no responde, solo escribe un aviso en consola.
- **Cola**: si se juntan más de 100 errores pendientes, descarta los nuevos para no llenar la memoria de tu app.
- **Agrupación**: el panel agrupa los errores iguales y te avisa solo cuando aparece uno **nuevo** o **vuelve** uno resuelto (ver `docs/errores.md`).

## Tests

```bash
node --test
```
