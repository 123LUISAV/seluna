package com.seluna.app

import java.time.LocalDate

object DemoSeeder {
    const val EMAIL = "demo@seluna.example"
    const val PASSWORD = "SelunaDemo26!"

    enum class Scenario(val label: String, val email: String, val profileName: String) {
        REGULAR("Seguimiento de ciclo", EMAIL, "Luisa Demo"),
        IRREGULAR("Ciclos variables y síntomas", "demo-sintomas@seluna.example", "Sara Demo"),
        NEW("Primer uso sin historial", "demo-inicio@seluna.example", "Ana Demo"),
    }

    val scenarios: List<Scenario> = Scenario.entries

    fun scenarioFor(email: String): Scenario? = scenarios.firstOrNull {
        it.email.equals(email.trim(), ignoreCase = true)
    }

    fun signIn(store: SelunaStore, scenario: Scenario = Scenario.REGULAR): Boolean {
        if (!BuildConfig.DEBUG || store.currentUser() != null) return false
        var success = false
        try {
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                if (store.register(scenario.profileName, scenario.email, PASSWORD)) {
                    if (scenario != Scenario.NEW) seedHistory(store, scenario)
                } else if (!store.login(scenario.email, PASSWORD)) {
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

    private fun seedHistory(store: SelunaStore, scenario: Scenario) {
        val irregular = scenario == Scenario.IRREGULAR
        val today = LocalDate.now()
        val last = today.minusDays(10)
        val previous = last.minusDays(if (irregular) 36 else 30)
        val first = previous.minusDays(if (irregular) 23 else 28)
        store.addCycleStart(first.toString())
        store.addCycleStart(previous.toString())
        store.completeOnboarding(
            CycleSettings(
                last.toString(),
                28,
                5,
                if (irregular) "Bienestar y síntomas" else "Conocer mi ciclo",
                if (irregular) "Alivio y control de síntomas" else "Recordatorios suaves",
                if (irregular) "Varía con frecuencia" else "Suele ser regular",
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
    }
}
