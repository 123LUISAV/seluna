package com.seluna.app

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var store: SelunaStore
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var page = 0
    private var month = YearMonth.now()
    private var selectedDate = LocalDate.now()
    private val messages = mutableListOf<Pair<String, String>>()
    private var chatDraft = ""
    private val ink = Color.rgb(56, 45, 54)
    private val plum = Color.rgb(116, 85, 99)
    private val pink = Color.rgb(247, 220, 229)
    private val mint = Color.rgb(220, 236, 231)
    private val lavender = Color.rgb(232, 234, 248)
    private val ivory = Color.rgb(252, 245, 245)
    private val fmt = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es"))

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun bg(color: Int, radius: Int = 20): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun text(s: String, size: Int = 16, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row() =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun gap(parent: LinearLayout, h: Int = 12) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(h)))
    }

    private fun card(color: Int = Color.WHITE) =
        column().apply {
            background = bg(color)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

    private fun add(parent: LinearLayout, view: View, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun button(label: String, secondary: Boolean = false, action: () -> Unit): TextView =
        text(label, 15, if (secondary) plum else Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = bg(if (secondary) pink else plum, 16)
            setPadding(dp(12), dp(15), dp(12), dp(15))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun input(
        hint: String,
        value: String = "",
        type: Int = InputType.TYPE_CLASS_TEXT,
    ): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            inputType = type
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(Color.rgb(118, 99, 108))
            background = bg(Color.WHITE, 14)
            setPadding(dp(16), dp(13), dp(16), dp(13))
            minHeight = dp(52)
        }

    private fun title(s: String, sub: String = "") {
        add(body, text(s, 28, ink, true))
        if (sub.isNotEmpty()) add(body, text(sub, 15, plum), 6)
        gap(body, 18)
    }

    private fun label(s: String) = text(s, 14, plum, true)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun parse(s: String): LocalDate? =
        try {
            LocalDate.parse(s)
        } catch (_: Exception) {
            null
        }

    private fun dateText(d: LocalDate) =
        d.format(fmt).replaceFirstChar { it.titlecase(Locale("es")) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SelunaStore(this)
        if (store.currentUser() == null) auth(false) else showPage(0)
    }

    private fun base(withNav: Boolean = false) {
        root = column().apply { setBackgroundColor(ivory) }
        setContentView(root)
        val header = row().apply { setPadding(dp(24), dp(16), dp(24), dp(12)) }
        header.addView(text("✿  Seluna", 24, plum, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (withNav) {
            val chat = text("✧  Chat", 14, plum, true)
            chat.setPadding(dp(10), dp(8), dp(10), dp(8))
            chat.background = bg(pink, 14)
            chat.setOnClickListener { chat() }
            header.addView(chat)
        }
        root.addView(header)
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
            }
        body = column().apply { setPadding(dp(24), dp(8), dp(24), dp(24)) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (withNav) {
            val nav =
                row().apply {
                    setPadding(dp(8), dp(8), dp(8), dp(12))
                    setBackgroundColor(Color.WHITE)
                }
            listOf("☼\nHoy", "▦\nCalendario", "◒\nEstadísticas", "✎\nRegistro").forEachIndexed {
                i,
                s ->
                val item =
                    text(s, 12, if (i == page) plum else Color.rgb(150, 138, 143), i == page)
                        .apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(8), 0, dp(5))
                            contentDescription = s.replace('\n', ' ')
                            setOnClickListener { showPage(i) }
                        }
                nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
            }
            root.addView(nav)
        }
    }

    private fun auth(register: Boolean) {
        base()
        gap(body, 30)
        add(
            body,
            text(if (register) "Tu espacio, a tu ritmo" else "Qué bueno verte", 30, ink, true),
        )
        add(
            body,
            text(
                if (register) "Crea tu cuenta y empieza a conocerte mejor."
                else "Inicia sesión para continuar tu bienestar.",
                16,
                plum,
            ),
            8,
        )
        gap(body, 26)
        val name = input("Tu nombre")
        val email =
            input(
                "Correo electrónico",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
        val pass =
            input(
                "Contraseña",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            )
        if (register) add(body, name)
        add(body, email, 12)
        add(body, pass, 12)
        gap(body, 18)
        add(
            body,
            button(if (register) "Crear cuenta" else "Iniciar sesión") {
                val e = email.text.toString().trim()
                val p = pass.text.toString()
                val n = name.text.toString().trim()
                when {
                    register && n.isEmpty() -> name.error = "Escribe tu nombre"
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() ->
                        email.error = "Escribe un correo válido"
                    p.length < 6 -> pass.error = "Usa al menos 6 caracteres"
                    else -> {
                        val ok = if (register) store.register(n, e, p) else store.login(e, p)
                        if (ok) {
                            if (register) profile(true) else showPage(0)
                        } else
                            toast(
                                if (register)
                                    "No se pudo crear la cuenta. El correo podría estar registrado."
                                else "Correo o contraseña incorrectos"
                            )
                    }
                }
            },
        )
        add(
            body,
            button(if (register) "Ya tengo una cuenta" else "Crear una cuenta", true) {
                auth(!register)
            },
            12,
        )
    }

    private fun showPage(i: Int) {
        page = i
        base(true)
        when (i) {
            0 -> home()
            1 -> calendar()
            2 -> stats()
            else -> diary()
        }
    }

    private fun home() {
        val user = store.currentUser()
        title("Hola, ${user?.name ?: "bienvenida"} ✨", "Un momento para escucharte.")
        val start = store.cycleStarts().lastOrNull()?.let(LocalDate::parse)
        val today = LocalDate.now()
        val days = if (start != null) ChronoUnit.DAYS.between(start, today).toInt() + 1 else 0
        val c = card(pink)
        add(c, text("TU CICLO HOY", 12, plum, true))
        gap(c, 16)
        val ring =
            text(if (days > 0) "Día\n$days" else "✿", 28, plum, true).apply {
                gravity = Gravity.CENTER
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ivory)
                        setStroke(dp(6), plum)
                    }
                setPadding(0, dp(28), 0, dp(28))
            }
        val wrap = row().apply { gravity = Gravity.CENTER }
        wrap.addView(ring, LinearLayout.LayoutParams(dp(145), dp(145)))
        c.addView(wrap)
        gap(c, 16)
        val est = store.estimate()
        val next = est?.nextStart?.let(LocalDate::parse)
        val forecast =
            when {
                next == null -> "Registra un inicio para ver una estimación"
                next.isBefore(today) ->
                    "Fecha estimada anterior: ${dateText(next)}. Registra un nuevo inicio si ya ocurrió."
                else -> "Próximo inicio estimado: ${dateText(next)}"
            }
        add(c, text(forecast, 15, plum, true).apply { gravity = Gravity.CENTER })
        add(body, c)
        add(body, button("Registrar síntomas y notas") { entryForm(LocalDate.now()) }, 18)
        add(body, button("Mi perfil y ciclo", true) { profile(false) }, 10)
        val note = card(mint)
        add(note, text("Un día a la vez", 18, ink, true))
        add(
            note,
            text(
                "Cada ciclo es diferente. Registra cómo te sientes y descubre tus patrones.",
                14,
                plum,
            ),
            8,
        )
        add(body, note, 18)
        add(
            body,
            button("Cerrar sesión", true) {
                store.logout()
                messages.clear()
                chatDraft = ""
                auth(false)
            },
            14,
        )
    }

    private fun profile(first: Boolean) {
        base()
        val s = store.settings()
        title(
            if (first) "Conozcamos tu ciclo" else "Tu perfil de ciclo",
            "Puedes cambiar estos datos cuando quieras.",
        )
        var date = store.cycleStarts().lastOrNull()?.let(LocalDate::parse)
        val dateButton = button(date?.let(::dateText) ?: "Seleccionar fecha", true) {}
        dateButton.setOnClickListener {
            pickDate(date ?: LocalDate.now()) {
                date = it
                dateButton.text = dateText(it)
            }
        }
        add(body, label("Último inicio de menstruación"))
        add(body, dateButton, 8)
        val cycle =
            input(
                "Duración del ciclo (días)",
                s.cycleLength.toString(),
                InputType.TYPE_CLASS_NUMBER,
            )
        val period =
            input(
                "Duración de menstruación (días)",
                s.periodLength.toString(),
                InputType.TYPE_CLASS_NUMBER,
            )
        add(body, label("Duración habitual del ciclo"), 16)
        add(body, cycle, 8)
        add(body, label("Duración habitual de menstruación"), 16)
        add(body, period, 8)
        val goal = input("Ej. conocer mi ciclo", s.goal).withLimit(120)
        val preferences = input("Ej. temas de bienestar", s.preferences).withLimit(200)
        add(body, label("Tu objetivo"), 16)
        add(body, goal, 8)
        add(body, label("Preferencias"), 16)
        add(body, preferences, 8)
        add(
            body,
            button("Guardar perfil") {
                val cl = cycle.text.toString().toIntOrNull()
                val pl = period.text.toString().toIntOrNull()
                val chosenDate = date
                when {
                    chosenDate == null -> toast("Selecciona el último inicio")
                    cl == null || cl !in 15..60 -> cycle.error = "Elige entre 15 y 60 días"
                    pl == null || pl !in 1..15 || pl > cl ->
                        period.error = "Elige entre 1 y 15 días"
                    goal.text.toString().trim().isEmpty() -> goal.error = "Escribe tu objetivo"
                    else -> {
                        store.saveSettings(
                            CycleSettings(
                                chosenDate.toString(),
                                cl,
                                pl,
                                goal.text.toString().trim(),
                                preferences.text.toString().trim(),
                            )
                        )
                        showPage(0)
                    }
                }
            },
            20,
        )
        if (!first) add(body, button("Volver", true) { showPage(0) }, 10)
    }

    private fun pickDate(d: LocalDate, onDate: (LocalDate) -> Unit) {
        DatePickerDialog(
                this,
                { _, y, m, day -> onDate(LocalDate.of(y, m + 1, day)) },
                d.year,
                d.monthValue - 1,
                d.dayOfMonth,
            )
            .apply {
                datePicker.maxDate = System.currentTimeMillis()
                show()
            }
    }

    private fun calendar() {
        title("Tu calendario", "Explora tus días y registra tu ciclo.")
        val controls = row()
        controls.addView(
            button("‹", true) {
                changeMonth(-1)
            },
            LinearLayout.LayoutParams(dp(52), -2),
        )
        controls.addView(
            text(
                    month
                        .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es")))
                        .replaceFirstChar { it.titlecase(Locale("es")) },
                    19,
                    ink,
                    true,
                )
                .apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(0, -2, 1f),
        )
        controls.addView(
            button("›", true) {
                changeMonth(1)
            },
            LinearLayout.LayoutParams(dp(52), -2),
        )
        add(body, controls)
        gap(body, 12)
        val weekdays = row()
        listOf("L", "M", "M", "J", "V", "S", "D").forEach {
            weekdays.addView(
                text(it, 13, plum, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
        }
        add(body, weekdays, 8)
        val offset = month.atDay(1).dayOfWeek.value - 1
        val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        val days =
            (0 until count).map { i ->
                if (i < offset || i >= offset + month.lengthOfMonth()) null
                else month.atDay(i - offset + 1)
            }
        val starts = store.cycleStarts().toSet()
        val entries = store.entries().map { it.date }.toSet()
        val estimatedStart = store.estimate()?.nextStart?.let { parse(it) }
        val estimatedEnd = estimatedStart?.plusDays((store.settings().periodLength - 1).toLong())
        val grid =
            RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, 7)
                isNestedScrollingEnabled = false
                adapter =
                    SimpleAdapter(days) { d ->
                        text(
                                if (d == null) ""
                                else
                                    "${d.dayOfMonth}${if(starts.contains(d.toString())) "\n●" else if(entries.contains(d.toString())) "\n·" else ""}",
                                14,
                                if (d == selectedDate) Color.WHITE else ink,
                                d == selectedDate,
                            )
                            .apply {
                                gravity = Gravity.CENTER
                                minHeight = dp(50)
                                if (d == selectedDate) background = bg(plum, 14)
                                else if (d != null && starts.contains(d.toString()))
                                    background = bg(pink, 14)
                                else if (
                                    d != null &&
                                        estimatedStart != null &&
                                        estimatedEnd != null &&
                                        !d.isBefore(estimatedStart) &&
                                        !d.isAfter(estimatedEnd) &&
                                        d.isAfter(LocalDate.now())
                                )
                                    background = bg(lavender, 14)
                                if (d != null)
                                    setOnClickListener {
                                        selectedDate = d
                                        showPage(1)
                                    }
                            }
                    }
            }
        add(body, grid, 12)
        add(
            body,
            text("● Inicio real   · Registro diario   ▪ Periodo futuro estimado", 12, plum),
            6,
        )
        val detail = card()
        add(detail, text(dateText(selectedDate), 18, ink, true))
        add(
            detail,
            text(
                if (starts.contains(selectedDate.toString())) "Inicio de menstruación registrado"
                else "Selecciona una acción para este día",
                14,
                plum,
            ),
            8,
        )
        add(
            detail,
            button(
                if (starts.contains(selectedDate.toString())) "Quitar inicio registrado"
                else "Registrar inicio real",
                true,
            ) {
                if (selectedDate.isAfter(LocalDate.now()))
                    toast("El inicio real no puede ser futuro")
                else {
                    if (starts.contains(selectedDate.toString()))
                        store.deleteCycleStart(selectedDate.toString())
                    else store.addCycleStart(selectedDate.toString())
                    showPage(1)
                }
            },
            14,
        )
        add(
            detail,
            button(
                if (entries.contains(selectedDate.toString())) "Ver o editar registro"
                else "Registrar este día"
            ) {
                if (selectedDate.isAfter(LocalDate.now())) toast("Elige hoy o una fecha pasada")
                else entryForm(selectedDate)
            },
            10,
        )
        add(body, detail, 20)
    }

    private fun changeMonth(offset: Long) {
        month = month.plusMonths(offset)
        val today = LocalDate.now()
        selectedDate = if (YearMonth.from(today) == month) today else month.atDay(1)
        showPage(1)
    }

    private fun diary() {
        title("Tu diario", "Un espacio para registrar cómo te sientes.")
        add(body, button("+  Nuevo registro") { entryForm(LocalDate.now()) })
        gap(body, 22)
        add(body, text("Tus registros", 20, ink, true))
        gap(body, 12)
        val items = store.entries().sortedByDescending { it.date }
        if (items.isEmpty())
            add(body, text("Todavía no hay registros. Empieza con el de hoy.", 15, plum))
        else
            add(
                body,
                list(items) { entry ->
                    card().apply {
                        add(
                            this,
                            text(
                                parse(entry.date)?.let { dateText(it) } ?: entry.date,
                                17,
                                ink,
                                true,
                            ),
                        )
                        add(
                            this,
                            text("${entry.mood}  ·  Intensidad ${entry.intensity}/5", 14, plum),
                            6,
                        )
                        if (entry.symptoms.isNotBlank()) add(this, text(entry.symptoms, 14, ink), 6)
                        if (entry.note.isNotBlank()) add(this, text(entry.note, 14, ink), 6)
                        val actions = row()
                        actions.addView(
                            button("Editar", true) { entryForm(LocalDate.parse(entry.date)) },
                            LinearLayout.LayoutParams(0, -2, 1f),
                        )
                        val del =
                            button("Eliminar", true) {
                                AlertDialog.Builder(this@MainActivity)
                                    .setMessage("¿Eliminar este registro?")
                                    .setNegativeButton("Cancelar", null)
                                    .setPositiveButton("Eliminar") { _, _ ->
                                        store.deleteEntry(entry.date)
                                        showPage(3)
                                    }
                                    .show()
                            }
                        actions.addView(
                            del,
                            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) },
                        )
                        add(this, actions, 12)
                    }
                },
            )
    }

    private fun entryForm(initial: LocalDate) {
        base()
        val date = initial
        val existing = store.entry(date.toString())
        title(
            if (existing == null) "Nuevo registro" else "Editar registro",
            "Tómate un momento para ti.",
        )
        val dateButton = button(dateText(date), true) {}
        dateButton.setOnClickListener { pickDate(date) { chosen -> entryForm(chosen) } }
        add(body, label("Fecha"))
        add(body, dateButton, 8)
        val parts = existing?.symptoms?.split("\nSíntomas: ", limit = 2)
        val savedFlow = parts?.firstOrNull()?.removePrefix("Flujo: ") ?: ""
        val savedSymptoms = if (parts?.size == 2) parts[1] else existing?.symptoms ?: ""
        val flow = input("Ej. ligero, moderado, abundante o ninguno", savedFlow)
        val mood = input("Ej. tranquila, sensible, cansada", existing?.mood ?: "")
        val symptoms = input("Ej. cólicos, dolor de cabeza", savedSymptoms)
        val intensity =
            input("De 1 a 5", existing?.intensity?.toString() ?: "", InputType.TYPE_CLASS_NUMBER)
        val note =
            input(
                    "Escribe lo que quieras recordar",
                    existing?.note ?: "",
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                )
                .apply {
                    minLines = 3
                    gravity = Gravity.TOP
                }
        flow.withLimit(40)
        mood.withLimit(80)
        symptoms.withLimit(235)
        intensity.withLimit(2)
        note.withLimit(1500)
        add(body, label("Flujo"), 16)
        add(body, flow, 8)
        add(body, choices(listOf("Ninguno", "Ligero", "Moderado", "Abundante"), flow), 8)
        add(body, label("Ánimo"), 16)
        add(body, mood, 8)
        add(body, choices(listOf("Tranquila", "Feliz", "Sensible", "Cansada"), mood), 8)
        add(body, label("Síntomas"), 16)
        add(body, symptoms, 8)
        add(
            body,
            choices(listOf("Cólicos", "Dolor de cabeza", "Hinchazón", "Ninguno"), symptoms, true),
            8,
        )
        add(body, label("Intensidad del flujo o síntomas (1–5)"), 16)
        add(body, intensity, 8)
        add(body, label("Nota"), 16)
        add(body, note, 8)
        add(
            body,
            button("Guardar registro") {
                val n = intensity.text.toString().toIntOrNull()
                when {
                    date.isAfter(LocalDate.now()) -> toast("La fecha no puede estar en el futuro")
                    flow.text.toString().trim().isEmpty() -> flow.error = "Describe tu flujo"
                    mood.text.toString().trim().isEmpty() ->
                        mood.error = "Cuéntanos cómo te sientes"
                    n == null || n !in 1..5 -> intensity.error = "Elige un valor entre 1 y 5"
                    else -> {
                        store.saveEntry(
                            DailyEntry(
                                date.toString(),
                                mood.text.toString().trim(),
                                "Flujo: ${flow.text.toString().trim()}\nSíntomas: ${symptoms.text.toString().trim()}",
                                n,
                                note.text.toString().trim(),
                            )
                        )
                        toast("Registro guardado")
                        showPage(3)
                    }
                }
            },
            20,
        )
        add(body, button("Cancelar", true) { showPage(3) }, 10)
    }

    private fun stats() {
        title("Tus estadísticas", "Una mirada a tus propios registros.")
        val entries = store.entries()
        val starts = store.cycleStarts().mapNotNull { parse(it) }.sorted()
        val est = store.estimate()
        val overview = card(lavender)
        add(overview, text("TU CICLO", 12, plum, true))
        add(
            overview,
            text(
                if (est != null) "${est.averageLength} días" else "Sin datos suficientes",
                24,
                ink,
                true,
            ),
            8,
        )
        add(
            overview,
            text(
                if (est?.basedOnHistory == true) "Promedio basado en tus inicios registrados"
                else "Duración configurada en tu perfil",
                14,
                plum,
            ),
            6,
        )
        add(body, overview)
        val totals = card(mint)
        val recordsLabel = if (entries.size == 1) "registro" else "registros"
        val startsLabel = if (starts.size == 1) "inicio" else "inicios"
        add(totals, text("${entries.size} $recordsLabel  ·  ${starts.size} $startsLabel", 19, ink, true))
        val avg =
            if (entries.isEmpty()) "Aún no hay intensidad registrada"
            else
                "Intensidad media: ${String.format(Locale("es"),"%.1f",entries.map { it.intensity }.average())} / 5"
        add(totals, text(avg, 14, plum), 8)
        add(body, totals, 14)
        val symptoms = symptomCounts(entries)
        if (symptoms.isNotEmpty()) {
            add(body, text("Patrones de síntomas", 20, ink, true), 24)
            add(
                body,
                list(symptoms) { (name, count) ->
                    card().apply {
                        add(
                            this,
                            text("$name · $count de ${entries.size} registros", 16, ink, true),
                        )
                        val bar =
                            ProgressBar(
                                this@MainActivity,
                                null,
                                android.R.attr.progressBarStyleHorizontal,
                            )
                        bar.max = entries.size
                        bar.progress = count
                        add(this, bar, 8)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(name)
                                .setMessage("Aparece en $count de tus ${entries.size} registros.")
                                .setPositiveButton("Cerrar", null)
                                .show()
                        }
                    }
                },
                10,
            )
        }
        add(body, text("Ánimos registrados", 20, ink, true), 24)
        val moods =
            entries.groupingBy { it.mood }.eachCount().entries.sortedByDescending { it.value }
        if (moods.isEmpty())
            add(body, text("Añade registros para ver tus tendencias.", 14, plum), 10)
        else
            add(
                body,
                list(moods) { item ->
                    card().apply {
                        add(
                            this,
                            text(
                                "${item.key}  ·  ${item.value} ${if(item.value==1) "día" else "días"}",
                                16,
                                ink,
                                true,
                            ),
                        )
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle(item.key)
                                .setMessage("Registrado en ${item.value} días.")
                                .setPositiveButton("Cerrar", null)
                                .show()
                        }
                    }
                },
                10,
            )
        add(
            body,
            button("Exportar resumen en PDF") {
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_TITLE, "Seluna-resumen-${LocalDate.now()}.pdf")
                    },
                    EXPORT_REPORT,
                )
            },
            16,
        )
        add(
            body,
            text("El resumen incluye recuentos y estimaciones. Elige dónde guardarlo.", 13, plum),
            8,
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != EXPORT_REPORT || resultCode != RESULT_OK || store.currentUser() == null)
            return
        val target = data?.data ?: return
        val entries = store.entries()
        try {
            val output =
                contentResolver.openOutputStream(target) ?: error("No se pudo abrir el destino")
            output.use {
                CycleReport.write(
                    it,
                    entries.size,
                    store.cycleStarts().size,
                    store.estimate(),
                    symptomCounts(entries),
                )
            }
            toast("Resumen PDF guardado")
        } catch (_: Exception) {
            toast("No se pudo guardar el resumen PDF")
        }
    }

    private fun EditText.withLimit(length: Int) = apply {
        filters = arrayOf(InputFilter.LengthFilter(length))
    }

    private fun chat() {
        base()
        title("Conversemos ✧", "Un espacio para tus preguntas de bienestar.")
        add(
            body,
            text(
                "Al enviar, compartes tu mensaje y hasta ocho mensajes recientes de este chat con B.AI. Tu diario queda en el dispositivo. La respuesta es orientativa. Para una consulta médica, acude a un profesional de salud.",
                14,
                plum,
            ),
        )
        gap(body, 12)
        if (messages.isEmpty()) {
            val welcome = card(lavender)
            add(welcome, text("Hola, soy Seluna. ¿En qué puedo acompañarte hoy?", 15))
            add(body, welcome)
        } else
            add(
                body,
                list(messages) { m ->
                    card(if (m.first == "user") pink else lavender).apply {
                        add(this, text(if (m.first == "user") "Tú" else "Seluna", 13, plum, true))
                        add(this, text(m.second, 15), 6)
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setMessage(m.second)
                                .setPositiveButton("Cerrar", null)
                                .show()
                        }
                    }
                },
            )
        val field =
            input(
                    "Escribe tu mensaje",
                    chatDraft,
                    type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                )
                .withLimit(1_500)
        add(body, field, 16)
        val send =
            button("Enviar") {
                val content = field.text.toString().trim()
                if (content.isNotEmpty()) {
                    chatDraft = ""
                    messages.add("user" to content)
                    chatLoading()
                } else field.error = "Escribe un mensaje"
            }
        add(body, send, 10)
        add(body, button("Volver", true) { showPage(page) }, 10)
    }

    private fun chatLoading() {
        base()
        title("Conversemos ✧", "Seluna está escribiendo…")
        add(body, text("Espera un momento mientras preparamos una respuesta.", 15, plum))
        val snapshot = messages.toList()
        thread {
            try {
                val response = ChatClient.reply(snapshot)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        messages.add("assistant" to response)
                        chat()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        chatDraft = messages.removeAt(messages.lastIndex).second
                        toast(e.message ?: "No fue posible conectar. Inténtalo de nuevo.")
                        chat()
                    }
                }
            }
        }
    }

    private fun choices(
        options: List<String>,
        field: EditText,
        append: Boolean = false,
    ): RecyclerView =
        RecyclerView(this).apply {
            layoutManager =
                LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            isNestedScrollingEnabled = false
            adapter =
                SimpleAdapter(options, true) { option ->
                    text(option, 13, plum, true).apply {
                        gravity = Gravity.CENTER
                        background = bg(pink, 14)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        setOnClickListener {
                            val old = field.text.toString().trim()
                            field.setText(
                                if (
                                    append &&
                                        old.isNotEmpty() &&
                                        old != "Ninguno" &&
                                        option != "Ninguno"
                                )
                                    "$old, $option"
                                else option
                            )
                        }
                    }
                }
        }

    private fun <T> list(items: List<T>, render: (T) -> View): RecyclerView =
        RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
            adapter = SimpleAdapter(items, render = render)
        }

    private inner class SimpleAdapter<T>(
        private val items: List<T>,
        private val horizontal: Boolean = false,
        private val render: (T) -> View,
    ) : RecyclerView.Adapter<SimpleAdapter<T>.Holder>() {
        inner class Holder(val box: LinearLayout) : RecyclerView.ViewHolder(box)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                column().apply {
                    layoutParams = RecyclerView.LayoutParams(if (horizontal) -2 else -1, -2)
                    setPadding(0, 0, if (horizontal) dp(8) else 0, dp(10))
                }
            )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.box.removeAllViews()
            holder.box.addView(
                render(items[position]),
                LinearLayout.LayoutParams(if (horizontal) -2 else -1, -2),
            )
        }
    }

    private companion object {
        const val EXPORT_REPORT = 1001
    }
}
