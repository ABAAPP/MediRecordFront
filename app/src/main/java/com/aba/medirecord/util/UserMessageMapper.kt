package com.aba.medirecord.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

object UserMessageMapper {

    private val TECHNICAL_PATTERNS = listOf(
        Regex("\\(\\d{3}\\)"), // Ej. (500), (404)
        Regex("HTTP\\s*\\d{3}", RegexOption.IGNORE_CASE),
        Regex("\\b(Exception|RuntimeException|NullPointerException|SocketTimeoutException|ConnectException|UnknownHostException|IOException|SQLException|JsonSyntaxException)\\b", RegexOption.IGNORE_CASE),
        Regex("https?://\\S+", RegexOption.IGNORE_CASE),
        Regex("<[^>]+>"), // Etiquetas HTML
        Regex("\\{[\\s\\S]*\\}"), // Bloques JSON crudos
        Regex("at\\s+[a-zA-Z0-9_.]+\\([^)]*\\)") // Líneas de stacktrace
    )

    /**
     * Limpia cualquier detalle técnico (códigos HTTP, nombres de excepciones, JSON crudo, URLs o trazas)
     * para garantizar que el mensaje sea 100% comprensible para el usuario final.
     */
    fun sanitizeMessageForUser(
        rawMessage: String?,
        fallbackMessage: String = "No se pudo completar la operación en este momento. Por favor intenta nuevamente.",
    ): String {
        if (rawMessage.isNullOrBlank()) {
            return fallbackMessage
        }

        var cleaned = rawMessage.trim()

        // Si parece un JSON con campo "message" o "mensaje", intentar extraer solo el texto amigable
        val jsonMsgMatch = Regex("\"(?:message|mensaje|detail)\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(cleaned)
        if (jsonMsgMatch != null) {
            cleaned = jsonMsgMatch.groupValues[1].trim()
        }

        // Eliminar patrones técnicos
        TECHNICAL_PATTERNS.forEach { pattern ->
            cleaned = pattern.replace(cleaned, "").trim()
        }

        // Limpiar espacios múltiples
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()

        // Si después de limpiar quedó vacío o contiene palabras técnicas en inglés de bajo nivel
        if (cleaned.length < 4 ||
            cleaned.contains("stacktrace", ignoreCase = true) ||
            cleaned.contains("internal server error", ignoreCase = true) ||
            cleaned.contains("bad gateway", ignoreCase = true) ||
            cleaned.contains("failed to connect", ignoreCase = true)
        ) {
            return fallbackMessage
        }

        return cleaned
    }

    /**
     * Convierte errores de red o desconexión en mensajes claros y amigables sin bloquear la aplicación.
     */
    fun mapConnectionError(throwable: Throwable?): String {
        return when (throwable) {
            is SocketTimeoutException ->
                "El servidor tardó demasiado en responder. Verifica tu conexión e inténtalo de nuevo."
            is UnknownHostException, is ConnectException ->
                "No fue posible conectar con el servidor. Revisa tu conexión a internet e inténtalo más tarde."
            is IOException ->
                "Ocurrió un problema de conexión al comunicarte con el servicio. Intenta nuevamente."
            else ->
                "No se pudo establecer comunicación con el servidor. Verifica tu conexión e inténtalo nuevamente."
        }
    }

    /**
     * Traduce códigos de estado HTTP y cuerpos de error de la API en mensajes comprensibles para el usuario.
     */
    fun mapApiError(
        httpCode: Int,
        rawErrorBody: String? = null,
        defaultFriendlyMessage: String = "No se pudo completar la operación solicitada. Por favor intenta nuevamente.",
    ): String {
        // Si el cuerpo tiene un mensaje amigable en español (no técnico), lo sanitizamos y usamos
        if (!rawErrorBody.isNullOrBlank()) {
            val sanitizedBody = sanitizeMessageForUser(rawErrorBody, "")
            if (sanitizedBody.isNotBlank() && !isGenericTechnicalPhrase(sanitizedBody)) {
                return sanitizedBody
            }
        }

        return when (httpCode) {
            400, 422 -> "Algunos de los datos ingresados no son válidos. Por favor revisa la información e inténtalo de nuevo."
            401 -> "Correo electrónico o contraseña incorrectos. Por favor verifica tus datos."
            403 -> "No tienes permiso para realizar esta acción en este momento."
            404 -> "No se encontró la información solicitada en el sistema."
            409 -> "Ya existe un registro con esta información. Por favor verifica los datos."
            in 500..599 -> "No se pudo completar la operación porque el servicio no está disponible temporalmente. Intenta más tarde."
            else -> defaultFriendlyMessage
        }
    }

    private fun isGenericTechnicalPhrase(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "error" ||
                lower == "null" ||
                lower.startsWith("java.") ||
                lower.startsWith("kotlin.") ||
                lower.startsWith("org.")
    }
}
