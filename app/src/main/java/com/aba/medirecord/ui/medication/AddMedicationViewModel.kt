package com.aba.medirecord.ui.medication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.data.repository.MedicationRepository
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

    private val _uiState = MutableStateFlow<AddMedicationUiState>(AddMedicationUiState.Idle)
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()

    private val _formErrors = MutableStateFlow(MedicationFormErrors())
    val formErrors: StateFlow<MedicationFormErrors> = _formErrors.asStateFlow()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

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
        val trimmedName = name.trim()
        val trimmedDosage = dosage.trim()
        val trimmedStartDate = startDate.trim()
        val trimmedEndDate = endDate?.trim().orEmpty()
        val trimmedFrequency = frequency.trim()
        val trimmedScheduledTime = scheduledTime.trim()
        val trimmedInstructions = instructions?.trim()

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
            dosageErr = "La dosis o presentación es obligatoria (ej. 500 mg, 1 tableta)"
        }

        // 3. Validar fecha de inicio
        var parsedStartDate: Date? = null
        if (trimmedStartDate.isEmpty()) {
            startDateErr = "La fecha de inicio es obligatoria"
        } else {
            try {
                parsedStartDate = dateFormat.parse(trimmedStartDate)
            } catch (_: Exception) {
                startDateErr = "Formato de fecha inválido (use DD/MM/AAAA)"
            }
        }

        // 4. Validar fecha de fin si no es tratamiento continuo
        if (!isContinuous) {
            if (trimmedEndDate.isEmpty()) {
                endDateErr = "Indica la fecha de fin o activa 'Tratamiento continuo'"
            } else {
                try {
                    val parsedEndDate = dateFormat.parse(trimmedEndDate)
                    if (parsedStartDate != null && parsedEndDate != null && parsedEndDate.before(parsedStartDate)) {
                        endDateErr = "La fecha de finalización no puede ser anterior a la de inicio"
                    }
                } catch (_: Exception) {
                    endDateErr = "Formato de fecha inválido (use DD/MM/AAAA)"
                }
            }
        }

        // 5. Validar horario / primera toma
        if (trimmedScheduledTime.isEmpty()) {
            scheduledTimeErr = "Debes indicar la hora de la toma"
        }

        // 6. Validar frecuencia
        if (trimmedFrequency.isEmpty()) {
            frequencyErr = "Debes seleccionar una frecuencia de horario"
        }

        val errors = MedicationFormErrors(
            nameError = nameErr,
            dosageError = dosageErr,
            startDateError = startDateErr,
            endDateError = endDateErr,
            scheduledTimeError = scheduledTimeErr,
            frequencyError = frequencyErr,
        )

        _formErrors.value = errors

        if (errors.hasErrors) {
            val firstErrorMessage = nameErr ?: dosageErr ?: startDateErr ?: endDateErr ?: scheduledTimeErr ?: frequencyErr
            _uiState.value = AddMedicationUiState.Error(
                firstErrorMessage ?: "Por favor revisa los campos obligatorios marcados en rojo."
            )
            return
        }

        val request = MedicationRequest(
            name = trimmedName,
            dosage = trimmedDosage,
            startDate = trimmedStartDate,
            endDate = if (isContinuous) null else trimmedEndDate.ifBlank { null },
            frequency = trimmedFrequency,
            scheduledTime = trimmedScheduledTime,
            instructions = trimmedInstructions?.ifBlank { null }
        )

        viewModelScope.launch {
            _uiState.value = AddMedicationUiState.Loading
            repository.registerMedication(request)
                .onSuccess { response ->
                    _uiState.value = AddMedicationUiState.Success(
                        response.message.ifBlank { "¡Medicamento registrado con éxito!" }
                    )
                }
                .onFailure { error ->
                    _uiState.value = AddMedicationUiState.Error(
                        error.message?.ifBlank { "No se pudo registrar el medicamento. Intente nuevamente." }
                            ?: "No se pudo registrar el medicamento. Intente nuevamente."
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
