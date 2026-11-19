package com.seluna.app

import android.app.*
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
    private lateinit var cloud: SelunaCloud
    private var cloudBusy = false
    private lateinit var guide: SelunaGuide
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
        cloud = SelunaCloud(this, store)
        guide = SelunaGuide(this)
        if (store.currentUser() == null) {
            cloud.signOut()
            auth(false)
        } else showPage(0)
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
        val welcome =
            card(pink).apply {
                background =
                    GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            intArrayOf(pink, ivory, mint),
                        )
                        .apply { cornerRadius = dp(24).toFloat() }
                setPadding(dp(22), dp(24), dp(22), dp(22))
            }
        add(
            welcome,
            text(if (register) "Tu espacio, a tu ritmo" else "Qué bueno verte", 30, ink, true),
        )
        add(
            welcome,
            text(
                if (register) "Crea tu cuenta y empieza a conocerte mejor."
                else "Inicia sesión para continuar tu bienestar.",
                16,
                plum,
            ),
            8,
        )
        add(body, welcome, 10)
        val form = card()
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
        listOf(name, email, pass).forEach { it.background = bg(ivory, 14) }
        if (register) add(form, name)
        add(form, email, if (register) 12 else 0)
        add(form, pass, 12)
        gap(form, 18)
        add(
            form,
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
                        val ok =
                            if (register) store.register(n, e, p)
                            else {
                                store.login(e, p) ||
                                    (BuildConfig.DEBUG &&
                                        e.equals(DemoSeeder.EMAIL, ignoreCase = true) &&
                                        p == DemoSeeder.PASSWORD &&
                                        DemoSeeder.signIn(store))
                            }
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
        add(body, form, 16)
        add(
            body,
            button(if (register) "Ya tengo una cuenta" else "Crear una cuenta", true) {
                auth(!register)
            },
            12,
        )
        if (BuildConfig.DEBUG && store.currentUser() == null) {
            add(
                body,
                button("Entrar con datos de muestra", true) {
                    if (DemoSeeder.signIn(store)) showPage(0)
                    else toast("No se pudo abrir la cuenta de muestra")
                },
                12,
            )
        }
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
        val est = store.estimate()
        val ring = CycleRing(days, est?.averageLength ?: store.settings().cycleLength)
        ring.contentDescription =
            if (days > 0) "Día $days desde el último inicio registrado" else "Sin inicio registrado"
        val wrap = row().apply { gravity = Gravity.CENTER }
        wrap.addView(ring, LinearLayout.LayoutParams(dp(190), dp(190)))
        c.addView(wrap)
        gap(c, 16)
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
        if (cloud.available) add(body, button("Copia en Firebase", true) { cloudPage() }, 10)
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
        editorial("Consejo de Seluna", guide.articles.filter(GuideArticle::wellness).take(1))
        add(
            body,
            button("Cerrar sesión", true) {
                cloud.signOut()
                store.logout()
                messages.clear()
                chatDraft = ""
                auth(false)
            },
            14,
        )
    }

    /** Decorative segments show elapsed days only; they do not imply cycle phases. */
    private inner class CycleRing(private val day: Int, private val length: Int) :
        View(this@MainActivity) {
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bounds = RectF()
        private val arcColors =
            intArrayOf(
                plum,
                Color.rgb(205, 170, 184),
                Color.rgb(180, 208, 199),
                Color.rgb(185, 189, 220),
            )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) * .38f
            bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = dp(11).toFloat()
            brush.strokeCap = Paint.Cap.ROUND
            arcColors.forEachIndexed { i, color ->
                brush.color = color
                canvas.drawArc(bounds, -88f + i * 90f, 82f, false, brush)
            }
            if (day > 0) {
                val fraction = ((day - 1).toFloat() / length.coerceAtLeast(1)).coerceIn(0f, 1f)
                val angle = Math.toRadians((-90f + fraction * 360f).toDouble())
                val x = cx + radius * kotlin.math.cos(angle).toFloat()
                val y = cy + radius * kotlin.math.sin(angle).toFloat()
                brush.style = Paint.Style.FILL
                brush.color = Color.WHITE
                canvas.drawCircle(x, y, dp(9).toFloat(), brush)
                brush.color = plum
                canvas.drawCircle(x, y, dp(5).toFloat(), brush)
            }
            brush.style = Paint.Style.FILL
            brush.color = plum
            brush.textAlign = Paint.Align.CENTER
            brush.typeface = Typeface.DEFAULT_BOLD
            brush.textSize = dp(14).toFloat()
            canvas.drawText(if (day > 0) "DÍA" else "TU CICLO", cx, cy - dp(8), brush)
            brush.textSize = dp(40).toFloat()
            canvas.drawText(if (day > 0) day.toString() else "✿", cx, cy + dp(34), brush)
        }
    }

    private fun cloudPage() {
        base()
        title("Copia en Firebase", "Guarda o recupera una copia de tu diario cuando lo decidas.")
        if (cloudBusy) {
            add(body, text("Conectando con Firebase…", 15, plum))
            return
        }

        val email = cloud.accountEmail
        val account = card()
        add(
            account,
            text(if (email == null) "Cuenta de Firebase" else "Cuenta conectada", 18, ink, true),
        )
        add(
            account,
            text(email ?: "Usa correo y contraseña para conectar este perfil local.", 15, plum),
            8,
        )
        add(body, account)

        if (email == null) {
            add(body, button("Iniciar sesión en Firebase") { cloudAuth(create = false) }, 16)
            add(body, button("Crear cuenta en Firebase", true) { cloudAuth(create = true) }, 10)
        } else {
            add(
                body,
                button("Guardar copia en Firebase") {
                    AlertDialog.Builder(this)
                        .setTitle("Guardar copia")
                        .setMessage(
                            "Se enviarán tu configuración de ciclo, inicios y diario, incluidas las notas, a la cuenta de Firebase conectada. Los datos reemplazarán la última copia de esta cuenta."
                        )
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Guardar") { _, _ ->
                            startCloudOperation()
                            cloud.backup {
                                finishCloudOperation(it) { "Copia guardada en Firebase" }
                            }
                        }
                        .show()
                },
                16,
            )
            add(
                body,
                button("Restaurar copia", true) {
                    AlertDialog.Builder(this)
                        .setTitle("Restaurar copia")
                        .setMessage(
                            "Se añadirán los inicios y registros que falten en este dispositivo. Se conservarán tus datos y cambios locales."
                        )
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Restaurar") { _, _ ->
                            startCloudOperation()
                            cloud.restore {
                                finishCloudOperation(it) { count ->
                                    if (count == 0) "Tu perfil ya contiene esos datos"
                                    else "Datos recuperados: $count"
                                }
                            }
                        }
                        .show()
                },
                10,
            )
            add(
                body,
                button("Cerrar sesión en Firebase", true) {
                    cloud.signOut()
                    cloudPage()
                },
                10,
            )
        }
        add(
            body,
            text(
                "Tu diario funciona en SQLite y la copia se guarda al confirmar el envío.",
                14,
                plum,
            ),
            16,
        )
        add(body, button("Volver a Hoy", true) { showPage(0) }, 14)
    }

    private fun cloudAuth(create: Boolean) {
        val fields = column().apply { setPadding(dp(20), dp(6), dp(20), 0) }
        val email =
            input(
                "Correo electrónico",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            )
        val password =
            input(
                "Contraseña",
                type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            )
        add(fields, email)
        add(fields, password, 8)
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(if (create) "Crear cuenta en Firebase" else "Iniciar sesión en Firebase")
                .setView(fields)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(if (create) "Crear cuenta" else "Iniciar sesión", null)
                .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val address = email.text.toString().trim()
            val secret = password.text.toString()
            when {
                !android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches() ->
                    email.error = "Escribe un correo válido"
                secret.length < 6 -> password.error = "Usa al menos 6 caracteres"
                else -> {
                    dialog.dismiss()
                    startCloudOperation()
                    cloud.authenticate(address, secret, create) { result ->
                        finishCloudOperation(result) { "Cuenta de Firebase conectada" }
                    }
                }
            }
        }
    }

    private fun startCloudOperation() {
        cloudBusy = true
        cloudPage()
    }

    private fun <T> finishCloudOperation(result: Result<T>, message: (T) -> String) {
        cloudBusy = false
        if (isFinishing || isDestroyed || store.currentUser() == null) return
        val status =
            result.fold(message) { error ->
                when (error) {
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                        "Firebase no aceptó el correo o la contraseña"
                    is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                        "Ese correo ya tiene cuenta en Firebase"
                    is com.google.firebase.firestore.FirebaseFirestoreException ->
                        when (error.code) {
                            com.google.firebase.firestore.FirebaseFirestoreException.Code
                                .PERMISSION_DENIED -> "Firebase denegó el acceso a la copia"
                            com.google.firebase.firestore.FirebaseFirestoreException.Code
                                .UNAVAILABLE ->
                                "Firestore no está disponible. Comprueba la conexión"
                            else -> "Firestore no pudo completar la operación"
                        }
                    is com.google.firebase.FirebaseNetworkException ->
                        "Firebase necesita conexión para esta operación"
                    else -> error.localizedMessage ?: "Firebase no pudo completar la operación"
                }
            }
        toast(status)
        cloudPage()
    }

    private fun profile(first: Boolean, draft: CycleSettings? = null) {
        base()
        val s = draft ?: store.settings()
        if (first) add(body, label("Paso 1 de 2 · Tu ciclo"), 6)
        title(
            if (first) "Conozcamos tu ciclo" else "Tu perfil de ciclo",
            "Puedes cambiar estos datos cuando quieras.",
        )
        var date =
            draft?.lastStart?.let(LocalDate::parse)
                ?: store.cycleStarts().lastOrNull()?.let(LocalDate::parse)
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
        if (!first) {
            add(body, label("Tu objetivo"), 16)
            add(body, goal, 8)
            add(body, label("Preferencias"), 16)
            add(body, preferences, 8)
        }
        add(
            body,
            button(if (first) "Continuar a personalización" else "Guardar perfil") {
                val cl = cycle.text.toString().toIntOrNull()
                val pl = period.text.toString().toIntOrNull()
                val chosenDate = date
                when {
                    chosenDate == null -> toast("Selecciona el último inicio")
                    cl == null || cl !in 15..60 -> cycle.error = "Elige entre 15 y 60 días"
                    pl == null || pl !in 1..15 || pl > cl ->
                        period.error = "Elige entre 1 y 15 días"
                    !first && goal.text.toString().trim().isEmpty() ->
                        goal.error = "Escribe tu objetivo"
                    else -> {
                        val updated =
                            CycleSettings(
                                chosenDate.toString(),
                                cl,
                                pl,
                                goal.text.toString().trim(),
                                preferences.text.toString().trim(),
                            )
                        if (first) personalizeProfile(updated)
                        else {
                            store.saveSettings(updated)
                            showPage(0)
                        }
                    }
                }
            },
            20,
        )
        if (!first) add(body, button("Volver", true) { showPage(0) }, 10)
    }

    private fun personalizeProfile(draft: CycleSettings) {
        base()
        add(body, label("Paso 2 de 2 · Personalización"), 6)
        title(
            "¿Qué quieres seguir?",
            "Elige un objetivo para tu perfil. Puedes editarlo más adelante.",
        )
        val goal = input("Escribe o elige un objetivo", draft.goal).withLimit(120)
        add(body, label("Tu objetivo"))
        add(
            body,
            choices(
                listOf("Conocer mi ciclo", "Registrar síntomas", "Observar mi bienestar"),
                goal,
            ),
            8,
        )
        add(body, goal, 8)
        val preferences = input("Ej. temas de bienestar", draft.preferences).withLimit(200)
        add(body, label("Preferencias opcionales"), 16)
        add(body, preferences, 8)
        val values = {
            draft.copy(
                goal = goal.text.toString().trim(),
                preferences = preferences.text.toString().trim(),
            )
        }
        add(
            body,
            button("Guardar perfil") {
                val updated = values()
                if (updated.goal.isEmpty()) goal.error = "Escribe o elige un objetivo"
                else {
                    store.saveSettings(updated)
                    showPage(0)
                }
            },
            20,
        )
        add(body, button("Volver al ciclo", true) { profile(first = true, draft = values()) }, 10)
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
        val calendarCard = card()
        val controls = row()
        controls.addView(
            button("‹", true) { changeMonth(-1) }.apply { contentDescription = "Mes anterior" },
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
            button("›", true) { changeMonth(1) }.apply { contentDescription = "Mes siguiente" },
            LinearLayout.LayoutParams(dp(52), -2),
        )
        add(calendarCard, controls)
        gap(calendarCard, 12)
        val weekdays = row()
        listOf("L", "M", "X", "J", "V", "S", "D").forEach {
            weekdays.addView(
                text(it, 13, plum, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
        }
        add(calendarCard, weekdays, 8)
        val offset = month.atDay(1).dayOfWeek.value - 1
        val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        val days =
            (0 until count).map { i ->
                if (i < offset || i >= offset + month.lengthOfMonth()) null
                else month.atDay(i - offset + 1)
            }
        val starts = store.cycleStarts().toSet()
        val entries = store.entries().map { it.date }.toSet()
        val today = LocalDate.now()
        val estimatedStart = store.estimate()?.nextStart?.let { parse(it) }
        val estimatedEnd = estimatedStart?.plusDays((store.settings().periodLength - 1).toLong())
        val grid =
            RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, 7)
                isNestedScrollingEnabled = false
                adapter =
                    SimpleAdapter(days) { d ->
                        val actual = d?.toString() in starts
                        val recorded = d?.toString() in entries
                        val estimated =
                            d != null &&
                                estimatedStart != null &&
                                estimatedEnd != null &&
                                d in estimatedStart..estimatedEnd &&
                                d.isAfter(today)
                        text(
                                if (d == null) ""
                                else
                                    "${d.dayOfMonth}\n${if(actual) "●" else if(recorded) "·" else "\u00A0"}",
                                14,
                                if (d == selectedDate) Color.WHITE else ink,
                                d == selectedDate,
                            )
                            .apply {
                                gravity = Gravity.CENTER
                                minHeight = dp(50)
                                when {
                                    d == selectedDate -> background = bg(plum, 14)
                                    actual -> background = bg(pink, 14)
                                    estimated -> background = bg(lavender, 14)
                                }
                                if (d != null) {
                                    contentDescription =
                                        buildList {
                                                add(dateText(d))
                                                if (d == selectedDate) add("seleccionado")
                                                if (actual) add("inicio real")
                                                if (recorded) add("registro diario")
                                                if (estimated) add("periodo estimado")
                                            }
                                            .joinToString(", ")
                                    setOnClickListener {
                                        selectedDate = d
                                        showPage(1)
                                    }
                                }
                            }
                    }
            }
        add(calendarCard, grid, 12)
        val legend = row()
        for ((name, color) in
            listOf(
                "Inicio real" to pink,
                "Periodo estimado" to lavender,
                "Registro diario" to ivory,
            )) {
            val item =
                text(name, 11, plum, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    background = bg(color, 9)
                }
            legend.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        add(calendarCard, legend, 8)
        add(body, calendarCard)
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
        add(
            totals,
            text("${entries.size} $recordsLabel  ·  ${starts.size} $startsLabel", 19, ink, true),
        )
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
        editorial("Guías de bienestar", guide.articles.filter(GuideArticle::wellness))
        add(body, button("Exportar informe de salud en PDF") { exportHealthReport() }, 16)
        add(
            body,
            text(
                "Puedes exportar un resumen o añadir el detalle del diario. El PDF excluye las notas personales.",
                13,
                plum,
            ),
            8,
        )
    }

    private fun exportHealthReport() {
        AlertDialog.Builder(this)
            .setTitle("Contenido del informe")
            .setItems(arrayOf("Solo resumen", "Resumen con detalle del diario")) { _, selected ->
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_TITLE, "Seluna-informe-${LocalDate.now()}.pdf")
                    },
                    if (selected == 0) EXPORT_REPORT else EXPORT_REPORT_WITH_DIARY,
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (
            requestCode !in EXPORT_REPORT..EXPORT_REPORT_WITH_DIARY ||
                resultCode != RESULT_OK ||
                store.currentUser() == null
        )
            return
        val target = data?.data ?: return
        val entries = store.entries()
        try {
            val output =
                contentResolver.openOutputStream(target) ?: error("No se pudo abrir el destino")
            output.use {
                CycleReport.write(
                    it,
                    entries,
                    store.cycleStarts(),
                    store.estimate(),
                    includeDiary = requestCode == EXPORT_REPORT_WITH_DIARY,
                )
            }
            toast("Informe PDF guardado")
        } catch (_: Exception) {
            toast("No se pudo guardar el resumen PDF")
        }
    }

    private fun EditText.withLimit(length: Int) = apply {
        filters = arrayOf(InputFilter.LengthFilter(length))
    }

    private fun chat(waiting: Boolean = false) {
        base()
        title("Conversemos ✧", "Un espacio para tus preguntas de bienestar.")
        add(
            body,
            text(
                "La guía responde en el dispositivo. Para otras preguntas, el chat puede compartir tu mensaje y hasta dos anteriores con B.AI. Tu diario queda en el dispositivo. La orientación es general.",
                14,
                plum,
            ),
        )
        gap(body, 12)
        add(body, text("Preguntas para empezar", 16, ink, true))
        val quickQuestions =
            RecyclerView(this).apply {
                layoutManager =
                    LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
                isNestedScrollingEnabled = false
                adapter =
                    SimpleAdapter(guide.articles, horizontal = true) { article ->
                        button(article.question, true) { answerLocally(article.question, article) }
                            .apply {
                                maxWidth = dp(215)
                                isEnabled = !waiting
                            }
                    }
            }
        add(body, quickQuestions, 8)
        if (messages.isEmpty()) {
            val welcome = card(lavender)
            add(welcome, text("Hola, soy Seluna. ¿En qué puedo acompañarte hoy?", 15))
            add(body, welcome)
        } else
            add(
                body,
                list(messages) { m ->
                    val mine = m.first == "user"
                    val bubble =
                        card(if (mine) pink else lavender).apply {
                            add(this, text(if (mine) "TÚ" else "✦  SELUNA", 12, plum, true))
                            val message =
                                text("", 15).apply {
                                    text = if (mine) m.second else formatChat(m.second)
                                    setLineSpacing(dp(3).toFloat(), 1f)
                                    setTextIsSelectable(true)
                                }
                            add(this, message, 8)
                            setOnClickListener {
                                AlertDialog.Builder(this@MainActivity)
                                    .setMessage(if (mine) m.second else formatChat(m.second))
                                    .setPositiveButton("Cerrar", null)
                                    .show()
                            }
                        }
                    row().apply {
                        gravity = if (mine) Gravity.END else Gravity.START
                        addView(
                            bubble,
                            LinearLayout.LayoutParams(
                                (resources.displayMetrics.widthPixels * .78f).toInt(),
                                -2,
                            ),
                        )
                    }
                },
            )
        val scroll = body.parent as ScrollView
        if (messages.isNotEmpty()) scroll.post { scroll.smoothScrollTo(0, body.height) }
        if (waiting) {
            val status = text("Seluna está escribiendo…", 15, plum)
            status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            add(body, status, 8)
            return
        }

        val field =
            input(
                    "Escribe tu mensaje",
                    chatDraft,
                    type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                )
                .withLimit(1_500)
                .apply { maxLines = 3 }
        val send =
            button("Enviar") {
                val content = field.text.toString().trim()
                if (content.isNotEmpty()) {
                    chatDraft = ""
                    val article = guide.match(content)
                    if (article != null) answerLocally(content, article)
                    else {
                        messages.add("user" to content)
                        chatLoading()
                    }
                } else field.error = "Escribe un mensaje"
            }
        val composer =
            column().apply {
                setPadding(dp(16), dp(8), dp(16), dp(6))
                setBackgroundColor(ivory)
            }
        val actions = row()
        actions.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(send, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        add(composer, actions)
        add(
            composer,
            text("Volver", 14, plum, true).apply {
                minHeight = dp(44)
                gravity = Gravity.CENTER
                isFocusable = true
                setOnClickListener { showPage(page) }
            },
        )
        root.addView(composer)
    }

    private fun answerLocally(question: String, article: GuideArticle) {
        chatDraft = ""
        messages.add("user" to question)
        val source = article.sourceLabel?.let { "\n\nFuente: $it" } ?: ""
        messages.add("assistant" to (article.answer + source))
        chat()
    }

    private fun editorial(heading: String, articles: List<GuideArticle>) {
        if (articles.isEmpty()) return
        add(body, text(heading, 20, ink, true), 22)
        add(
            body,
            list(articles) { article ->
                card(if (articles.indexOf(article) % 2 == 0) mint else lavender).apply {
                    if (article.title.contains("infusiones", ignoreCase = true)) {
                        val image =
                            ImageView(this@MainActivity).apply {
                                setImageResource(R.drawable.seluna_infusion)
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                background = bg(lavender, 14)
                                clipToOutline = true
                            }
                        addView(
                            image,
                            LinearLayout.LayoutParams(-1, dp(145)).apply { bottomMargin = dp(14) },
                        )
                    }
                    add(this, text("✦  GUÍA DE BIENESTAR", 11, plum, true))
                    add(this, text(article.title, 18, ink, true), 8)
                    add(this, text(article.question, 14, plum), 6)
                    add(this, text("Leer consejo  →", 13, plum, true), 12)
                    contentDescription = "${article.title}. Abrir guía"
                    setOnClickListener { showArticle(article) }
                }
            },
            10,
        )
    }

    private fun showArticle(article: GuideArticle) {
        val dialog =
            AlertDialog.Builder(this)
                .setTitle(article.title)
                .setMessage(formatChat(article.answer))
                .setNegativeButton("Cerrar", null)
        val url = article.sourceUrl
        if (url != null && url.startsWith("https://")) {
            dialog.setPositiveButton(article.sourceLabel ?: "Ver fuente") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    toast("No se pudo abrir la fuente")
                }
            }
        }
        dialog.show()
    }

    private fun formatChat(source: String): CharSequence {
        val result = SpannableStringBuilder()
        source.take(8000).lines().forEachIndexed { index, original ->
            if (index > 0) result.append("\n")
            val trimmed = original.trimStart()
            val headingPrefix = trimmed.takeWhile { it == '#' }
            val heading =
                headingPrefix.length in 1..6 && trimmed.getOrNull(headingPrefix.length) == ' '
            var line = if (heading) trimmed.drop(headingPrefix.length).trimStart() else original
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) line = "• " + trimmed.drop(2)
            val start = result.length
            var pos = 0
            while (pos < line.length) {
                val open = line.indexOf("**", pos)
                if (open < 0) {
                    result.append(line.substring(pos))
                    break
                }
                val close = line.indexOf("**", open + 2)
                if (close < 0) {
                    result.append(line.substring(pos))
                    break
                }
                result.append(line.substring(pos, open))
                val boldStart = result.length
                result.append(line.substring(open + 2, close))
                if (result.length > boldStart) {
                    result.setSpan(
                        StyleSpan(Typeface.BOLD),
                        boldStart,
                        result.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                pos = close + 2
            }
            if (heading && result.length > start) {
                result.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    result.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                result.setSpan(
                    RelativeSizeSpan(1.15f),
                    start,
                    result.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return result
    }

    private fun chatLoading() {
        chat(waiting = true)
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
        const val EXPORT_REPORT_WITH_DIARY = 1002
    }
}
