package com.aba.medirecord.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ReminderFilter {
    ALL,
    PENDING,
    COMPLETED
}

sealed class ReminderActionFeedback {
    data class Success(val message: String) : ReminderActionFeedback()
    data class Error(val message: String) : ReminderActionFeedback()
}

sealed class RemindersUiState {
    object Loading : RemindersUiState()
    data class Success(
        val allReminders: List<MedicationReminder>,
        val displayedReminders: List<MedicationReminder>,
        val selectedFilter: ReminderFilter,
        val userEmail: String,
    ) : RemindersUiState()
    data class Empty(val userEmail: String) : RemindersUiState()
    data class Error(val message: String) : RemindersUiState()
}

class RemindersViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MedicationRepository = MedicationRepository(ApiClient.apiService),
) : AndroidViewModel(application) {

    companion object {
        fun getTimePeriodLabel(scheduledTime: String): String {
            val minutes = MedicationRepository.parseTimeToMinutes(scheduledTime)
            return when {
                minutes < 12 * 60 -> "Mañana"
                minutes < 19 * 60 -> "Tarde"
                else -> "Noche"
            }
        }

        fun applyFilter(
            reminders: List<MedicationReminder>,
            filter: ReminderFilter,
        ): List<MedicationReminder> {
            val sorted = MedicationRepository.sortRemindersChronologically(reminders)
            return when (filter) {
                ReminderFilter.ALL -> sorted
                ReminderFilter.PENDING -> sorted.filter { !it.isCompleted }
                ReminderFilter.COMPLETED -> sorted.filter { it.isCompleted }
            }
        }
    }

    private val tokenManager = TokenManager(application)

    private val _uiState = MutableStateFlow<RemindersUiState>(RemindersUiState.Loading)
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    private val _completingReminderId = MutableStateFlow<String?>(null)
    val completingReminderId: StateFlow<String?> = _completingReminderId.asStateFlow()

    private val _actionFeedback = MutableStateFlow<ReminderActionFeedback?>(null)
    val actionFeedback: StateFlow<ReminderActionFeedback?> = _actionFeedback.asStateFlow()

    private var currentFilter: ReminderFilter = ReminderFilter.ALL
    private var cachedReminders: List<MedicationReminder> = emptyList()
    private var currentEmail: String = ""

    fun loadReminders(userEmail: String? = null) {
        val resolvedEmail = userEmail?.takeIf { it.isNotBlank() }
            ?: tokenManager.getUserEmail()?.takeIf { it.isNotBlank() }
            ?: if (tokenManager.isLoggedIn()) "usuario@medirecord.com" else ""

        currentEmail = resolvedEmail

        viewModelScope.launch {
            _uiState.value = RemindersUiState.Loading
            repository.getRemindersForUser(resolvedEmail)
                .onSuccess { reminders ->
                    cachedReminders = reminders
                    if (reminders.isEmpty()) {
                        _uiState.value = RemindersUiState.Empty(resolvedEmail)
                    } else {
                        _uiState.value = RemindersUiState.Success(
                            allReminders = reminders,
                            displayedReminders = applyFilter(reminders, currentFilter),
                            selectedFilter = currentFilter,
                            userEmail = resolvedEmail
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = RemindersUiState.Error(
                        error.message ?: "No se pudieron cargar los recordatorios del usuario"
                    )
                }
        }
    }

    fun setFilter(filter: ReminderFilter) {
        currentFilter = filter
        if (cachedReminders.isNotEmpty()) {
            _uiState.value = RemindersUiState.Success(
                allReminders = cachedReminders,
                displayedReminders = applyFilter(cachedReminders, currentFilter),
                selectedFilter = currentFilter,
                userEmail = currentEmail
            )
        }
    }

    fun markReminderAsCompleted(reminderId: String) {
        viewModelScope.launch {
            _completingReminderId.value = reminderId
            _actionFeedback.value = null

            repository.markReminderAsCompleted(reminderId, currentEmail)
                .onSuccess { response ->
                    // Actualizar lista en memoria para reflejar inmediatamente el estado "Realizado"
                    cachedReminders = cachedReminders.map { item ->
                        if (item.id == reminderId || item.medicationId == reminderId) {
                            item.copy(
                                isCompleted = true,
                                completedAt = response.completedAt ?: item.completedAt ?: "Recién tomado"
                            )
                        } else {
                            item
                        }
                    }

                    // Volver a consultar al repositorio para asegurar sincronización persistente
                    repository.getRemindersForUser(currentEmail)
                        .onSuccess { refreshed ->
                            cachedReminders = refreshed
                        }

                    _uiState.value = RemindersUiState.Success(
                        allReminders = cachedReminders,
                        displayedReminders = applyFilter(cachedReminders, currentFilter),
                        selectedFilter = currentFilter,
                        userEmail = currentEmail
                    )

                    _actionFeedback.value = ReminderActionFeedback.Success(
                        response.message.ifBlank { "Recordatorio marcado como realizado correctamente" }
                    )
                    _completingReminderId.value = null
                }
                .onFailure { error ->
                    _actionFeedback.value = ReminderActionFeedback.Error(
                        error.message?.ifBlank { "No se pudo marcar el recordatorio como realizado. Intenta de nuevo." }
                            ?: "No se pudo marcar el recordatorio como realizado. Intenta de nuevo."
                    )
                    _completingReminderId.value = null
                }
        }
    }

    fun toggleReminderStatus(reminderId: String) {
        val target = cachedReminders.find { it.id == reminderId }
        if (target != null && !target.isCompleted) {
            markReminderAsCompleted(reminderId)
            return
        }
        viewModelScope.launch {
            repository.toggleReminderStatus(reminderId, currentEmail)
                .onSuccess { updatedReminders ->
                    cachedReminders = updatedReminders
                    if (updatedReminders.isEmpty()) {
                        _uiState.value = RemindersUiState.Empty(currentEmail)
                    } else {
                        _uiState.value = RemindersUiState.Success(
                            allReminders = updatedReminders,
                            displayedReminders = applyFilter(updatedReminders, currentFilter),
                            selectedFilter = currentFilter,
                            userEmail = currentEmail
                        )
                    }
                }
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }
}
