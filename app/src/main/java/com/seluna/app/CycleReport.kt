package com.seluna.app

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

fun symptomCounts(entries: List<DailyEntry>): List<Pair<String, Int>> = entries
    .flatMap { entry ->
        entry.symptoms.substringAfter("\nSíntomas: ", entry.symptoms)
            .split(',')
            .map { it.trim().lowercase(Locale.forLanguageTag("es")) }
            .filter { it.isNotBlank() && it != "ninguno" }
            .distinct()
    }
    .groupingBy { it }.eachCount().toList()
    .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    .map { (name, count) -> name.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) } to count }

object CycleReport {
    fun write(output: OutputStream, entries: Int, starts: Int, estimate: CycleEstimate?, symptoms: List<Pair<String, Int>>) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val ink = Color.rgb(56, 45, 54)
            val plum = Color.rgb(116, 85, 99)
            val brush = Paint(Paint.ANTI_ALIAS_FLAG)
            fun label(value: String, y: Int, size: Int, color: Int = ink, bold: Boolean = false) {
                brush.color = color
                brush.textSize = size.toFloat()
                brush.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(value, 56f, y.toFloat(), brush)
            }
            canvas.drawColor(Color.rgb(252, 245, 245))
            label("Seluna", 90, 38, plum, true)
            label("Resumen de ciclo · ${LocalDate.now()}", 132, 20, ink, true)
            label("Registros diarios: $entries", 182, 15)
            label("Inicios de menstruación registrados: $starts", 208, 15)
            label(estimate?.let { "Duración estimada del ciclo: ${it.averageLength} días" } ?: "Duración estimada: pendiente de datos", 234, 15)
            label("Síntomas más frecuentes", 286, 19, plum, true)
            if (symptoms.isEmpty()) label("Sin síntomas registrados", 320, 15)
            symptoms.take(10).forEachIndexed { index, (name, count) ->
                label("${name.take(47)} · $count", 320 + index * 26, 15)
            }
            label("Resumen personal de seguimiento. La estimación puede variar.", 760, 13, plum)
            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }
}
