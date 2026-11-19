# Seluna

Beta Android de Tecnologías Móviles, desarrollada por **Luisa Fernanda Velandia Salamanca**. Incluye seguimiento de ciclo, calendario, registro diario, estadísticas de síntomas, exportación de resumen PDF y un asistente educativo opcional.

- [Plan y criterios de aceptación](docs/plan.md)
- [Presentación con guion](docs/presentacion.html) · Abre el archivo en el navegador, usa `←` y `→` para avanzar o retroceder, `N` para mostrar el guion y `Esc` para cerrarlo.
- [Mockups de Seluna en Figma](https://www.figma.com/design/NSu4CMDTwWvWka0ClNQGJ2/ProyectoMov?node-id=0-1&p=f)

## Ejecutar en Android Studio

Abre esta carpeta como proyecto, usa JDK 17 y Android SDK 35, selecciona la variante `debug` y ejecuta en un teléfono o emulador con Android 8.0 o superior. Desde PowerShell también puedes compilar y ejecutar el análisis estático.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. El seguimiento y las cuentas locales funcionan en el dispositivo. Para la demostración del asistente, usa el emulador Android estándar. El APK de depuración apunta a `10.0.2.2:8787`, la dirección con la que el emulador llega al equipo anfitrión.

## Probar el asistente

Con Node.js 22 o superior, inicia el servidor de demostración desde la raíz del proyecto. Devuelve una respuesta identificada como demostración local.

```powershell
node server/server.mjs --demo
```

Para usar el modelo real, detén el servidor de demostración, copia `server/.env.example` a `server/.env` si el archivo todavía no existe y escribe tu clave en `BAI_API_KEY`. Luego verifica el acceso al modelo e inicia el servidor.

```powershell
node server/server.mjs --check-model
node server/server.mjs
```

El servidor lee la clave exclusivamente de `server/.env`, excluido de Git, y usa `https://api.b.ai/v1` con el identificador `qwen3.8-flash`. Al pulsar **Enviar**, viajan el mensaje y hasta ocho mensajes recientes de ese chat. El perfil y el diario se conservan en SQLite. El servidor de referencia escucha solo en el equipo anfitrión. El chat en un teléfono físico requiere desplegar un servicio HTTPS con control de acceso, que queda fuera de esta beta. [B.AI · Qwen3.8 Flash](https://docs.b.ai/llmservice/models/qwen3-8-flash/)

## Recorrido para exponer

1. En la app, crea una cuenta local ficticia y selecciona un inicio de menstruación reciente.
2. Muestra el anillo de Hoy y la fecha estimada. Abre el calendario y selecciona un día.
3. Guarda un registro con flujo, ánimo, síntomas e intensidad. Revisa los recuentos y la exportación PDF en Estadísticas.
4. Abre el chat y pregunta «¿Cómo se cuenta el día 1 del ciclo?». Identifica el modo que estás usando.

## Publicar desde una cuenta aislada

Desde una terminal del proyecto, ejecuta `.\scripts\Publicar-Seluna.ps1`. Abre el enlace de GitHub en una ventana de incógnito y autoriza el código temporal con `123LUISAV`. El script comprueba el usuario, verifica o crea `123LUISAV/seluna` como repositorio privado y confirma el commit de `main` después del push. Guarda la sesión en un directorio temporal propio y retira sus credenciales locales al finalizar. [GitHub CLI · Acceso por código](https://cli.github.com/manual/gh_auth_login)
