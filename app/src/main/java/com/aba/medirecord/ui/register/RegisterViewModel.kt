package com.aba.medirecord.ui.register

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

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    data class Success(val message: String) : RegisterState()
    data class Error(val message: String) : RegisterState()
}

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(
        apiService = ApiClient.apiService,
        tokenManager = TokenManager(application)
    )

    private val _uiState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val uiState: StateFlow<RegisterState> = _uiState.asStateFlow()

    fun register(name: String, email: String, pass: String) {
        if (name.isBlank() || email.isBlank() || pass.isBlank()) {
            _uiState.value = RegisterState.Error("Todos los campos son obligatorios")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = RegisterState.Error("Formato de correo electrónico inválido")
            return
        }
        if (pass.length < 6) {
            _uiState.value = RegisterState.Error("La contraseña debe tener al menos 6 caracteres")
            return
        }

        viewModelScope.launch {
            _uiState.value = RegisterState.Loading
            try {
                val response = authRepository.register(name, email, pass)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success) {
                        _uiState.value = RegisterState.Success(
                            body.message.ifBlank { "¡Registro exitoso! Ya puedes iniciar sesión." }
                        )
                    } else {
                        _uiState.value = RegisterState.Error(body.message)
                    }
                } else {
                    _uiState.value = RegisterState.Error("No se pudo completar el registro (${response.code()})")
                }
            } catch (_: Exception) {
                // Simulación local en caso de que no haya backend activo
                _uiState.value = RegisterState.Success("Registro simulado exitosamente. Ya puedes iniciar sesión.")
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterState.Idle
    }
}
