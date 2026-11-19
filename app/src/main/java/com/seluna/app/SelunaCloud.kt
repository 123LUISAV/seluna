package com.seluna.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import java.time.LocalDate
import java.security.MessageDigest
import android.util.Base64
import org.json.JSONObject

class SelunaCloud(context: Context, private val store: SelunaStore) {
    companion object {
        @Volatile private var generation = 0
        private var authPending = false
        private var configuredFirestore: FirebaseFirestore? = null

        @Synchronized
        private fun memoryFirestore(app: FirebaseApp): FirebaseFirestore {
            configuredFirestore?.let {
                return it
            }
            val db = FirebaseFirestore.getInstance(app)
            db.firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            configuredFirestore = db
            return db
        }
    }

    private val app =
        FirebaseApp.getApps(context.applicationContext).firstOrNull {
            it.name == FirebaseApp.DEFAULT_APP_NAME
        }
    private val auth: FirebaseAuth? = app?.let { FirebaseAuth.getInstance(it) }
    private val firestore: FirebaseFirestore? = app?.let { memoryFirestore(it) }
    private val syncState = context.applicationContext.getSharedPreferences(
        "seluna_cloud_sync", Context.MODE_PRIVATE,
    )
    val available: Boolean
        get() = auth != null && firestore != null

    val accountEmail: String?
        get() {
            val local = store.currentUser() ?: return null
            val user = auth?.currentUser ?: return null
            return if (store.cloudLinked(local.id, user.uid)) user.email else null
        }

    enum class SyncResult { CURRENT, UPLOADED, RESTORED, CONFLICT, LOCAL_CHANGED }

    private data class Snapshot(
        val settings: CycleSettings?,
        val starts: List<String>,
        val entries: List<DailyEntry>,
    ) {
        fun isEmpty() = settings == null && starts.isEmpty() && entries.isEmpty()
    }

    val automaticBackup: Boolean
        get() = linkedUid()?.let { syncState.getBoolean("enabled/$it", false) } ?: false

    fun setAutomaticBackup(enabled: Boolean) {
        linkedUid()?.let { syncState.edit().putBoolean("enabled/$it", enabled).apply() }
    }

    private fun linkedUid(): String? {
        val local = store.currentUser() ?: return null
        val uid = auth?.currentUser?.uid ?: return null
        return uid.takeIf { store.cloudLinked(local.id, it) }
    }

    fun needsInitialRestore(): Boolean {
        val local = store.currentUser() ?: return false
        val uid = linkedUid() ?: return false
        return store.requiresFirebaseSession(local.id) &&
            !syncState.contains("fingerprint/$uid") && localSnapshot(local.id).isEmpty()
    }

    fun synchronize(
        restoreOnly: Boolean = false,
        canApply: () -> Boolean = { true },
        onResult: (Result<SyncResult>) -> Unit,
    ) {
        val id = try {
            identity()
        } catch (e: Exception) {
            onResult(Result.failure(e))
            return
        }
        val token = generation
        val session = store.sessionVersion
        val uid = id.second
        val reference = backupDocument(uid)
        val local = localSnapshot(id.first)
        val fingerprintKey = "fingerprint/$uid"
        val revisionKey = "revision/$uid"
        fun assertSession() {
            check(same(id, token, session) && (restoreOnly || automaticBackup)) {
                "La copia automática se interrumpió"
            }
        }
        fun checkpoint(snapshot: Snapshot, updatedAt: Timestamp) {
            syncState.edit().putString(fingerprintKey, fingerprint(snapshot))
                .putString(revisionKey, revision(updatedAt)).apply()
        }
        reference.get(Source.SERVER).addOnCompleteListener { read ->
            try {
                if (!canApply()) {
                    onResult(Result.success(SyncResult.CURRENT))
                    return@addOnCompleteListener
                }
                assertSession()
                if (!read.isSuccessful) throw (read.exception ?: error("No se pudo consultar la copia"))
                val document = read.result
                val remote = document.data?.let(::decode)
                val timestamp = if (remote == null) null else
                    requireNotNull(document.getTimestamp("updatedAt")) { "La copia no tiene una fecha válida" }
                val fingerprint = fingerprint(local)
                val lastFingerprint = syncState.getString(fingerprintKey, null)
                val lastRevision = syncState.getString(revisionKey, null)
                val result = when {
                    local != localSnapshot(id.first) -> SyncResult.LOCAL_CHANGED
                    remote == local && timestamp != null -> {
                        checkpoint(local, timestamp)
                        SyncResult.CURRENT
                    }
                    remote != null && timestamp != null && (
                        local.isEmpty() && lastFingerprint == null ||
                            !restoreOnly && lastFingerprint == fingerprint
                        ) -> {
                        store.replaceFromCloud(id.first, remote.settings, remote.starts, remote.entries)
                        checkpoint(localSnapshot(id.first), timestamp)
                        SyncResult.RESTORED
                    }
                    restoreOnly || remote == null && local.isEmpty() -> SyncResult.CURRENT
                    remote == null && lastRevision == null ||
                        timestamp != null && revision(timestamp) == lastRevision -> null
                    else -> SyncResult.CONFLICT
                }
                if (result != null) {
                    onResult(Result.success(result))
                    return@addOnCompleteListener
                }
                val data = encode(local)
                data["updatedAt"] = FieldValue.serverTimestamp()
                firestore!!.runTransaction { transaction ->
                    assertSession()
                    val latest = transaction.get(reference)
                    if (latest.exists() != document.exists() || latest.getTimestamp("updatedAt") != timestamp)
                        throw FirebaseFirestoreException("La copia cambió", FirebaseFirestoreException.Code.ABORTED)
                    transaction.set(reference, data)
                    Unit
                }.addOnSuccessListener {
                    reference.get(Source.SERVER).addOnCompleteListener { saved ->
                        onResult(runCatching {
                            assertSession()
                            if (!saved.isSuccessful)
                                throw (saved.exception ?: error("No se pudo confirmar la copia"))
                            val current = saved.result
                            if (current.data?.let(::decode) != local) SyncResult.LOCAL_CHANGED
                            else {
                                checkpoint(local, requireNotNull(current.getTimestamp("updatedAt")))
                                if (local == localSnapshot(id.first)) SyncResult.UPLOADED
                                else SyncResult.LOCAL_CHANGED
                            }
                        })
                    }
                }.addOnFailureListener { error ->
                    if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.ABORTED)
                        onResult(Result.success(SyncResult.LOCAL_CHANGED))
                    else onResult(Result.failure(error))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    private fun backupDocument(uid: String) =
        firestore!!.collection("users").document(uid).collection("backups").document("current")

    private fun revision(value: Timestamp) = "${value.seconds}:${value.nanoseconds}"

    private fun localSnapshot(userId: Long) = Snapshot(
        if (store.hasSettings(userId)) store.settings() else null,
        store.cycleStarts(),
        store.entries(),
    )

    private fun fingerprint(snapshot: Snapshot): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(json(encode(snapshot))),
        Base64.NO_WRAP,
    )

    private fun json(data: Map<String, Any>) =
        JSONObject(data).toString().toByteArray(Charsets.UTF_8)

    private fun encode(snapshot: Snapshot): MutableMap<String, Any> {
        val data = mutableMapOf<String, Any>(
            "schemaVersion" to 1,
            "cycleStarts" to snapshot.starts,
            "entries" to snapshot.entries.map {
                mapOf(
                    "date" to it.date,
                    "mood" to it.mood,
                    "symptoms" to it.symptoms,
                    "intensity" to it.intensity,
                    "note" to it.note,
                )
            },
        )
        snapshot.settings?.let {
            data["settings"] = mapOf(
                "lastStart" to it.lastStart,
                "cycleLength" to it.cycleLength,
                "periodLength" to it.periodLength,
                "goal" to it.goal,
                "preferences" to it.preferences,
                "regularity" to it.regularity,
                "contraception" to it.contraception,
            )
        }
        require(json(data).size <= 800_000) { "La copia es demasiado grande" }
        return data
    }

    fun resumeAccount(): Boolean {
        val local = store.currentUser() ?: return false
        if (!store.requiresFirebaseSession(local.id)) return true
        val user = auth?.currentUser
        if (user != null && store.cloudLinked(local.id, user.uid)) return true
        store.logout()
        return false
    }

    fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) {
        val service = auth ?: return onResult(Result.failure(IllegalStateException("Firebase no está disponible")))
        service.sendPasswordResetEmail(email.trim()).addOnCompleteListener { task ->
            if (task.isSuccessful) onResult(Result.success(Unit))
            else onResult(Result.failure(task.exception ?: IllegalStateException("No se pudo enviar el correo")))
        }
    }

    fun signInAccount(
        name: String?,
        email: String,
        password: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val service = auth ?: return onResult(Result.failure(IllegalStateException("Firebase no está disponible")))
        val token = synchronized(SelunaCloud::class.java) {
            if (authPending) null
            else {
                authPending = true
                ++generation
            }
        }
        if (token == null)
            return onResult(Result.failure(IllegalStateException("Ya hay un inicio de sesión en curso")))
        val sessionVersion = store.sessionVersion
        val cleanEmail = email.trim()
        try {
            val task = if (name == null) service.signInWithEmailAndPassword(cleanEmail, password)
                else service.createUserWithEmailAndPassword(cleanEmail, password)
            task.addOnCompleteListener { result ->
                val outcome = synchronized(SelunaCloud::class.java) {
                    val user = if (result.isSuccessful) result.result.user else null
                    val opened = runCatching {
                        check(token == generation && sessionVersion == store.sessionVersion) {
                            "La sesión cambió"
                        }
                        if (!result.isSuccessful)
                            throw (result.exception ?: IllegalStateException("No se pudo iniciar sesión"))
                        check(user != null && user.uid == service.currentUser?.uid) { "Cuenta no disponible" }
                        store.openFirebaseAccount(user.uid, user.email ?: cleanEmail, name ?: user.displayName)
                        if (name != null)
                            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
                        Unit
                    }
                    if (opened.isFailure && user != null && service.currentUser?.uid == user.uid)
                        service.signOut()
                    authPending = false
                    opened
                }
                onResult(outcome)
            }
        } catch (e: Exception) {
            synchronized(SelunaCloud::class.java) { authPending = false }
            onResult(Result.failure(e))
        }
    }

    fun authenticate(
        email: String,
        password: String,
        create: Boolean,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val local = store.currentUser()
        val service = auth
        if (local == null || service == null) {
            onResult(
                Result.failure(IllegalStateException("Inicia sesión local y configura Firebase"))
            )
            return
        }
        if (create && store.hasCloudLink(local.id)) {
            onResult(
                Result.failure(IllegalStateException("Este perfil ya tiene una cuenta vinculada"))
            )
            return
        }
        val token =
            synchronized(SelunaCloud::class.java) {
                if (authPending) null
                else {
                    authPending = true
                    ++generation
                }
            }
        if (token == null) {
            onResult(Result.failure(IllegalStateException("Ya hay un inicio de sesión en curso")))
            return
        }
        val sessionVersion = store.sessionVersion
        try {
            val task =
                if (create) service.createUserWithEmailAndPassword(email.trim(), password)
                else service.signInWithEmailAndPassword(email.trim(), password)
            task.addOnCompleteListener { result ->
                val outcome =
                    synchronized(SelunaCloud::class.java) {
                        val changed =
                            token != generation ||
                                store.sessionVersion != sessionVersion ||
                                store.currentUser()?.id != local.id
                        val uid = if (result.isSuccessful) result.result?.user?.uid else null
                        val answer =
                            when {
                                changed -> {
                                    if (uid != null && service.currentUser?.uid == uid)
                                        try {
                                            service.signOut()
                                        } catch (_: Exception) {}
                                    Result.failure(IllegalStateException("La sesión cambió"))
                                }
                                !result.isSuccessful ->
                                    Result.failure(
                                        result.exception
                                            ?: IllegalStateException("No se pudo iniciar sesión")
                                    )
                                else -> {
                                    val linked = runCatching {
                                        val authenticatedUid = uid ?: error("Cuenta no disponible")
                                        check(service.currentUser?.uid == authenticatedUid) {
                                            "La sesión cambió"
                                        }
                                        store.linkCloud(local.id, authenticatedUid)
                                    }
                                    if (
                                        linked.isFailure &&
                                            uid != null &&
                                            service.currentUser?.uid == uid
                                    )
                                        try {
                                            service.signOut()
                                        } catch (_: Exception) {}
                                    linked
                                }
                            }
                        authPending = false
                        answer
                    }
                onResult(outcome)
            }
        } catch (e: Exception) {
            synchronized(SelunaCloud::class.java) { authPending = false }
            onResult(Result.failure(e))
        }
    }

    fun signOut() {
        synchronized(SelunaCloud::class.java) {
            generation++
            try {
                auth?.signOut()
            } catch (_: Exception) {}
        }
    }

    private fun identity(): Pair<Long, String> {
        val local = store.currentUser() ?: error("Inicia sesión local")
        val uid = auth?.currentUser?.uid ?: error("Inicia sesión en la nube")
        check(store.cloudLinked(local.id, uid)) { "Cuenta no vinculada a este perfil" }
        return local.id to uid
    }

    private fun same(identity: Pair<Long, String>, token: Int, sessionVersion: Long): Boolean =
        store.sessionVersion == sessionVersion &&
            generation == token &&
            store.currentUser()?.id == identity.first &&
            auth?.currentUser?.uid == identity.second &&
            store.cloudLinked(identity.first, identity.second)

    fun backup(onResult: (Result<Unit>) -> Unit) {
        try {
            val id = identity()
            val token = generation
            val sessionVersion = store.sessionVersion
            val data = encode(localSnapshot(id.first))
            check(same(id, token, sessionVersion)) { "La sesión cambió" }
            data["updatedAt"] = FieldValue.serverTimestamp()
            val reference = backupDocument(id.second)
            firestore!!
                .runTransaction { transaction ->
                    transaction.set(reference, data)
                    Unit
                }
                .addOnCompleteListener { task ->
                    val result = runCatching {
                        check(same(id, token, sessionVersion)) { "La sesión cambió" }
                        if (!task.isSuccessful)
                            throw (task.exception
                                ?: IllegalStateException("No se pudo guardar la copia"))
                    }
                    onResult(result)
                }
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    fun restore(onResult: (Result<Int>) -> Unit) {
        try {
            val id = identity()
            val token = generation
            val sessionVersion = store.sessionVersion
            backupDocument(id.second)
                .get(Source.SERVER)
                .addOnCompleteListener { task ->
                    val result = runCatching {
                        check(same(id, token, sessionVersion)) { "La sesión cambió" }
                        if (!task.isSuccessful)
                            throw (task.exception
                                ?: IllegalStateException("No se pudo obtener la copia"))
                        val data = task.result?.data ?: error("No hay copia guardada")
                        val snapshot = decode(data)
                        check(same(id, token, sessionVersion)) { "La sesión cambió" }
                        store.mergeCloud(id.first, snapshot.settings, snapshot.starts, snapshot.entries)
                    }
                    onResult(result)
                }
        } catch (e: Exception) {
            onResult(Result.failure(e))
        }
    }

    private fun decode(data: Map<String, Any>): Snapshot {
        require((data["schemaVersion"] as? Number)?.toLong() == 1L) {
            "Versión de copia incompatible"
        }
        val checkSize = data.filterKeys { it != "updatedAt" }
        require(json(checkSize).size <= 800_000) {
            "La copia es demasiado grande"
        }
        val settings =
            (data["settings"] as? Map<*, *>)?.let { m ->
                val last = string(m, "lastStart", 10)
                if (last.isNotEmpty()) date(last)
                val cycle = number(m, "cycleLength")
                val period = number(m, "periodLength")
                require(cycle in 15..90 && period in 1..cycle)
                CycleSettings(
                    last,
                    cycle,
                    period,
                    string(m, "goal", 120),
                    string(m, "preferences", 300),
                    if (m.containsKey("regularity")) string(m, "regularity", 100) else "",
                    if (m.containsKey("contraception")) string(m, "contraception", 100) else "",
                )
            }
        require(data["settings"] == null || settings != null)
        val starts =
            (data["cycleStarts"] as? List<*>)?.map {
                (it as? String ?: error("Fecha inválida")).also(::date)
            } ?: error("Copia inválida")
        val entries =
            (data["entries"] as? List<*>)?.map { item ->
                val m = item as? Map<*, *> ?: error("Registro inválido")
                val d = string(m, "date", 10).also(::date)
                val mood = string(m, "mood", 80)
                val intensity = number(m, "intensity")
                require(mood.isNotBlank() && intensity in 1..5)
                DailyEntry(
                    d,
                    mood,
                    string(m, "symptoms", 300),
                    intensity,
                    string(m, "note", 1500),
                )
            } ?: error("Copia inválida")
        require(
            starts.distinct().size == starts.size &&
                entries.map { it.date }.distinct().size == entries.size
        ) {
            "Fechas duplicadas"
        }
        val normalizedStarts = (starts + listOfNotNull(settings?.lastStart?.ifBlank { null }))
            .distinct().sorted()
        return Snapshot(settings, normalizedStarts, entries.sortedByDescending { it.date })
    }

    private fun string(map: Map<*, *>, key: String, limit: Int): String =
        (map[key] as? String ?: error("Campo inválido: $key")).also { require(it.length <= limit) }

    private fun number(map: Map<*, *>, key: String): Int {
        val n = map[key] as? Long ?: error("Campo inválido: $key")
        require(n in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
        return n.toInt()
    }

    private fun date(value: String) {
        val parsed = LocalDate.parse(value)
        require(parsed.toString() == value && !parsed.isAfter(LocalDate.now())) { "Fecha inválida" }
    }
}
