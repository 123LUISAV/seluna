# Seluna · Plan del proyecto

- **Autora** · Luisa Fernanda Velandia Salamanca · U00179371
- **Asignatura** · Tecnologías Móviles
- **Docente** · Fabian Enrique Suarez Carvajal

## Problema y objetivo

Cuando las fechas, los síntomas y las notas quedan repartidos entre el calendario y otros apuntes, cuesta reconstruir cómo cambió un ciclo. Seluna reúne ese historial y permite consultar preguntas generales sobre bienestar menstrual desde el mismo lugar.

**Qué.** Construir una beta Android para registrar menstruación, síntomas, flujo y estado de ánimo.

**Cómo.** Implementar en Kotlin los recorridos de los mockups, guardar los datos por perfil local en SQLite y presentar una estimación basada en las fechas registradas. El asistente se comunica mediante un servidor local que llama a B.AI con Qwen3.8 Flash.

**Para qué.** Ayudar a la usuaria a recordar fechas, identificar patrones en su propio historial y preparar preguntas informadas sobre su bienestar.

## Alcance de la beta

| Recorrido | Resultado esperado |
| --- | --- |
| Cuenta y perfil | Registrarse con nombre, correo y contraseña. Ver el nombre de perfil, configurar el ciclo y el objetivo en dos pasos opcionales y elegir o quitar la foto. |
| Hoy | Consultar el día desde el último inicio real y ver la próxima fecha estimada cuando está disponible. |
| Calendario | Cambiar el mes, seleccionar una fecha y registrar o quitar un inicio de menstruación. |
| Registro | Guardar, consultar, editar y eliminar registros por fecha. |
| Estadísticas | Consultar recuentos, abrir guías de bienestar y exportar un PDF con resumen o detalle diario. |
| Asistente | Resolver preguntas de la guía sin conexión. Las consultas a Qwen están disponibles al configurar e iniciar el servidor de integración. |
| Copia en Firebase | Activar respaldo automático por equipo, guardar primero en SQLite y recuperar la copia con la misma cuenta en un equipo nuevo. La opción manual sigue disponible. |

El acceso y la recuperación por correo usan Firebase Authentication. El diario funciona en SQLite después de iniciar sesión. La copia automática requiere activación, conexión y una cuenta vinculada. El chat en vivo requiere un servidor con clave y conexión a B.AI. El [diagrama de clases y datos](modelo-datos.md) muestra qué servicio y qué tabla conservan cada dato.

## Metodología

1. **Diseño y aceptación.** Mapear las pantallas del Figma y los cinco criterios de la guía. Definir un recorrido de demostración con datos ficticios.
2. **Seguimiento local.** Implementar el esquema SQLite, el acceso por perfil, los formularios y las pantallas de seguimiento. Verificar los estados vacío, inicial y con historial.
3. **Asistente.** Validar el contrato Android–servidor en modo de demostración. Activar Qwen3.8 Flash con la clave de Luisa y repetir la consulta de prueba cuando esté disponible.
4. **Entrega.** Compilar la beta en Android Studio o Gradle, probar en emulador, preparar la exposición y publicar en el repositorio privado de Luisa tras autenticar su cuenta.

## Correspondencia con la rúbrica

| Criterio | Evidencia para mostrar |
| --- | --- |
| Problemática | Reunir fechas, síntomas y notas en un diario local permite reconstruir el ciclo. La estimación explicable, la guía sin conexión y el PDF compartible amplían ese recorrido. |
| Persistencia | SQLite conserva el diario, los inicios, la configuración y la foto mediante relaciones por perfil. Firebase Authentication administra la identidad y Firestore admite un perfil mínimo y la copia opcional. |
| Clases modelos orientados a objetos | `UserProfile`, `CycleSettings`, `DailyEntry` y `CycleEstimate` encapsulan datos en propiedades inmutables. `SelunaStore` y `SelunaCloud` gestionan su almacenamiento. |
| Listados | El calendario, el diario, las estadísticas, las guías y el chat presentan sus colecciones con `RecyclerView`. Cada dato permite una acción, como seleccionar un día, editar un registro o abrir el detalle del recuento. |
| Formularios | La configuración de ciclo y el diario comprueban fechas, rangos, campos obligatorios y longitudes antes de guardar. El registro de cuenta valida nombre, correo y contraseña. |

La guía exige problemática, persistencia y clases modelo. Seluna también cubre listados y formularios con funciones del seguimiento del ciclo. El selector de foto complementa el perfil. El [informe de pruebas](pruebas-funcionales.md) reúne la evidencia por requisito.

## Cierre de la entrega

1. **Firebase.** Las reglas publicadas en `seluna-app` coinciden con las del repositorio y habilitan el documento privado `users/{uid}`. En la prueba manual anterior se recuperó un registro eliminado y se verificó el bloqueo de lectura anónima. En la prueba automática, un registro guardado en un emulador apareció al entrar desde otro.
2. **Diseño.** Mantener la barra inferior con texto legible, la fecha registrada diferenciada de la estimación y el guion de la presentación debajo de la diapositiva.
3. **Exposición.** Preparar una cuenta con datos ficticios. Mostrar el nombre, una fecha real y una estimada, el registro, los dos modos del informe PDF, una respuesta de la guía local y el respaldo de Firestore.

## Ampliaciones de los mockups

| Pantalla | Pendiente |
| --- | --- |
| Acceso y personalización | Google y Apple. El onboarding admite objetivo, regularidad y anticoncepción opcional. El formulario de condiciones ginecológicas y los recordatorios programados siguen pendientes. |
| Hoy | Resumen de la semana y accesos visuales para ánimo, flujo e hidratación. |
| Calendario | Unir visualmente los días de un periodo y añadir el resumen desplegable del próximo mes. |
| Estadísticas | Filtro temporal, tarjetas de duración y variación y barras verticales. Ya funcionan las barras de recuento, la guía «Infusiones y bienestar» y el PDF. |
| Registro | Selector visual de flujo y campo específico para medicación. |

Los mockups incluyen fases, fertilidad y un porcentaje de confianza. El cálculo implementado estima el próximo inicio del periodo con el historial. Incorporar indicadores de fertilidad requeriría definir y validar un cálculo adicional.

## Estimación del próximo inicio

El largo de un ciclo se cuenta entre el primer día de una menstruación y el primer día de la siguiente. Seluna ordena los inicios registrados, calcula los intervalos aprovechables y suma su promedio redondeado al último inicio. Con un solo inicio, suma la duración habitual que configuró la usuaria. Si una fecha esperada ya pasó, puede hacer falta actualizar el último inicio. La duración puede variar y esta estimación no debe usarse como método anticonceptivo. [Office on Women’s Health](https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle)

## Propuesta de negocio

La hoja de ruta contempla una app web para consultar el historial con la misma cuenta y el acceso de pareja mediante una invitación de solo lectura. La usuaria elegiría qué fechas o resúmenes compartir. Los registros privados y las notas quedarían fuera del permiso por defecto. La invitación tendría vencimiento y opción de revocar el acceso.

La versión gratuita conservaría el registro, el calendario y las estadísticas básicas. **Seluna Plus** podría ofrecer una cuota mayor de consultas educativas y resúmenes bajo solicitud. Una prueba de interés mediría frecuencia de registro, consultas completadas, costo medio por consulta y disposición a pagar. El modelo evita condicionar el acceso al historial personal a una suscripción. Los planes y cobros son una propuesta para evaluar después de la beta.

El diferencial de Seluna es cómo combina control local del historial, una estimación que se puede explicar y un chat que solo recibe lo que la usuaria decide compartir en la conversación. Otros productos de la categoría ya ofrecen chat. [Flo, modo anónimo](https://flo.health/anonymous-mode)
