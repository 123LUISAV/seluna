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
| Cuenta y perfil | Entrar o registrarse por correo en Firebase. Configurar el ciclo y el objetivo en dos pasos opcionales y elegir o quitar la foto del perfil. |
| Hoy | Consultar el día desde el último inicio real y ver la próxima fecha estimada cuando está disponible. |
| Calendario | Cambiar el mes, seleccionar una fecha y registrar o quitar un inicio de menstruación. |
| Registro | Guardar, consultar, editar y eliminar registros por fecha. |
| Estadísticas | Consultar recuentos, abrir guías de bienestar y exportar un PDF con resumen o detalle diario. |
| Asistente | Resolver preguntas de la guía sin conexión y enviar otras consultas a Qwen con contexto limitado. |
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
| Problemática y valor agregado | Diario local, estimación explicable y una conversación educativa opcional en el mismo flujo. |
| Persistencia | SQLite conserva preferencias, foto, inicios y registros por perfil al cerrar la aplicación. Los perfiles ficticios cubren ciclo con historial, ciclos variables y primer uso. |
| Modelos orientados a objetos | `UserProfile`, `CycleSettings`, `DailyEntry` y `CycleEstimate` encapsulan propiedades. `SelunaStore` gestiona las consultas y relaciones. |
| Listados | El calendario y las listas de diario, estadísticas y chat usan `RecyclerView`, con acciones por elemento. |
| Formularios | El perfil del ciclo y el diario validan los valores antes de persistirlos. |

## Cierre de la entrega

1. **Firebase verificado.** Las reglas de `firestore.rules` están activas en `seluna-app`. La prueba manual guardó seis registros ficticios y tres inicios, recuperó un registro eliminado y verificó que se rechaza una lectura anónima. La prueba posterior guardó un registro automáticamente y lo recuperó al entrar en otro emulador con la misma cuenta.
2. **Diseño.** Comparar la beta actual con Figma en un teléfono pequeño y otro ancho. Priorizar la jerarquía de Hoy, la leyenda del calendario, la legibilidad de los formularios y las tarjetas de bienestar.
3. **Exposición.** Ensayar con la cuenta de muestra. Mostrar una fecha real y una estimada, el registro, los dos modos del informe PDF, una respuesta de la guía local y la copia ficticia de Firestore.

## Diferencias pendientes frente a Figma

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
