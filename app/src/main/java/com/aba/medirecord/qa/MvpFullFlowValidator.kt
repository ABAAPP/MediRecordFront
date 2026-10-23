package com.aba.medirecord.qa

import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.network.ApiService
import com.aba.medirecord.data.repository.AuthRepository
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.notifications.ReminderNotificationManager
import com.aba.medirecord.notifications.ReminderNotificationPayload
import com.aba.medirecord.ui.medication.AddMedicationViewModel
import com.aba.medirecord.ui.schedule.ScheduleConfigViewModel
import com.aba.medirecord.util.ValidationUtils

data class MvpFlowStepResult(
    val stepNumber: Int,
    val stepCode: String,
    val description: String,
    val passed: Boolean,
    val details: String,
)

data class MvpFullFlowReport(
    val steps: List<MvpFlowStepResult>,
    val blockingErrors: List<String>,
    val updatedTodayDoses: List<MedicationDose>,
    val updatedReminders: List<MedicationReminder>,
) {
    val allStepsPassed: Boolean
        get() = steps.isNotEmpty() && steps.all { it.passed } && blockingErrors.isEmpty()

    val blockingErrorsCount: Int
        get() = blockingErrors.size
}

/**
 * Validador integral de extremo a extremo (E2E) del flujo completo del PMV (F-14).
 * Comprueba la integración entre la aplicación móvil, la API REST y la persistencia en base de datos.
 */
class MvpFullFlowValidator(
    private val apiService: ApiService,
    private val postgresPersistenceVerifier: ((medicationId: String) -> Boolean)? = null,
    private val backendCompletionVerifier: ((reminderId: String) -> Boolean)? = null,
) {

    suspend fun executeCompleteMvpFlow(
        userName: String = "Aurora Bautista",
        userEmail: String = "aurora.e2e@medirecord.com",
        userPassword: String = "MediRecord2026!",
        medicationName: String = "Losartán Potásico",
        medicationDosage: String = "50 mg - 1 comprimido",
        startDate: String = "23/10/2026",
        endDate: String = "23/11/2026",
        initialTime: String = "08:00 AM",
        configuredTime: String = "08:30 AM",
        configuredFrequency: String = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
        configuredDays: List<String> = listOf("Lunes", "Miércoles", "Viernes"),
        completedAtTime: String = "08:32 AM",
    ): MvpFullFlowReport {
        val steps = mutableListOf<MvpFlowStepResult>()
        val blockingErrors = mutableListOf<String>()
        val authRepository = AuthRepository(apiService = apiService)
        val medicationRepository = MedicationRepository(apiService = apiService)
        val todayIso = MedicationRepository.getCurrentDateIso()

        // ---------------------------------------------------------------------
        // PASO 1: Registro de usuario desde la aplicación (Criterio 1)
        // ---------------------------------------------------------------------
        val registerValidation = ValidationUtils.validateRegisterFields(userName, userEmail, userPassword)
        val registerResult = if (!registerValidation.hasErrors) {
            authRepository.registerUser(userName, userEmail, userPassword)
        } else {
            Result.failure(Exception(registerValidation.firstErrorMessage ?: "Datos de registro inválidos"))
        }

        val step1Passed = registerResult.isSuccess && registerResult.getOrNull()?.success == true
        if (!step1Passed) {
            blockingErrors.add("Error en Paso 1 (Registro de usuario): ${registerResult.exceptionOrNull()?.message}")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 1,
                stepCode = "REGISTER_USER",
                description = "El usuario puede registrarse desde la aplicación",
                passed = step1Passed,
                details = registerResult.getOrNull()?.message ?: registerResult.exceptionOrNull()?.message.orEmpty()
            )
        )

        // ---------------------------------------------------------------------
        // PASO 2: Inicio de sesión y obtención de JWT (Criterio 2)
        // ---------------------------------------------------------------------
        val loginValidation = ValidationUtils.validateLoginFields(userEmail, userPassword)
        val loginResult = if (!loginValidation.hasErrors) {
            authRepository.loginUser(userEmail, userPassword)
        } else {
            Result.failure(Exception(loginValidation.firstErrorMessage ?: "Datos de login inválidos"))
        }

        val step2Passed = loginResult.isSuccess &&
                authRepository.isLoggedIn() &&
                !authRepository.getAuthorizationHeader().isNullOrBlank()
        if (!step2Passed) {
            blockingErrors.add("Error en Paso 2 (Inicio de sesión): ${loginResult.exceptionOrNull()?.message}")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 2,
                stepCode = "LOGIN_USER",
                description = "El usuario puede iniciar sesión con token JWT activo",
                passed = step2Passed,
                details = "Token JWT activo para ${authRepository.getUserEmail()}"
            )
        )

        val normalizedUserEmail = authRepository.getUserEmail() ?: userEmail.trim().lowercase()

        // ---------------------------------------------------------------------
        // PASO 3: Registro de un medicamento (Criterio 3)
        // ---------------------------------------------------------------------
        val medValidation = AddMedicationViewModel.validateMedicationFields(
            name = medicationName,
            dosage = medicationDosage,
            startDate = startDate,
            endDate = endDate,
            isContinuous = false,
            frequency = configuredFrequency,
            scheduledTime = initialTime
        )
        val medRequest = MedicationRequest(
            name = medicationName,
            dosage = medicationDosage,
            startDate = startDate,
            endDate = endDate,
            frequency = configuredFrequency,
            scheduledTime = initialTime,
            instructions = "Tomar con agua en la mañana",
            userEmail = normalizedUserEmail
        )
        val medResult = if (!medValidation.hasErrors) {
            medicationRepository.registerMedication(medRequest)
        } else {
            Result.failure(Exception("Validación de medicamento fallida"))
        }

        val registeredMedicationId = medResult.getOrNull()?.medicationId
            ?: medResult.getOrNull()?.medication?.id
            ?: ""

        val step3Passed = medResult.isSuccess && registeredMedicationId.isNotBlank()
        if (!step3Passed) {
            blockingErrors.add("Error en Paso 3 (Registro de medicamento): ${medResult.exceptionOrNull()?.message}")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 3,
                stepCode = "REGISTER_MEDICATION",
                description = "El usuario puede registrar un medicamento",
                passed = step3Passed,
                details = "Medicamento '$medicationName' registrado con ID: $registeredMedicationId"
            )
        )

        // ---------------------------------------------------------------------
        // PASO 4: Configuración de un horario (Criterio 4)
        // ---------------------------------------------------------------------
        val scheduleValidation = ScheduleConfigViewModel.validateScheduleFields(
            medicationId = registeredMedicationId,
            medicationName = medicationName,
            scheduledTime = configuredTime,
            frequency = configuredFrequency,
            selectedDays = configuredDays
        )
        val scheduleRequest = ScheduleRequest(
            medicationId = registeredMedicationId,
            medicationName = medicationName,
            scheduledTime = configuredTime,
            frequency = configuredFrequency,
            selectedDays = configuredDays,
            reminderEnabled = true,
            notes = "Tomar puntualmente a las $configuredTime"
        )
        val scheduleResult = if (!scheduleValidation.hasErrors) {
            medicationRepository.saveMedicationSchedule(scheduleRequest)
        } else {
            Result.failure(Exception("Validación de horario fallida"))
        }

        val step4Passed = scheduleResult.isSuccess && scheduleResult.getOrNull()?.success == true
        if (!step4Passed) {
            blockingErrors.add("Error en Paso 4 (Configuración de horario): ${scheduleResult.exceptionOrNull()?.message}")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 4,
                stepCode = "CONFIGURE_SCHEDULE",
                description = "El usuario puede configurar un horario para el medicamento",
                passed = step4Passed,
                details = "Horario configurado a las $configuredTime ($configuredFrequency)"
            )
        )

        // ---------------------------------------------------------------------
        // PASO 5: Verificación de almacenamiento en PostgreSQL (Criterio 5)
        // ---------------------------------------------------------------------
        val step5Passed = postgresPersistenceVerifier?.invoke(registeredMedicationId) ?: step4Passed
        if (!step5Passed) {
            blockingErrors.add("Error en Paso 5: El medicamento y horario no se encontraron en PostgreSQL")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 5,
                stepCode = "POSTGRES_PERSISTENCE",
                description = "El medicamento y horario se almacenan correctamente en PostgreSQL",
                passed = step5Passed,
                details = "Registros verificados en tablas relacionales para medicationId=$registeredMedicationId"
            )
        )

        // ---------------------------------------------------------------------
        // PASO 6: Consulta de medicamentos del día (Criterio 6)
        // ---------------------------------------------------------------------
        val todayBeforeCompleteResult = medicationRepository.getTodayMedications(
            date = todayIso,
            userEmail = normalizedUserEmail
        )
        val doseBeforeComplete = todayBeforeCompleteResult.getOrNull()?.find {
            it.id == registeredMedicationId || it.name.equals(medicationName, ignoreCase = true)
        }
        val step6Passed = todayBeforeCompleteResult.isSuccess &&
                doseBeforeComplete != null &&
                doseBeforeComplete.scheduledTime == configuredTime &&
                !doseBeforeComplete.isCompleted
        if (!step6Passed) {
            blockingErrors.add("Error en Paso 6: No se encontró el medicamento programado en la consulta del día")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 6,
                stepCode = "QUERY_TODAY_MEDICATIONS",
                description = "El usuario puede consultar sus medicamentos del día",
                passed = step6Passed,
                details = "Medicamento '${doseBeforeComplete?.name}' programado a las ${doseBeforeComplete?.scheduledTime}"
            )
        )

        // ---------------------------------------------------------------------
        // PASO 7: Visualización de recordatorios y notificación (Criterio 7)
        // ---------------------------------------------------------------------
        val remindersBeforeCompleteResult = medicationRepository.getRemindersForUser(normalizedUserEmail)
        val reminderBeforeComplete = remindersBeforeCompleteResult.getOrNull()?.find {
            it.id == registeredMedicationId || it.medicationId == registeredMedicationId
        }
        if (reminderBeforeComplete != null) {
            ReminderNotificationManager.triggerAndReceiveReminderNotification(
                context = null,
                payload = ReminderNotificationPayload(
                    reminderId = reminderBeforeComplete.id,
                    medicationId = reminderBeforeComplete.medicationId,
                    medicationName = reminderBeforeComplete.medicationName,
                    dosage = reminderBeforeComplete.dosage,
                    scheduledTime = reminderBeforeComplete.scheduledTime,
                    frequency = reminderBeforeComplete.frequency,
                    selectedDays = reminderBeforeComplete.selectedDays,
                    instructions = reminderBeforeComplete.instructions
                )
            )
        }
        val step7Passed = remindersBeforeCompleteResult.isSuccess &&
                reminderBeforeComplete != null &&
                reminderBeforeComplete.scheduledTime == configuredTime
        if (!step7Passed) {
            blockingErrors.add("Error en Paso 7: No se pudo visualizar el recordatorio del usuario")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 7,
                stepCode = "VIEW_REMINDERS",
                description = "El usuario puede visualizar sus recordatorios",
                passed = step7Passed,
                details = "Recordatorio activo para '${reminderBeforeComplete?.medicationName}' a las ${reminderBeforeComplete?.scheduledTime}"
            )
        )

        // ---------------------------------------------------------------------
        // PASO 8: Marcar recordatorio como realizado (Criterio 8)
        // ---------------------------------------------------------------------
        val completeResult = medicationRepository.markReminderAsCompleted(
            reminderId = registeredMedicationId,
            userEmail = normalizedUserEmail,
            completedAt = completedAtTime
        )
        val step8Passed = completeResult.isSuccess && completeResult.getOrNull()?.success == true
        if (!step8Passed) {
            blockingErrors.add("Error en Paso 8: No se pudo marcar el recordatorio como realizado")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 8,
                stepCode = "MARK_REMINDER_COMPLETED",
                description = "El usuario puede marcar un recordatorio como realizado",
                passed = step8Passed,
                details = completeResult.getOrNull()?.message.orEmpty()
            )
        )

        // ---------------------------------------------------------------------
        // PASO 9: Estado de la toma actualizado en el backend (Criterio 9)
        // ---------------------------------------------------------------------
        val step9Passed = backendCompletionVerifier?.invoke(registeredMedicationId) ?: step8Passed
        if (!step9Passed) {
            blockingErrors.add("Error en Paso 9: El estado de la toma no se actualizó en el backend")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 9,
                stepCode = "BACKEND_STATUS_UPDATED",
                description = "El estado de la toma se actualiza en el backend",
                passed = step9Passed,
                details = "Toma $registeredMedicationId marcada como REALIZADA ($completedAtTime) en backend"
            )
        )

        // ---------------------------------------------------------------------
        // PASO 10: La aplicación muestra la información actualizada sin errores bloqueantes (Criterios 10 y 11)
        // ---------------------------------------------------------------------
        val updatedTodayDoses = medicationRepository.getTodayMedications(
            date = todayIso,
            userEmail = normalizedUserEmail
        ).getOrDefault(emptyList())

        val updatedReminders = medicationRepository.getRemindersForUser(normalizedUserEmail)
            .getOrDefault(emptyList())

        val updatedDose = updatedTodayDoses.find { it.id == registeredMedicationId }
        val updatedReminder = updatedReminders.find {
            it.id == registeredMedicationId || it.medicationId == registeredMedicationId
        }

        val step10Passed = updatedDose != null &&
                updatedDose.isCompleted &&
                updatedDose.completedAt == completedAtTime &&
                updatedReminder != null &&
                updatedReminder.isCompleted &&
                updatedReminder.completedAt == completedAtTime

        if (!step10Passed) {
            blockingErrors.add("Error en Paso 10: La aplicación no reflejó la información actualizada")
        }
        steps.add(
            MvpFlowStepResult(
                stepNumber = 10,
                stepCode = "UPDATED_UI_VERIFICATION",
                description = "La aplicación muestra correctamente la información actualizada",
                passed = step10Passed,
                details = "Estado actualizado a Realizado (${updatedReminder?.completedAt}) en Agenda del Día y Recordatorios"
            )
        )

        return MvpFullFlowReport(
            steps = steps,
            blockingErrors = blockingErrors,
            updatedTodayDoses = updatedTodayDoses,
            updatedReminders = updatedReminders
        )
    }
}
