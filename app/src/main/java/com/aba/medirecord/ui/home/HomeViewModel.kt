package com.aba.medirecord.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MedicationRepository(ApiClient.apiService)

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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

    fun toggleDoseStatus(doseId: String) {
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

    fun clearAllForEmptyTest() {
        _uiState.value = HomeUiState.Empty
    }
}
