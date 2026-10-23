package com.aba.medirecord.util

data class AuthFormValidationErrors(
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
) {
    val hasErrors: Boolean
        get() = nameError != null || emailError != null || passwordError != null

    val firstErrorMessage: String?
        get() = nameError ?: emailError ?: passwordError
}

object ValidationUtils {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val TIME_REGEX = Regex("^(\\d{1,2}):(\\d{2})(?:\\s?(AM|PM|am|pm))?$")
    private val DATE_REGEX = Regex("^\\d{2}/\\d{2}/\\d{4}$")

    fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email.trim())
    }

    /**
     * Valida tanto la estructura como los rangos reales de hora (1..12 con AM/PM o 0..23 en 24h)
     * y minutos (0..59). (Corrección de defecto DEF-01).
     */
    fun isValidTimeFormat(time: String): Boolean {
        val match = TIME_REGEX.matchEntire(time.trim()) ?: return false
        val hour = match.groupValues[1].toIntOrNull() ?: return false
        val minute = match.groupValues[2].toIntOrNull() ?: return false
        val amPm = match.groupValues[3]

        if (minute !in 0..59) return false

        return if (amPm.isNotEmpty()) {
            hour in 1..12
        } else {
            hour in 0..23
        }
    }

    fun isValidDateFormat(date: String): Boolean {
        return DATE_REGEX.matches(date.trim())
    }

    fun validateLoginFields(email: String, password: String): AuthFormValidationErrors {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        var emailErr: String? = null
        var passErr: String? = null

        if (trimmedEmail.isEmpty()) {
            emailErr = "Por favor ingresa tu correo electrónico"
        } else if (!isValidEmail(trimmedEmail)) {
            emailErr = "El formato del correo electrónico no es válido (ejemplo: nombre@correo.com)"
        }

        if (trimmedPassword.isEmpty()) {
            passErr = "Por favor ingresa tu contraseña"
        }

        return AuthFormValidationErrors(
            emailError = emailErr,
            passwordError = passErr
        )
    }

    fun validateRegisterFields(
        name: String,
        email: String,
        password: String,
    ): AuthFormValidationErrors {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()

        var nameErr: String? = null
        var emailErr: String? = null
        var passErr: String? = null

        if (trimmedName.isEmpty()) {
            nameErr = "Por favor ingresa tu nombre completo"
        } else if (trimmedName.length < 3) {
            nameErr = "El nombre debe tener al menos 3 caracteres"
        }

        if (trimmedEmail.isEmpty()) {
            emailErr = "Por favor ingresa tu correo electrónico"
        } else if (!isValidEmail(trimmedEmail)) {
            emailErr = "El formato del correo electrónico no es válido (ejemplo: nombre@correo.com)"
        }

        if (trimmedPassword.isEmpty()) {
            passErr = "Por favor ingresa una contraseña"
        } else if (trimmedPassword.length < 6) {
            passErr = "La contraseña debe tener al menos 6 caracteres"
        }

        return AuthFormValidationErrors(
            nameError = nameErr,
            emailError = emailErr,
            passwordError = passErr
        )
    }
}
