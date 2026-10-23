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

enum class TodayDoseFilter {
    ALL,
    PENDING,
    COMPLETED
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val doses: List<MedicationDose>,
        val displayedDoses: List<MedicationDose> = doses,
        val selectedFilter: TodayDoseFilter = TodayDoseFilter.ALL,
        val queriedDate: String = MedicationRepository.getCurrentDateIso(),
    ) : HomeUiState()
    data class Empty(
        val queriedDate: String = MedicationRepository.getCurrentDateIso(),
    ) : HomeUiState()
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

    private var cachedDoses: List<MedicationDose> = emptyList()
    private var currentFilter: TodayDoseFilter = TodayDoseFilter.ALL
    private var lastQueriedDate: String = MedicationRepository.getCurrentDateIso()

    init {
        loadTodayMedications()
    }

    fun loadTodayMedications(
        date: String = MedicationRepository.getCurrentDateIso(),
        userEmail: String? = tokenManager.getUserEmail(),
    ) {
        lastQueriedDate = date
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            repository.getTodayMedications(date = date, userEmail = userEmail)
                .onSuccess { doses ->
                    cachedDoses = doses
                    if (doses.isEmpty()) {
                        _uiState.value = HomeUiState.Empty(queriedDate = date)
                    } else {
                        _uiState.value = HomeUiState.Success(
                            doses = doses,
                            displayedDoses = applyDoseFilter(doses, currentFilter),
                            selectedFilter = currentFilter,
                            queriedDate = date
                        )
                    }
                }
                .onFailure {
                    _uiState.value = HomeUiState.Error("No se pudieron cargar los medicamentos del día ($date)")
                }
        }
    }

    fun setFilter(filter: TodayDoseFilter) {
        currentFilter = filter
        if (cachedDoses.isNotEmpty()) {
            _uiState.value = HomeUiState.Success(
                doses = cachedDoses,
                displayedDoses = applyDoseFilter(cachedDoses, currentFilter),
                selectedFilter = currentFilter,
                queriedDate = lastQueriedDate
            )
        }
    }

    private fun applyDoseFilter(
        doses: List<MedicationDose>,
        filter: TodayDoseFilter,
    ): List<MedicationDose> {
        val sorted = MedicationRepository.sortDosesChronologically(doses)
        return when (filter) {
            TodayDoseFilter.ALL -> sorted
            TodayDoseFilter.PENDING -> sorted.filter { !it.isCompleted }
            TodayDoseFilter.COMPLETED -> sorted.filter { it.isCompleted }
        }
    }

    fun markDoseAsCompleted(doseId: String) {
        viewModelScope.launch {
            _actionMessage.value = null
            val email = tokenManager.getUserEmail()
            repository.markReminderAsCompleted(doseId, email)
                .onSuccess { response ->
                    repository.getTodayMedications(date = lastQueriedDate, userEmail = email)
                        .onSuccess { updatedDoses ->
                            cachedDoses = updatedDoses
                            if (updatedDoses.isEmpty()) {
                                _uiState.value = HomeUiState.Empty(queriedDate = lastQueriedDate)
                            } else {
                                _uiState.value = HomeUiState.Success(
                                    doses = updatedDoses,
                                    displayedDoses = applyDoseFilter(updatedDoses, currentFilter),
                                    selectedFilter = currentFilter,
                                    queriedDate = lastQueriedDate
                                )
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
        val target = cachedDoses.find { it.id == doseId }
        if (target != null && !target.isCompleted) {
            markDoseAsCompleted(doseId)
            return
        }
        viewModelScope.launch {
            repository.toggleDoseStatus(doseId)
                .onSuccess { updatedDoses ->
                    cachedDoses = updatedDoses
                    if (updatedDoses.isEmpty()) {
                        _uiState.value = HomeUiState.Empty(queriedDate = lastQueriedDate)
                    } else {
                        _uiState.value = HomeUiState.Success(
                            doses = updatedDoses,
                            displayedDoses = applyDoseFilter(updatedDoses, currentFilter),
                            selectedFilter = currentFilter,
                            queriedDate = lastQueriedDate
                        )
                    }
                }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun clearAllForEmptyTest() {
        cachedDoses = emptyList()
        _uiState.value = HomeUiState.Empty(queriedDate = lastQueriedDate)
    }
}
