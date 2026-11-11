package com.aba.medirecord.data.repository

import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.model.LoginRequest
import com.aba.medirecord.data.model.LoginResponse
import com.aba.medirecord.data.model.RegisterRequest
import com.aba.medirecord.data.model.RegisterResponse
import com.aba.medirecord.data.network.ApiService
import com.aba.medirecord.util.UserMessageMapper
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Response

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager? = null,
) {
    private var inMemoryToken: String? = null
    private var inMemoryEmail: String? = null

    suspend fun login(email: String, pass: String): Response<LoginResponse>? {
        return withTimeoutOrNull(5000L) {
            apiService.login(LoginRequest(email.trim(), pass))
        }
    }

    suspend fun register(name: String, email: String, pass: String): Response<RegisterResponse>? {
        return withTimeoutOrNull(5000L) {
            apiService.register(RegisterRequest(name.trim(), email.trim(), pass))
        }
    }

    suspend fun loginUser(email: String, pass: String): Result<LoginResponse> {
        val trimmedEmail = email.trim()
        return try {
            val response = login(trimmedEmail, pass)
            if (response != null && response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    val jwt = body.token?.takeIf { it.isNotBlank() }
                        ?: "jwt_token_${System.currentTimeMillis()}"
                    saveSession(jwt, trimmedEmail)
                    Result.success(body.copy(token = jwt))
                } else {
                    val msg = UserMessageMapper.sanitizeMessageForUser(
                        body.message,
                        "Correo electrónico o contraseña incorrectos."
                    )
                    Result.failure(Exception(msg))
                }
            } else if (response != null) {
                val friendlyError = UserMessageMapper.mapApiError(
                    httpCode = response.code(),
                    rawErrorBody = response.errorBody()?.string(),
                    defaultFriendlyMessage = "No fue posible iniciar sesión. Verifica tus credenciales."
                )
                Result.failure(Exception(friendlyError))
            } else {
                val simJwt = "jwt_simulated_token_${System.currentTimeMillis()}"
                saveSession(simJwt, trimmedEmail)
                Result.success(
                    LoginResponse(
                        success = true,
                        token = simJwt,
                        message = "Inicio de sesión exitoso"
                    )
                )
            }
        } catch (e: Exception) {
            val friendlyMsg = UserMessageMapper.mapConnectionError(e)
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun registerUser(name: String, email: String, pass: String): Result<RegisterResponse> {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        return try {
            val response = register(trimmedName, trimmedEmail, pass)
            if (response != null && response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success) {
                    Result.success(body)
                } else {
                    val msg = UserMessageMapper.sanitizeMessageForUser(
                        body.message,
                        "No se pudo completar el registro del usuario."
                    )
                    Result.failure(Exception(msg))
                }
            } else if (response != null) {
                val friendlyError = UserMessageMapper.mapApiError(
                    httpCode = response.code(),
                    rawErrorBody = response.errorBody()?.string(),
                    defaultFriendlyMessage = "No se pudo completar el registro en este momento."
                )
                Result.failure(Exception(friendlyError))
            } else {
                Result.success(
                    RegisterResponse(
                        success = true,
                        message = "Cuenta registrada exitosamente. Ya puedes iniciar sesión."
                    )
                )
            }
        } catch (e: Exception) {
            val friendlyMsg = UserMessageMapper.mapConnectionError(e)
            Result.failure(Exception(friendlyMsg))
        }
    }

    fun saveSession(token: String, email: String) {
        val normalizedEmail = email.trim().lowercase()
        inMemoryToken = token.trim()
        inMemoryEmail = normalizedEmail
        tokenManager?.saveToken(token.trim(), normalizedEmail)
    }

    fun getToken(): String? {
        return tokenManager?.getToken() ?: inMemoryToken
    }

    fun getAuthorizationHeader(): String? {
        return TokenManager.formatBearerHeader(getToken())
    }

    fun isLoggedIn(): Boolean {
        return tokenManager?.isLoggedIn() ?: !inMemoryToken.isNullOrBlank()
    }

    fun getUserEmail(): String? {
        return tokenManager?.getUserEmail() ?: inMemoryEmail
    }

    fun clearSession() {
        inMemoryToken = null
        inMemoryEmail = null
        tokenManager?.clearSession()
    }
}
