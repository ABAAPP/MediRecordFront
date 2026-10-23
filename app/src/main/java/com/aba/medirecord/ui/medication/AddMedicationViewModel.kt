package com.aba.medirecord.ui.medication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.util.UserMessageMapper
import com.aba.medirecord.util.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AddMedicationUiState {
    object Idle : AddMedicationUiState()
    object Loading : AddMedicationUiState()
    data class Success(val message: String) : AddMedicationUiState()
    data class Error(val message: String) : AddMedicationUiState()
}

data class MedicationFormErrors(
    val nameError: String? = null,
    val dosageError: String? = null,
    val startDateError: String? = null,
    val endDateError: String? = null,
    val scheduledTimeError: String? = null,
    val frequencyError: String? = null,
) {
    val hasErrors: Boolean
        get() = nameError != null || dosageError != null || startDateError != null ||
                endDateError != null || scheduledTimeError != null || frequencyError != null
}

class AddMedicationViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: MedicationRepository = MedicationRepository(ApiClient.apiService),
) : AndroidViewModel(application) {

    companion object {
        fun validateMedicationFields(
            name: String,
            dosage: String,
            startDate: String,
            endDate: String?,
            isContinuous: Boolean,
            frequency: String,
            scheduledTime: String,
        ): MedicationFormErrors {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                isLenient = false
            }
            val trimmedName = name.trim()
            val trimmedDosage = dosage.trim()
            val trimmedStartDate = startDate.trim()
            val trimmedEndDate = endDate?.trim().orEmpty()
            val trimmedFrequency = frequency.trim()
            val trimmedScheduledTime = scheduledTime.trim()

            var nameErr: String? = null
            var dosageErr: String? = null
            var startDateErr: String? = null
            var endDateErr: String? = null
            var scheduledTimeErr: String? = null
            var frequencyErr: String? = null

            // 1. Validar nombre del medicamento
            if (trimmedName.isEmpty()) {
                nameErr = "El nombre del medicamento es obligatorio"
            } else if (trimmedName.length < 2) {
                nameErr = "El nombre debe tener al menos 2 caracteres"
            }

            // 2. Validar dosis
            if (trimmedDosage.isEmpty()) {
                dosageErr = "La dosis o presentación es obligatoria (ejemplo: 500 mg, 1 tableta)"
            }

            // 3. Validar fecha de inicio y su formato
            var parsedStartDate: Date? = null
            if (trimmedStartDate.isEmpty()) {
                startDateErr = "La fecha de inicio es obligatoria"
            } else if (!ValidationUtils.isValidDateFormat(trimmedStartDate)) {
                startDateErr = "Formato de fecha inválido (usa el formato DD/MM/AAAA)"
            } else {
                try {
                    parsedStartDate = dateFormat.parse(trimmedStartDate)
                } catch (_: Exception) {
                    startDateErr = "La fecha de inicio no es válida (usa DD/MM/AAAA)"
                }
            }

            // 4. Validar fecha de fin si no es tratamiento continuo
            if (!isContinuous) {
                if (trimmedEndDate.isEmpty()) {
                    endDateErr = "Indica la fecha de finalización o activa 'Tratamiento continuo'"
                } else if (!ValidationUtils.isValidDateFormat(trimmedEndDate)) {
                    endDateErr = "Formato de fecha inválido (usa el formato DD/MM/AAAA)"
                } else {
                    try {
                        val parsedEndDate = dateFormat.parse(trimmedEndDate)
                        if (parsedStartDate != null && parsedEndDate != null && parsedEndDate.before(parsedStartDate)) {
                            endDateErr = "La fecha de finalización no puede ser anterior a la de inicio"
                        }
                    } catch (_: Exception) {
                        endDateErr = "La fecha de finalización no es válida (usa DD/MM/AAAA)"
                    }
                }
            }

            // 5. Validar horario / primera toma y su formato
            if (trimmedScheduledTime.isEmpty()) {
                scheduledTimeErr = "Debes indicar la hora de la toma"
            } else if (!ValidationUtils.isValidTimeFormat(trimmedScheduledTime)) {
                scheduledTimeErr = "El formato de la hora no es válido (ejemplo: 08:00 AM)"
            }

            // 6. Validar frecuencia
            if (trimmedFrequency.isEmpty()) {
                frequencyErr = "Debes seleccionar una frecuencia de horario"
            }

            return MedicationFormErrors(
                nameError = nameErr,
                dosageError = dosageErr,
                startDateError = startDateErr,
                endDateError = endDateErr,
                scheduledTimeError = scheduledTimeErr,
                frequencyError = frequencyErr,
            )
        }
    }

    private val _uiState = MutableStateFlow<AddMedicationUiState>(AddMedicationUiState.Idle)
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()

    private val _formErrors = MutableStateFlow(MedicationFormErrors())
    val formErrors: StateFlow<MedicationFormErrors> = _formErrors.asStateFlow()

    fun validateAndRegister(
        name: String,
        dosage: String,
        startDate: String,
        endDate: String?,
        isContinuous: Boolean,
        frequency: String,
        scheduledTime: String,
        instructions: String?,
    ) {
        val errors = validateMedicationFields(
            name = name,
            dosage = dosage,
            startDate = startDate,
            endDate = endDate,
            isContinuous = isContinuous,
            frequency = frequency,
            scheduledTime = scheduledTime
        )

        _formErrors.value = errors

        if (errors.hasErrors) {
            val firstErrorMessage = errors.nameError
                ?: errors.dosageError
                ?: errors.startDateError
                ?: errors.endDateError
                ?: errors.scheduledTimeError
                ?: errors.frequencyError
            _uiState.value = AddMedicationUiState.Error(
                firstErrorMessage ?: "Por favor revisa los campos obligatorios marcados en rojo."
            )
            return
        }

        val request = MedicationRequest(
            name = name.trim(),
            dosage = dosage.trim(),
            startDate = startDate.trim(),
            endDate = if (isContinuous) null else endDate?.trim()?.ifBlank { null },
            frequency = frequency.trim(),
            scheduledTime = scheduledTime.trim(),
            instructions = instructions?.trim()?.ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.value = AddMedicationUiState.Loading
            repository.registerMedication(request)
                .onSuccess { response ->
                    _uiState.value = AddMedicationUiState.Success(
                        UserMessageMapper.sanitizeMessageForUser(
                            response.message,
                            "¡Medicamento registrado con éxito!"
                        )
                    )
                }
                .onFailure { error ->
                    _uiState.value = AddMedicationUiState.Error(
                        UserMessageMapper.sanitizeMessageForUser(
                            error.message,
                            "No se pudo registrar el medicamento. Intenta nuevamente."
                        )
                    )
                }
        }
    }

    fun clearErrors() {
        _formErrors.value = MedicationFormErrors()
    }

    fun resetState() {
        _uiState.value = AddMedicationUiState.Idle
    }
}
