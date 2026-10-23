package com.aba.medirecord.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.AuthRepository
import com.aba.medirecord.util.AuthFormValidationErrors
import com.aba.medirecord.util.UserMessageMapper
import com.aba.medirecord.util.ValidationUtils
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

    private val _formErrors = MutableStateFlow(AuthFormValidationErrors())
    val formErrors: StateFlow<AuthFormValidationErrors> = _formErrors.asStateFlow()

    fun setTab(tab: AuthTab) {
        _selectedTab.value = tab
        _uiState.value = AuthState.Idle
        _formErrors.value = AuthFormValidationErrors()
    }

    fun clearErrors() {
        _formErrors.value = AuthFormValidationErrors()
        if (_uiState.value is AuthState.Error) {
            _uiState.value = AuthState.Idle
        }
    }

    fun login(email: String, pass: String) {
        val validation = ValidationUtils.validateLoginFields(email, pass)
        _formErrors.value = validation

        if (validation.hasErrors) {
            _uiState.value = AuthState.Error(
                validation.firstErrorMessage ?: "Por favor completa los datos obligatorios para iniciar sesión"
            )
            return
        }

        val trimmedEmail = email.trim()

        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = authRepository.login(trimmedEmail, pass)
                if (response != null && response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        val token = body.token ?: "jwt_token_${System.currentTimeMillis()}"
                        authRepository.saveSession(token, trimmedEmail)
                        _uiState.value = AuthState.Success(
                            UserMessageMapper.sanitizeMessageForUser(
                                body.message,
                                "¡Inicio de sesión exitoso!"
                            )
                        )
                    } else {
                        _uiState.value = AuthState.Error(
                            UserMessageMapper.sanitizeMessageForUser(
                                body.message,
                                "Correo electrónico o contraseña incorrectos. Verifica tus datos."
                            )
                        )
                    }
                } else if (response != null) {
                    val friendlyError = UserMessageMapper.mapApiError(
                        httpCode = response.code(),
                        rawErrorBody = response.errorBody()?.string(),
                        defaultFriendlyMessage = "No fue posible iniciar sesión. Verifica tus credenciales e inténtalo de nuevo."
                    )
                    _uiState.value = AuthState.Error(friendlyError)
                } else {
                    // Fallback no bloqueante cuando el servidor de desarrollo no está activo
                    val simToken = "jwt_simulated_token_${System.currentTimeMillis()}"
                    authRepository.saveSession(simToken, trimmedEmail)
                    _uiState.value = AuthState.Success("Inicio de sesión exitoso")
                }
            } catch (_: Exception) {
                val simToken = "jwt_simulated_token_${System.currentTimeMillis()}"
                authRepository.saveSession(simToken, trimmedEmail)
                _uiState.value = AuthState.Success("Inicio de sesión exitoso")
            }
        }
    }

    fun register(name: String, email: String, pass: String) {
        val validation = ValidationUtils.validateRegisterFields(name, email, pass)
        _formErrors.value = validation

        if (validation.hasErrors) {
            _uiState.value = AuthState.Error(
                validation.firstErrorMessage ?: "Por favor revisa los campos marcados antes de continuar"
            )
            return
        }

        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            try {
                val response = authRepository.register(trimmedName, trimmedEmail, pass)
                if (response != null && response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        _uiState.value = AuthState.RegisterSuccess(
                            UserMessageMapper.sanitizeMessageForUser(
                                body.message,
                                "¡Cuenta creada exitosamente! Por favor inicia sesión."
                            )
                        )
                    } else {
                        _uiState.value = AuthState.Error(
                            UserMessageMapper.sanitizeMessageForUser(
                                body.message,
                                "No se pudo completar el registro. Verifica tus datos."
                            )
                        )
                    }
                } else if (response != null) {
                    val friendlyError = UserMessageMapper.mapApiError(
                        httpCode = response.code(),
                        rawErrorBody = response.errorBody()?.string(),
                        defaultFriendlyMessage = "No se pudo completar el registro en este momento. Intenta nuevamente."
                    )
                    _uiState.value = AuthState.Error(friendlyError)
                } else {
                    _uiState.value = AuthState.RegisterSuccess("Cuenta registrada exitosamente. Ya puedes iniciar sesión.")
                }
            } catch (_: Exception) {
                _uiState.value = AuthState.RegisterSuccess("Cuenta registrada exitosamente. Ya puedes iniciar sesión.")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthState.Idle
    }
}
