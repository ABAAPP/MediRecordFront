package com.aba.medirecord.data.repository

import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.model.LoginRequest
import com.aba.medirecord.data.model.LoginResponse
import com.aba.medirecord.data.model.RegisterRequest
import com.aba.medirecord.data.model.RegisterResponse
import com.aba.medirecord.data.network.ApiService
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
) {
    suspend fun login(email: String, pass: String): Response<LoginResponse>? {
        return withTimeoutOrNull(1500L) {
            apiService.login(LoginRequest(email, pass))
        }
    }

    suspend fun register(name: String, email: String, pass: String): Response<RegisterResponse>? {
        return withTimeoutOrNull(1500L) {
            apiService.register(RegisterRequest(name, email, pass))
        }
    }

    fun saveSession(token: String, email: String) {
        tokenManager.saveToken(token, email)
    }

    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    fun getUserEmail(): String? {
        return tokenManager.getUserEmail()
    }

    fun clearSession() {
        tokenManager.clearSession()
    }
}
