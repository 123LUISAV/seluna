package com.seluna.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.roundToInt

data class UserProfile(val id: Long, val name: String, val email: String)

data class CycleSettings(
    val lastStart: String = "",
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val goal: String = "",
    val preferences: String = "",
)

data class DailyEntry(
    val date: String,
    val mood: String = "",
    val symptoms: String = "",
    val intensity: Int = 0,
    val note: String = "",
)

data class CycleEstimate(
    val nextStart: String,
    val averageLength: Int,
    val basedOnHistory: Boolean,
)

class SelunaStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "seluna.db", null, 1) {
    private val session =
        context.applicationContext.getSharedPreferences("seluna_session", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, salt TEXT NOT NULL, password_hash TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE settings (user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, last_start TEXT NOT NULL, cycle_length INTEGER NOT NULL, period_length INTEGER NOT NULL, goal TEXT NOT NULL, preferences TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE entries (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, date TEXT NOT NULL, mood TEXT NOT NULL, symptoms TEXT NOT NULL, intensity INTEGER NOT NULL, note TEXT NOT NULL, PRIMARY KEY(user_id, date))"
        )
        db.execSQL(
            "CREATE TABLE cycle_starts (user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, date TEXT NOT NULL, PRIMARY KEY(user_id, date))"
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private fun emailKey(email: String) = email.trim().lowercase(java.util.Locale.ROOT)

    private fun uid(): Long =
        currentUser()?.id ?: throw IllegalStateException("Inicia sesión primero")

    private fun validDate(date: String) {
        val parsed = LocalDate.parse(date)
        require(parsed.toString() == date && !parsed.isAfter(LocalDate.now())) { "Fecha inválida" }
    }

    fun register(name: String, email: String, password: String): Boolean {
        val cleanName = name.trim()
        val cleanEmail = emailKey(email)
        if (
            cleanName.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() ||
                password.length < 6
        )
            return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val values =
            ContentValues().apply {
                put("name", cleanName)
                put("email", cleanEmail)
                put("salt", encode(salt))
                put("password_hash", encode(hash(password, salt)))
            }
        val id =
            writableDatabase.insertWithOnConflict(
                "users",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
        if (id < 0) return false
        session.edit().putLong("user_id", id).apply()
        return true
    }

    fun login(email: String, password: String): Boolean {
        readableDatabase
            .rawQuery(
                "SELECT id, salt, password_hash FROM users WHERE email = ?",
                arrayOf(emailKey(email)),
            )
            .use { c ->
                if (!c.moveToFirst()) return false
                if (
                    !MessageDigest.isEqual(
                        hash(password, decode(c.getString(1))),
                        decode(c.getString(2)),
                    )
                )
                    return false
                session.edit().putLong("user_id", c.getLong(0)).apply()
                return true
            }
    }

    fun logout() {
        session.edit().remove("user_id").apply()
    }

    fun currentUser(): UserProfile? {
        val id = session.getLong("user_id", -1)
        if (id < 0) return null
        readableDatabase
            .rawQuery("SELECT id, name, email FROM users WHERE id = ?", arrayOf(id.toString()))
            .use { c ->
                return if (c.moveToFirst())
                    UserProfile(c.getLong(0), c.getString(1), c.getString(2))
                else null
            }
    }

    fun settings(): CycleSettings {
        val id = uid()
        readableDatabase
            .rawQuery(
                "SELECT last_start, cycle_length, period_length, goal, preferences FROM settings WHERE user_id = ?",
                arrayOf(id.toString()),
            )
            .use { c ->
                return if (c.moveToFirst())
                    CycleSettings(
                        c.getString(0),
                        c.getInt(1),
                        c.getInt(2),
                        c.getString(3),
                        c.getString(4),
                    )
                else CycleSettings()
            }
    }

    fun saveSettings(settings: CycleSettings) {
        val id = uid()
        require(
            settings.cycleLength in 15..90 && settings.periodLength in 1..settings.cycleLength
        ) {
            "Duración inválida"
        }
        if (settings.lastStart.isNotEmpty()) validDate(settings.lastStart)
        val values =
            ContentValues().apply {
                put("user_id", id)
                put("last_start", settings.lastStart)
                put("cycle_length", settings.cycleLength)
                put("period_length", settings.periodLength)
                put("goal", settings.goal)
                put("preferences", settings.preferences)
            }
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (date in
                listOf(this.settings().lastStart, settings.lastStart)
                    .filter { it.isNotEmpty() }
                    .distinct()) {
                insertCycleStart(db, id, date)
            }
            check(
                db.insertWithOnConflict(
                    "settings",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                ) >= 0
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun entries(): List<DailyEntry> {
        val result = mutableListOf<DailyEntry>()
        readableDatabase
            .rawQuery(
                "SELECT date, mood, symptoms, intensity, note FROM entries WHERE user_id = ? ORDER BY date DESC",
                arrayOf(uid().toString()),
            )
            .use { c ->
                while (c.moveToNext()) result.add(
                    DailyEntry(
                        c.getString(0),
                        c.getString(1),
                        c.getString(2),
                        c.getInt(3),
                        c.getString(4),
                    )
                )
            }
        return result
    }

    fun entry(date: String): DailyEntry? {
        validDate(date)
        readableDatabase
            .rawQuery(
                "SELECT date, mood, symptoms, intensity, note FROM entries WHERE user_id = ? AND date = ?",
                arrayOf(uid().toString(), date),
            )
            .use { c ->
                return if (c.moveToFirst())
                    DailyEntry(
                        c.getString(0),
                        c.getString(1),
                        c.getString(2),
                        c.getInt(3),
                        c.getString(4),
                    )
                else null
            }
    }

    fun saveEntry(entry: DailyEntry) {
        validDate(entry.date)
        require(
            entry.intensity in 1..5 &&
                entry.mood.isNotBlank() &&
                entry.mood.length <= 80 &&
                entry.symptoms.length <= 300 &&
                entry.note.length <= 1500
        ) {
            "Registro inválido"
        }
        val values =
            ContentValues().apply {
                put("user_id", uid())
                put("date", entry.date)
                put("mood", entry.mood)
                put("symptoms", entry.symptoms)
                put("intensity", entry.intensity)
                put("note", entry.note)
            }
        check(
            writableDatabase.insertWithOnConflict(
                "entries",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            ) >= 0
        )
    }

    fun deleteEntry(date: String) {
        writableDatabase.delete(
            "entries",
            "user_id = ? AND date = ?",
            arrayOf(uid().toString(), date),
        )
    }

    fun cycleStarts(): List<String> {
        val result = mutableListOf<String>()
        val user = uid().toString()
        readableDatabase
            .rawQuery(
                "SELECT date FROM cycle_starts WHERE user_id = ? UNION SELECT last_start FROM settings WHERE user_id = ? AND last_start <> '' ORDER BY 1",
                arrayOf(user, user),
            )
            .use { c -> while (c.moveToNext()) result.add(c.getString(0)) }
        return result
    }

    fun addCycleStart(date: String) {
        validDate(date)
        insertCycleStart(writableDatabase, uid(), date)
    }

    private fun insertCycleStart(db: SQLiteDatabase, user: Long, date: String) {
        val values =
            ContentValues().apply {
                put("user_id", user)
                put("date", date)
            }
        db.insertWithOnConflict("cycle_starts", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun deleteCycleStart(date: String) {
        val user = uid().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val keys = arrayOf(user, date)
            db.delete("cycle_starts", "user_id = ? AND date = ?", keys)
            val clear = ContentValues().apply { put("last_start", "") }
            db.update("settings", clear, "user_id = ? AND last_start = ?", keys)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun estimate(): CycleEstimate? {
        val config = settings()
        val starts = cycleStarts().map(LocalDate::parse)
        if (starts.isEmpty()) return null
        val intervals =
            starts
                .zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
                .filter { it in 15..90 }
        val length =
            if (intervals.isEmpty()) config.cycleLength else intervals.average().roundToInt()
        return CycleEstimate(
            starts.last().plusDays(length.toLong()).toString(),
            length,
            intervals.isNotEmpty(),
        )
    }
}
