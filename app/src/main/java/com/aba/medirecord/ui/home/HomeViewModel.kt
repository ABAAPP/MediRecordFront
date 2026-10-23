package com.aba.medirecord.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val doses: List<MedicationDose>) : HomeUiState()
    object Empty : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

sealed class HomeActionMessage {
    data class Success(val text: String) : HomeActionMessage()
    data class Error(val text: String) : HomeActionMessage()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicationRepository(ApiClient.apiService)
    private val tokenManager = TokenManager(application)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<HomeActionMessage?>(null)
    val actionMessage: StateFlow<HomeActionMessage?> = _actionMessage.asStateFlow()

    init {
        loadTodayMedications()
    }

    fun loadTodayMedications() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            repository.getTodayMedications()
                .onSuccess { doses ->
                    if (doses.isEmpty()) {
                        _uiState.value = HomeUiState.Empty
                    } else {
                        _uiState.value = HomeUiState.Success(doses)
                    }
                }
                .onFailure {
                    _uiState.value = HomeUiState.Error("No se pudieron cargar los medicamentos del día")
                }
        }
    }

    fun markDoseAsCompleted(doseId: String) {
        viewModelScope.launch {
            _actionMessage.value = null
            repository.markReminderAsCompleted(doseId, tokenManager.getUserEmail())
                .onSuccess { response ->
                    repository.getTodayMedications()
                        .onSuccess { updatedDoses ->
                            if (updatedDoses.isEmpty()) {
                                _uiState.value = HomeUiState.Empty
                            } else {
                                _uiState.value = HomeUiState.Success(updatedDoses)
                            }
                        }
                    _actionMessage.value = HomeActionMessage.Success(
                        response.message.ifBlank { "Recordatorio marcado como realizado" }
                    )
                }
                .onFailure { error ->
                    _actionMessage.value = HomeActionMessage.Error(
                        error.message ?: "No se pudo marcar el recordatorio como realizado"
                    )
                }
        }
    }

    fun toggleDoseStatus(doseId: String) {
        val currentDoses = (_uiState.value as? HomeUiState.Success)?.doses.orEmpty()
        val target = currentDoses.find { it.id == doseId }
        if (target != null && !target.isCompleted) {
            markDoseAsCompleted(doseId)
            return
        }
        viewModelScope.launch {
            repository.toggleDoseStatus(doseId)
                .onSuccess { updatedDoses ->
                    if (updatedDoses.isEmpty()) {
                        _uiState.value = HomeUiState.Empty
                    } else {
                        _uiState.value = HomeUiState.Success(updatedDoses)
                    }
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearAllForEmptyTest() {
        _uiState.value = HomeUiState.Empty
    }
}
