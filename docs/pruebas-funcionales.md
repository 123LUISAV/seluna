# Seluna · Pruebas funcionales

Luisa Fernanda Velandia Salamanca · U00179371 · Tecnologías Móviles

## Verificación de la rúbrica

La guía «Proyecto Final · Consideraciones, lineamientos y requerimientos», páginas 1 a 4, pide una beta funcional en Kotlin con un objetivo que responda qué, cómo y para qué, un alcance definido y una metodología. Exige problemática, persistencia y clases modelo. Seluna también implementa listados y formularios. [Objetivo, alcance y metodología](plan.md)

| Requisito | Evidencia en Seluna |
| --- | --- |
| **Problemática** | El diario reúne inicios, flujo, ánimo y síntomas. Calendario, estadísticas y PDF ayudan a reconstruir el ciclo y presentar el historial. El valor diferencial de la propuesta combina control local, estimación explicable y consulta educativa. |
| **Persistencia** | SQLite guarda el perfil, la configuración, las entradas y los inicios. Los registros relacionan su titular por `user_id`. El UID de Firebase vincula cada cuenta con su documento de perfil y, si hay respaldo, con su copia de Firestore. |
| **Clases modelos orientados a objetos** | `UserProfile`, `CycleSettings`, `DailyEntry` y `CycleEstimate` representan los datos. `javap -private` confirmó campos privados y getters públicos para los tres primeros. El modelo usa propiedades inmutables. Las ediciones guardan una instancia nueva tras validar sus valores. `SelunaStore` y `SelunaCloud` gestionan la persistencia. |
| **Listados** | Las colecciones del calendario, diario, estadísticas, guías y chat usan `RecyclerView`. Las acciones incluyen seleccionar fecha, editar o eliminar una entrada, consultar un recuento, abrir un artículo y leer un mensaje. |
| **Formularios** | El registro de cuenta comprueba nombre, correo y contraseña. Los formularios de configuración y diario validan fechas no futuras, rangos de duración e intensidad, entradas obligatorias y límites de texto antes de guardar. |

[Diagrama de clases y datos](modelo-datos.md)

## Evidencia del cierre

| Prueba | Resultado |
| --- | --- |
| Compilación y análisis estático | **Pasó.** Se compilaron las variantes de depuración y entrega. Pasaron `lintDebug` y `lintVitalRelease`. |
| Firma del APK | **Verificada.** El instalador de clase pasó la verificación de Android en los esquemas v2 y v3. |
| Instalación, registro y acceso | **Pasaron en el APK de entrega.** El emulador instaló y abrió la app. El formulario creó una cuenta ficticia y Authentication devolvió el nombre escrito, distinto del correo. El mismo nombre apareció al cerrar sesión y volver a entrar. |
| Perfil en producción | **Pasó en `seluna-app`.** El UID titular leyó su documento con `displayName` y `updatedAt`. La lectura anónima fue bloqueada. El perfil existía aunque la cuenta todavía carecía de copia del diario. |
| Reglas de acceso a Firestore | **Pasaron 14 comprobaciones HTTP en el emulador.** Las reglas permiten el acceso del UID titular y rechazan accesos de terceros, anónimos y documentos fuera del esquema. El despliegue se leyó de vuelta y coincide con el archivo del repositorio. [Prueba de reglas](../scripts/verificar-reglas.mjs) |
| Calendario, formulario y persistencia | **Pasaron en la compilación de depuración del cierre.** Se marcó y retiró un inicio real mediante la cuadrícula. El diario rechazó intensidad 9, aceptó intensidad 2 y guardó un registro con su nota ficticia. Se volvió a abrir Seluna y el registro se conservó. |
| Listado de Estadísticas | **Pasó en la compilación de depuración del cierre.** Tocar la fila de cólicos abrió el detalle del recuento. |
| Guía educativa en Android | **Pasó en la compilación de depuración del cierre.** La pregunta sugerida sobre el primer día del ciclo mostró la respuesta de la guía integrada. |
| Contrato HTTP del chat | **Pasó en local.** En modo `--demo` respondió a `/health`, devolvió una respuesta de la guía y generó una respuesta de demostración a una consulta distinta. Rechazó JSON malformado, consultas vacías o largas y rutas desconocidas. |
| Exportación PDF | **Pasaron ambos modos en la compilación de depuración del cierre.** Se exportaron, reabrieron y renderizaron los dos PDF. El modo detalle contiene la fecha del nuevo registro y la tabla diaria. Ambos omiten la nota ficticia. Los archivos se guardaron como `entrega/Seluna-QA-resumen-final.pdf` y `entrega/Seluna-QA-detalle-final.pdf`. |
| Copia manual | **Verificada en una beta anterior en `seluna-app`.** Guardó seis registros y tres inicios, recuperó un registro seleccionado y bloqueó una lectura anónima. [Captura de Firestore](capturas/firebase-firestore.png) |
| Recuperación automática | **Verificada en una beta anterior.** Un registro guardado en un emulador apareció al entrar con la misma cuenta en otro. |

## APK para clase

El archivo `entrega/Seluna-Entrega.apk` usa la variante `release` con el certificado de pruebas de Android. El acceso usa una cuenta de Firebase de `seluna-app`. El servidor de demostración se prueba localmente. La guía educativa está incluida en el APK.

Huella SHA-256 del APK de entrega

`5725EBA3D17F8D3F1F211DF988206C8256B6C497BB209526A129746FFECA2FB6`

## Recorrido de comprobación

1. Instalar el APK, abrir Seluna y crear una cuenta ficticia con un nombre de prueba que sea distinto del correo. Revisar el saludo y el nombre en **Mi perfil y ciclo**. Abrir **Authentication → Usuarios** para ver el UID y **Firestore → Datos → users → UID** para comprobar el `displayName`.
2. Completar el onboarding. Introducir una duración fuera de rango y comprobar el mensaje. Corregirla, seleccionar un objetivo y guardar el perfil.
3. En **Calendario**, seleccionar un día, guardar un inicio y confirmar la diferencia entre el inicio registrado y una fecha estimada. En **Registro**, probar una intensidad inválida, guardar un registro correcto y editarlo. Reabrir la app y comprobar la persistencia en el diario.
4. Abrir **Estadísticas**. Pulsar un recuento para ver su detalle. Exportar el resumen y el informe con tabla diaria, y abrir cada archivo para comprobar el contenido.
5. Abrir **Chat** y pulsar una pregunta sugerida. Activar la copia del diario desde **Copia en Firebase** y recuperar el registro desde otro emulador o teléfono con la misma cuenta.
