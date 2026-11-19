package com.seluna.app

import java.time.LocalDate

object DemoSeeder {
    const val EMAIL = "demo@seluna.example"
    const val PASSWORD = "SelunaDemo26!"

    fun signIn(store: SelunaStore): Boolean {
        if (!BuildConfig.DEBUG || store.currentUser() != null) return false
        var success = false
        try {
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                if (store.register("Luisa Demo", EMAIL, PASSWORD)) {
                    val today = LocalDate.now()
                    val last = today.minusDays(10)
                    val previous = last.minusDays(30)
                    val first = previous.minusDays(28)
                    store.addCycleStart(first.toString())
                    store.addCycleStart(previous.toString())
                    store.saveSettings(
                        CycleSettings(
                            last.toString(),
                            28,
                            5,
                            "Conocer mi ciclo",
                            "Recordatorios suaves",
                        )
                    )
                    val examples =
                        listOf(
                            DailyEntry(
                                last.toString(),
                                "Cansada",
                                "Flujo: Abundante\nSíntomas: Cólicos",
                                4,
                                "Descansé un poco más.",
                            ),
                            DailyEntry(
                                last.plusDays(1).toString(),
                                "Sensible",
                                "Flujo: Moderado\nSíntomas: Dolor de cabeza",
                                3,
                                "Tomé agua durante el día.",
                            ),
                            DailyEntry(
                                last.plusDays(3).toString(),
                                "Tranquila",
                                "Flujo: Ligero\nSíntomas: Ninguno",
                                1,
                                "Me sentí mejor.",
                            ),
                            DailyEntry(
                                today.minusDays(5).toString(),
                                "Feliz",
                                "Flujo: Ninguno\nSíntomas: Ninguno",
                                1,
                                "Salí a caminar.",
                            ),
                            DailyEntry(
                                today.minusDays(2).toString(),
                                "Cansada",
                                "Flujo: Ninguno\nSíntomas: Fatiga",
                                2,
                                "Día de mucho trabajo.",
                            ),
                            DailyEntry(
                                today.toString(),
                                "Tranquila",
                                "Flujo: Ninguno\nSíntomas: Ninguno",
                                1,
                                "Un día tranquilo.",
                            ),
                        )
                    examples.forEach(store::saveEntry)
                } else if (!store.login(EMAIL, PASSWORD)) {
                    return false
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            success = true
        } catch (_: Exception) {
            // The transaction rolls back a partial seed so a later attempt can retry.
        } finally {
            if (!success) store.logout()
        }
        return success
    }
}
