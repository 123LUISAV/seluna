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
| Cuenta y perfil | Crear sesión local, indicar el último inicio y ajustar la duración habitual del ciclo y del sangrado. |
| Hoy | Consultar el día desde el último inicio real y ver la próxima fecha estimada cuando está disponible. |
| Calendario | Cambiar el mes, seleccionar una fecha y registrar o quitar un inicio de menstruación. |
| Registro | Guardar, consultar, editar y eliminar registros por fecha. |
| Estadísticas | Ver duración del ciclo, recuentos e indicadores derivados del historial y exportar un resumen en PDF. |
| Asistente | Enviar preguntas escritas por decisión de la usuaria. La conversación se mantiene en memoria mientras se usa la app. |

La beta usa una cuenta en el dispositivo. Sincronización, inicio de sesión con Google o Apple, suscripciones y recomendaciones médicas individualizadas quedan fuera del alcance académico. El seguimiento funciona sin red. El chat en vivo requiere un servidor con clave y conexión a B.AI.

## Metodología

1. **Diseño y aceptación.** Mapear las pantallas del Figma y los cinco criterios de la guía. Definir un recorrido de demostración con datos ficticios.
2. **Seguimiento local.** Implementar el esquema SQLite, el acceso por perfil, los formularios y las pantallas de seguimiento. Verificar los estados vacío, inicial y con historial.
3. **Asistente.** Validar el contrato Android–servidor en modo de demostración. Activar Qwen3.8 Flash con la clave de Luisa y repetir la consulta de prueba cuando esté disponible.
4. **Entrega.** Compilar la beta en Android Studio o Gradle, probar en emulador, preparar la exposición y publicar en el repositorio privado de Luisa tras autenticar su cuenta.

## Correspondencia con la rúbrica

| Criterio | Evidencia para mostrar |
| --- | --- |
| Problemática y valor agregado | Diario local, estimación explicable y una conversación educativa opcional en el mismo flujo. |
| Persistencia | SQLite conserva preferencias, inicios y registros por perfil al cerrar la aplicación. |
| Modelos orientados a objetos | `UserProfile`, `CycleSettings`, `DailyEntry` y `CycleEstimate` encapsulan propiedades. `SelunaStore` gestiona las consultas y relaciones. |
| Listados | El calendario y las listas de diario, estadísticas y chat usan `RecyclerView`, con acciones por elemento. |
| Formularios | El perfil del ciclo y el diario validan los valores antes de persistirlos. |

## Estimación del próximo inicio

El largo de un ciclo se cuenta entre el primer día de una menstruación y el primer día de la siguiente. Seluna ordena los inicios registrados, calcula los intervalos aprovechables y suma su promedio redondeado al último inicio. Con un solo inicio, suma la duración habitual que configuró la usuaria. Si una fecha esperada ya pasó, puede hacer falta actualizar el último inicio. La duración puede variar y esta estimación no debe usarse como método anticonceptivo. [Office on Women’s Health](https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle)

## Propuesta de negocio

La versión gratuita conservaría el registro, el calendario y las estadísticas básicas. **Seluna Plus** podría ofrecer una cuota mayor de consultas educativas y resúmenes bajo solicitud. Una prueba de interés mediría frecuencia de registro, consultas completadas, costo medio por consulta y disposición a pagar. El modelo evita condicionar el acceso al historial personal a una suscripción. Los planes y cobros son una propuesta para evaluar después de la beta.

El diferencial de Seluna es cómo combina control local del historial, una estimación que se puede explicar y un chat que solo recibe lo que la usuaria decide compartir en la conversación. Otros productos de la categoría ya ofrecen chat. [Flo, modo anónimo](https://flo.health/anonymous-mode)
