package com.seluna.app

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

object ChatClient {
    fun reply(messages: List<Pair<String, String>>): String {
        if (BuildConfig.ASSISTANT_URL.isBlank()) {
            throw IOException("El asistente no está configurado en esta versión.")
        }
        val connection =
            URI(BuildConfig.ASSISTANT_URL).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5_000
            connection.readTimeout = 40_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val history = JSONArray()
            for ((role, content) in messages.takeLast(9)) {
                history.put(JSONObject().put("role", role).put("content", content.take(1_500)))
            }
            val payload = JSONObject().put("messages", history).toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            when (connection.responseCode) {
                200 -> {
                    val result =
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    JSONObject(result).getString("reply").ifBlank {
                        throw IOException("La respuesta llegó vacía.")
                    }
                }
                429 ->
                    throw IOException(
                        "Hay una consulta en curso. Espera un momento e inténtalo de nuevo."
                    )
                503 -> throw IOException("El asistente está pendiente de configuración o servicio.")
                else -> throw IOException("El asistente no pudo responder. Inténtalo de nuevo.")
            }
        } catch (_: java.net.ConnectException) {
            throw IOException(
                "El asistente está desconectado. Inténtalo cuando vuelva a estar disponible."
            )
        } catch (_: java.net.SocketTimeoutException) {
            throw IOException("El asistente tardó demasiado. Puedes volver a intentarlo.")
        } finally {
            connection.disconnect()
        }
    }
}
