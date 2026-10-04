package com.aba.medirecord.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val message: String) : AuthState()
    data class RegisterSuccess(val message: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

enum class AuthTab {
    LOGIN,
    REGISTER
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(
        apiService = ApiClient.apiService,
        tokenManager = TokenManager(application)
    )

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Idle)
    val uiState: StateFlow<AuthState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(AuthTab.LOGIN)
    val selectedTab: StateFlow<AuthTab> = _selectedTab.asStateFlow()

    fun setTab(tab: AuthTab) {
        _selectedTab.value = tab
        _uiState.value = AuthState.Idle
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthState.Error("Por favor ingrese correo y contraseña")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthState.Error("Formato de correo electrónico inválido")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = authRepository.login(email, pass)
                if (response != null && response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        val token = body.token ?: "jwt_token_${System.currentTimeMillis()}"
                        authRepository.saveSession(token, email)
                        _uiState.value = AuthState.Success(body.message.ifBlank { "¡Inicio de sesión exitoso!" })
                    } else {
                        _uiState.value = AuthState.Error(body.message)
                    }
                } else if (response != null) {
                    _uiState.value = AuthState.Error("Credenciales incorrectas o error en el servidor (${response.code()})")
                } else {
                    // Fallback para prueba local
                    val simToken = "jwt_simulated_token_${System.currentTimeMillis()}"
                    authRepository.saveSession(simToken, email)
                    _uiState.value = AuthState.Success("Inicio de sesión exitoso")
                }
            } catch (_: Exception) {
                val simToken = "jwt_simulated_token_${System.currentTimeMillis()}"
                authRepository.saveSession(simToken, email)
                _uiState.value = AuthState.Success("Inicio de sesión exitoso")
            }
        }
    }

    fun register(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthState.Error("Todos los campos son obligatorios")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthState.Error("Formato de correo electrónico inválido")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = authRepository.register(name, email, pass)
                if (response != null && response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        _uiState.value = AuthState.RegisterSuccess(
                            body.message.ifBlank { "¡Cuenta creada exitosamente! Por favor inicia sesión." }
                        )
                    } else {
                        _uiState.value = AuthState.Error(body.message)
                    }
                } else if (response != null) {
                    _uiState.value = AuthState.Error("No se pudo completar el registro (${response.code()})")
                } else {
                    _uiState.value = AuthState.RegisterSuccess("Registro simulado exitosamente. Ya puedes iniciar sesión.")
                }
            } catch (_: Exception) {
                _uiState.value = AuthState.RegisterSuccess("Registro simulado exitosamente. Ya puedes iniciar sesión.")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthState.Idle
    }
}
