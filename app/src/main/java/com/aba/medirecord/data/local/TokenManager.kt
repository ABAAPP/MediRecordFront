package com.aba.medirecord.data.local

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("medirecord_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_EMAIL = "user_email"

        fun formatBearerHeader(token: String?): String? {
            val trimmed = token?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
                trimmed
            } else {
                "Bearer $trimmed"
            }
        }
    }

    fun saveToken(token: String, email: String? = null) {
        val editor = prefs.edit()
        editor.putString(KEY_JWT_TOKEN, token.trim())
        if (email != null) {
            editor.putString(KEY_USER_EMAIL, email.trim())
        }
        editor.apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_JWT_TOKEN, null)
    }

    fun getAuthorizationHeader(): String? {
        return formatBearerHeader(getToken())
    }

    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
