package com.aba.medirecord.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.notifications.ReminderNotificationManager
import com.aba.medirecord.notifications.ReminderNotificationPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScheduleConfigUiState {
    object Idle : ScheduleConfigUiState()
    object Loading : ScheduleConfigUiState()
    data class Success(val message: String) : ScheduleConfigUiState()
    data class Error(val message: String) : ScheduleConfigUiState()
}

data class ScheduleFormErrors(
    val medicationError: String? = null,
    val scheduledTimeError: String? = null,
    val frequencyError: String? = null,
    val selectedDaysError: String? = null,
) {
    val hasErrors: Boolean
        get() = medicationError != null ||
                scheduledTimeError != null ||
                frequencyError != null ||
                selectedDaysError != null
}

class ScheduleConfigViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MedicationRepository = MedicationRepository(ApiClient.apiService),
) : AndroidViewModel(application) {

    companion object {
        const val FREQ_DAILY = "Todos los días (Diario)"
        const val FREQ_EVERY_8_HOURS = "Cada 8 horas (3 veces al día)"
        const val FREQ_EVERY_12_HOURS = "Cada 12 horas (2 veces al día)"
        const val FREQ_SPECIFIC_DAYS = "Días específicos de la semana"
        const val FREQ_ALTERNATE_DAYS = "Días alternos (Un día sí y un día no)"
        const val FREQ_WEEKEND = "Fin de semana (Sábado y Domingo)"

        val FREQUENCY_OPTIONS = listOf(
            FREQ_DAILY,
            FREQ_EVERY_8_HOURS,
            FREQ_EVERY_12_HOURS,
            FREQ_SPECIFIC_DAYS,
            FREQ_ALTERNATE_DAYS,
            FREQ_WEEKEND,
        )

        val WEEK_DAYS = listOf(
            "Lunes",
            "Martes",
            "Miércoles",
            "Jueves",
            "Viernes",
            "Sábado",
            "Domingo"
        )

        fun requiresDaySelection(frequency: String): Boolean {
            return frequency.equals(FREQ_SPECIFIC_DAYS, ignoreCase = true) ||
                    frequency.contains("específicos", ignoreCase = true) ||
                    frequency.contains("personalizado", ignoreCase = true)
        }

        fun defaultDaysForFrequency(frequency: String): List<String> {
            return when (frequency) {
                FREQ_DAILY, FREQ_EVERY_8_HOURS, FREQ_EVERY_12_HOURS -> WEEK_DAYS
                FREQ_WEEKEND -> listOf("Sábado", "Domingo")
                FREQ_ALTERNATE_DAYS -> listOf("Lunes", "Miércoles", "Viernes", "Domingo")
                FREQ_SPECIFIC_DAYS -> emptyList()
                else -> WEEK_DAYS
            }
        }

        fun validateScheduleFields(
            medicationId: String,
            medicationName: String,
            scheduledTime: String,
            frequency: String,
            selectedDays: List<String>,
        ): ScheduleFormErrors {
            val trimmedId = medicationId.trim()
            val trimmedName = medicationName.trim()
            val trimmedTime = scheduledTime.trim()
            val trimmedFreq = frequency.trim()

            var medErr: String? = null
            var timeErr: String? = null
            var freqErr: String? = null
            var daysErr: String? = null

            // 1. Validar selección de medicamento
            if (trimmedId.isEmpty() || trimmedName.isEmpty()) {
                medErr = "Debes seleccionar un medicamento de la lista"
            }

            // 2. Validar hora de toma
            val timeRegex = Regex("^\\d{1,2}:\\d{2}(\\s?(AM|PM|am|pm))?$")
            if (trimmedTime.isEmpty()) {
                timeErr = "Debes establecer una hora de toma"
            } else if (!timeRegex.matches(trimmedTime)) {
                timeErr = "Formato de hora inválido (ej. 08:00 AM)"
            }

            // 3. Validar frecuencia
            if (trimmedFreq.isEmpty()) {
                freqErr = "Debes seleccionar la frecuencia correspondiente"
            }

            // 4. Validar selección de días cuando corresponda
            if (trimmedFreq.isNotEmpty() && selectedDays.isEmpty()) {
                daysErr = if (requiresDaySelection(trimmedFreq)) {
                    "Selecciona al menos un día de la semana para esta frecuencia"
                } else {
                    "Debes mantener al menos un día seleccionado para el horario"
                }
            }

            return ScheduleFormErrors(
                medicationError = medErr,
                scheduledTimeError = timeErr,
                frequencyError = freqErr,
                selectedDaysError = daysErr,
            )
        }
    }

    private val _medications = MutableStateFlow<List<MedicationDose>>(emptyList())
    val medications: StateFlow<List<MedicationDose>> = _medications.asStateFlow()

    private val _uiState = MutableStateFlow<ScheduleConfigUiState>(ScheduleConfigUiState.Idle)
    val uiState: StateFlow<ScheduleConfigUiState> = _uiState.asStateFlow()

    private val _formErrors = MutableStateFlow(ScheduleFormErrors())
    val formErrors: StateFlow<ScheduleFormErrors> = _formErrors.asStateFlow()

    init {
        loadAvailableMedications()
    }

    fun loadAvailableMedications() {
        viewModelScope.launch {
            repository.getTodayMedications()
                .onSuccess { list ->
                    _medications.value = list
                }
                .onFailure {
                    _medications.value = MedicationRepository.localDoses.toList()
                }
        }
    }

    fun validateAndSaveSchedule(
        medicationId: String,
        medicationName: String,
        scheduledTime: String,
        frequency: String,
        selectedDays: List<String>,
        reminderEnabled: Boolean = true,
        notes: String? = null,
    ) {
        val errors = validateScheduleFields(
            medicationId = medicationId,
            medicationName = medicationName,
            scheduledTime = scheduledTime,
            frequency = frequency,
            selectedDays = selectedDays
        )

        _formErrors.value = errors

        if (errors.hasErrors) {
            val firstError = errors.medicationError
                ?: errors.scheduledTimeError
                ?: errors.frequencyError
                ?: errors.selectedDaysError
            _uiState.value = ScheduleConfigUiState.Error(
                firstError ?: "Por favor revisa los campos obligatorios antes de guardar."
            )
            return
        }

        val orderedDays = WEEK_DAYS.filter { it in selectedDays }.ifEmpty { selectedDays }

        val request = ScheduleRequest(
            medicationId = medicationId.trim(),
            medicationName = medicationName.trim(),
            scheduledTime = scheduledTime.trim(),
            frequency = frequency.trim(),
            selectedDays = orderedDays,
            reminderEnabled = reminderEnabled,
            notes = notes?.trim()?.ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.value = ScheduleConfigUiState.Loading
            repository.saveMedicationSchedule(request)
                .onSuccess { response ->
                    val updatedList = MedicationRepository.localDoses.toList()
                    _medications.value = updatedList

                    if (reminderEnabled) {
                        val medDose = updatedList.find {
                            it.id == request.medicationId || it.name.equals(request.medicationName, ignoreCase = true)
                        }
                        val payload = ReminderNotificationPayload(
                            reminderId = request.medicationId,
                            medicationId = request.medicationId,
                            medicationName = request.medicationName,
                            dosage = medDose?.dosage ?: "Dosis programada",
                            scheduledTime = request.scheduledTime,
                            frequency = request.frequency,
                            selectedDays = request.selectedDays,
                            instructions = request.notes ?: medDose?.instructions
                        )
                        ReminderNotificationManager.scheduleNotificationForConfiguredTime(
                            context = getApplication(),
                            payload = payload
                        )
                    }

                    _uiState.value = ScheduleConfigUiState.Success(
                        response.message.ifBlank {
                            "El horario de '${request.medicationName}' se guardó correctamente."
                        }
                    )
                }
                .onFailure { error ->
                    _uiState.value = ScheduleConfigUiState.Error(
                        error.message?.ifBlank { "Ocurrió un error al guardar el horario. Intenta nuevamente." }
                            ?: "Ocurrió un error al guardar el horario. Intenta nuevamente."
                    )
                }
        }
    }

    fun clearErrors() {
        _formErrors.value = ScheduleFormErrors()
        if (_uiState.value is ScheduleConfigUiState.Error) {
            _uiState.value = ScheduleConfigUiState.Idle
        }
    }

    fun resetState() {
        _uiState.value = ScheduleConfigUiState.Idle
    }
}
