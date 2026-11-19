package com.seluna.app

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

fun symptomCounts(entries: List<DailyEntry>): List<Pair<String, Int>> =
    entries
        .flatMap { entry ->
            entry.symptoms
                .substringAfter("\nSíntomas: ", entry.symptoms)
                .split(',')
                .map { it.trim().lowercase(Locale.forLanguageTag("es")) }
                .filter { it.isNotBlank() && it != "ninguno" }
                .distinct()
        }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .map { (name, count) ->
            name.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) } to count
        }

object CycleReport {
    fun write(
        output: OutputStream,
        entries: List<DailyEntry>,
        starts: List<String>,
        estimate: CycleEstimate?,
        includeDiary: Boolean,
    ) {
        val document = PdfDocument()
        try {
            Writer(document).render(entries, starts, estimate, includeDiary)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private class Writer(private val document: PdfDocument) {
        private val ink = Color.rgb(56, 45, 54)
        private val plum = Color.rgb(116, 85, 99)
        private val pink = Color.rgb(247, 220, 229)
        private val mint = Color.rgb(220, 236, 231)
        private val lavender = Color.rgb(232, 234, 248)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private lateinit var page: PdfDocument.Page
        private var pageNumber = 0
        private var y = 0f
        private val left = 42f
        private val right = 553f
        private val bottom = 785f

        private fun style(size: Float, color: Int = ink, bold: Boolean = false) {
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.style = Paint.Style.FILL
        }

        private fun newPage() {
            if (pageNumber > 0) finishPage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawRect(0f, 0f, 595f, 12f, paint.apply { color = pink })
            style(17f, plum, true)
            page.canvas.drawText("Seluna", left, 39f, paint)
            style(9f, plum)
            page.canvas.drawText("INFORME PERSONAL DE CICLO", left, 55f, paint)
            y = 80f
        }

        private fun finishPage() {
            style(9f, plum)
            page.canvas.drawText("Seluna · Información personal", left, 818f, paint)
            page.canvas.drawText("Página $pageNumber", 505f, 818f, paint)
            document.finishPage(page)
        }

        private fun need(height: Float) {
            if (y + height > bottom) newPage()
        }

        // Uses Paint measurement instead of character counts, including for long unbroken words.
        private fun wrap(
            value: String,
            width: Float,
            size: Float = 10f,
            bold: Boolean = false,
        ): List<String> {
            style(size, ink, bold)
            val result = mutableListOf<String>()
            value.replace("\r", "").split('\n').forEach { paragraph ->
                if (paragraph.isEmpty()) {
                    result.add("")
                    return@forEach
                }
                var rest = paragraph
                while (rest.isNotEmpty()) {
                    var count = paint.breakText(rest, true, width, null).coerceAtLeast(1)
                    // Keep surrogate pairs together when wrapping emoji or other supplementary
                    // characters.
                    if (
                        count < rest.length &&
                            count > 0 &&
                            Character.isHighSurrogate(rest[count - 1]) &&
                            Character.isLowSurrogate(rest[count])
                    )
                        count--
                    if (count == 0) count = minOf(2, rest.length)
                    if (count < rest.length) {
                        val space = rest.lastIndexOf(' ', count - 1)
                        if (space > 0) count = space + 1
                    }
                    result.add(rest.substring(0, count).trimEnd())
                    rest = rest.substring(count).trimStart()
                }
            }
            return result.ifEmpty { listOf("") }
        }

        private fun paragraph(
            value: String,
            size: Float = 10f,
            color: Int = ink,
            bold: Boolean = false,
        ) {
            wrap(value, right - left, size, bold).forEach { line ->
                need(size + 6f)
                style(size, color, bold)
                page.canvas.drawText(line, left, y, paint)
                y += size + 5f
            }
        }

        private fun heading(value: String) {
            need(40f)
            y += 12f
            paragraph(value, 16f, plum, true)
            y += 5f
        }

        fun render(
            entries: List<DailyEntry>,
            starts: List<String>,
            estimate: CycleEstimate?,
            includeDiary: Boolean,
        ) {
            newPage()
            style(26f, plum, true)
            page.canvas.drawText(
                if (includeDiary) "Informe de seguimiento" else "Resumen de seguimiento",
                left,
                y,
                paint,
            )
            y += 23f
            val dates =
                (entries.map { it.date } + starts)
                    .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                    .sorted()
            val range =
                if (dates.isEmpty()) "Sin fechas registradas"
                else "${dates.first()} — ${dates.last()}"
            paragraph("Periodo disponible: $range   ·   Generado: ${LocalDate.now()}", 10f, plum)
            paragraph(
                if (includeDiary) "Incluye datos diarios. Las notas libres se conservan en el diario."
                else "Resumen sin detalle diario ni notas libres.",
                10f,
                plum,
            )
            y += 12f
            need(75f)
            val labels = listOf("REGISTROS", "INICIOS REALES", "DURACIÓN ESTIMADA")
            val values =
                listOf(
                    entries.size.toString(),
                    starts.size.toString(),
                    estimate?.let { "${it.averageLength} días" } ?: "Sin datos",
                )
            val colors = listOf(pink, mint, lavender)
            for (i in 0..2) {
                val x = left + i * 173f
                paint.color = colors[i]
                page.canvas.drawRoundRect(x, y, x + 164f, y + 64f, 10f, 10f, paint)
                style(8f, plum, true)
                page.canvas.drawText(labels[i], x + 10f, y + 20f, paint)
                style(17f, ink, true)
                page.canvas.drawText(values[i], x + 10f, y + 45f, paint)
            }
            y += 82f
            paragraph(
                estimate?.let {
                    "Próximo inicio estimado: ${it.nextStart}. Puede variar según tu ciclo."
                } ?: "Próximo inicio estimado: aún no hay datos suficientes.",
                11f,
                ink,
                true,
            )
            paragraph("Esta estimación es orientativa y no es un método anticonceptivo.", 10f, plum)
            heading("Inicios registrados")
            if (starts.isEmpty()) paragraph("No hay inicios registrados.")
            else paragraph(starts.sorted().joinToString("  ·  "), 10f)
            heading("Síntomas registrados")
            paragraph(
                "El ancho indica la proporción de registros con el síntoma. La cifra muestra el recuento.",
                10f,
                plum,
            )
            y += 6f
            val symptoms = symptomCounts(entries)
            if (symptoms.isEmpty()) paragraph("No hay síntomas registrados.")
            val totalEntries = entries.size.coerceAtLeast(1)
            symptoms.forEach { (name, count) ->
                // Names are wrapped and may continue over pages; every symptom remains visible.
                val lines = wrap(name, 270f, 10f)
                lines.forEachIndexed { index, line ->
                    need(19f)
                    style(10f)
                    page.canvas.drawText(line, left, y, paint)
                    if (index == 0) {
                        paint.color = lavender
                        page.canvas.drawRoundRect(335f, y - 9f, 508f, y + 2f, 5f, 5f, paint)
                        paint.color = plum
                        page.canvas.drawRoundRect(
                            335f,
                            y - 9f,
                            335f + 173f * count / totalEntries,
                            y + 2f,
                            5f,
                            5f,
                            paint,
                        )
                        style(10f, plum, true)
                        page.canvas.drawText(count.toString(), 518f, y, paint)
                    }
                    y += 16f
                }
                y += 3f
            }
            if (includeDiary) diary(entries)
            heading("Sobre este informe")
            paragraph(
                "Informe generado localmente con la información guardada en el dispositivo. No sustituye una valoración médica."
            )
            paragraph("Referencia: womenshealth.gov/menstrual-cycle/your-menstrual-cycle", 9f, plum)
            finishPage()
        }

        private fun diary(entries: List<DailyEntry>) {
            heading("Detalle diario")
            paragraph(
                "Fecha, flujo, ánimo, intensidad y síntomas. Las notas privadas no se incluyen.",
                10f,
                plum,
            )
            y += 8f
            val widths = floatArrayOf(70f, 76f, 80f, 58f, 227f)
            val labels = listOf("Fecha", "Flujo", "Ánimo", "Intensidad", "Síntomas")
            fun header() {
                need(30f)
                paint.color = pink
                page.canvas.drawRect(left, y - 12f, right, y + 12f, paint)
                var x = left
                labels.forEachIndexed { i, label ->
                    style(9f, plum, true)
                    page.canvas.drawText(label, x + 3f, y + 3f, paint)
                    x += widths[i]
                }
                y += 22f
            }
            header()
            if (entries.isEmpty()) {
                paragraph("No hay registros diarios.")
                return
            }
            entries
                .sortedBy { it.date }
                .forEach { entry ->
                    val parts = entry.symptoms.split("\nSíntomas: ", limit = 2)
                    val flow =
                        if (parts.size == 2 && parts[0].startsWith("Flujo: "))
                            parts[0].removePrefix("Flujo: ")
                        else "—"
                    val symptomText = if (parts.size == 2) parts[1] else entry.symptoms
                    val values =
                        listOf(
                            entry.date,
                            flow,
                            entry.mood,
                            entry.intensity.toString(),
                            symptomText,
                        )
                    val lines =
                        values.mapIndexed { i, value ->
                            wrap(value.ifBlank { "—" }, widths[i] - 7f, 9f)
                        }
                    val total = lines.maxOf { it.size }
                    var offset = 0
                    while (offset < total) {
                        if (y + 20f > bottom) {
                            newPage()
                            header()
                        }
                        val count = minOf(total - offset, max(1, ((bottom - y - 4f) / 13f).toInt()))
                        var x = left
                        lines.forEachIndexed { i, column ->
                            style(9f)
                            for (line in offset until minOf(offset + count, column.size)) {
                                page.canvas.drawText(
                                    column[line],
                                    x + 3f,
                                    y + (line - offset) * 13f,
                                    paint,
                                )
                            }
                            x += widths[i]
                        }
                        y += count * 13f + 6f
                        paint.color = Color.rgb(234, 225, 229)
                        page.canvas.drawLine(left, y - 3f, right, y - 3f, paint)
                        offset += count
                        if (offset < total) {
                            newPage()
                            header()
                        }
                    }
                }
        }
    }
}
