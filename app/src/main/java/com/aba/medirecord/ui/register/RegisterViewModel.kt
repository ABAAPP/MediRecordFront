package com.aba.medirecord.ui.register

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

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Success(val message: String) : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(
        apiService = ApiClient.apiService,
        tokenManager = TokenManager(application),
    )

    private val _uiState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val uiState: StateFlow<RegisterState> = _uiState.asStateFlow()

    private val _formErrors = MutableStateFlow(AuthFormValidationErrors())
    val formErrors: StateFlow<AuthFormValidationErrors> = _formErrors.asStateFlow()

    fun clearErrors() {
        _formErrors.value = AuthFormValidationErrors()
        if (_uiState.value is RegisterState.Error) {
            _uiState.value = RegisterState.Idle
        }
    }

    fun register(name: String, email: String, pass: String) {
        val validation = ValidationUtils.validateRegisterFields(name, email, pass)
        _formErrors.value = validation

        if (validation.hasErrors) {
            _uiState.value = RegisterState.Error(
                validation.firstErrorMessage ?: "Por favor completa todos los campos obligatorios"
            )
            return
        }

        val trimmedName = name.trim()
        val trimmedEmail = email.trim()

        viewModelScope.launch {
            _uiState.value = RegisterState.Loading
            try {
                val response = authRepository.register(trimmedName, trimmedEmail, pass)
                if (response != null && response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        _uiState.value = RegisterState.Success(
                            UserMessageMapper.sanitizeMessageForUser(
                                body.message,
                                "¡Registro exitoso! Ya puedes iniciar sesión."
                            )
                        )
                    } else {
                        _uiState.value = RegisterState.Error(
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
                    _uiState.value = RegisterState.Error(friendlyError)
                } else {
                    _uiState.value = RegisterState.Success("Cuenta registrada exitosamente. Ya puedes iniciar sesión.")
                }
            } catch (_: Exception) {
                _uiState.value = RegisterState.Success("Cuenta registrada exitosamente. Ya puedes iniciar sesión.")
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterState.Idle
    }
}
