package com.aba.medirecord.data.repository

import com.aba.medirecord.data.model.CompleteReminderRequest
import com.aba.medirecord.data.model.CompleteReminderResponse
import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.MedicationResponse
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.model.ScheduleResponse
import com.aba.medirecord.data.network.ApiService
import com.aba.medirecord.util.UserMessageMapper
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MedicationRepository(
    private val apiService: ApiService,
) {
    companion object {
        val ALL_WEEK_DAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

        // Registro persistente en sesión de recordatorios marcados como realizados (id -> hora de realización)
        val completedRemindersRegistry = mutableMapOf(
            "1" to "08:05 AM"
        )

        // Lista compartida en memoria para simulación cuando no hay backend activo
        val localDoses = mutableListOf(
            MedicationDose(
                id = "1",
                name = "Paracetamol",
                dosage = "500 mg - 1 tableta",
                scheduledTime = "08:00 AM",
                isCompleted = true,
                instructions = "Con alimentos",
                frequency = "Cada 8 horas (3 veces al día)",
                selectedDays = ALL_WEEK_DAYS,
                completedAt = "08:05 AM"
            ),
            MedicationDose(
                id = "2",
                name = "Omeprazol",
                dosage = "20 mg - 1 cápsula",
                scheduledTime = "09:00 AM",
                isCompleted = false,
                instructions = "En ayunas",
                frequency = "Todos los días (Diario)",
                selectedDays = ALL_WEEK_DAYS
            ),
            MedicationDose(
                id = "3",
                name = "Amoxicilina",
                dosage = "500 mg - 1 cápsula",
                scheduledTime = "02:00 PM",
                isCompleted = false,
                instructions = "Tomar con agua",
                frequency = "Cada 8 horas (3 veces al día)",
                selectedDays = ALL_WEEK_DAYS
            ),
            MedicationDose(
                id = "4",
                name = "Ibuprofeno",
                dosage = "400 mg - 1 tableta",
                scheduledTime = "08:00 PM",
                isCompleted = false,
                instructions = "Después de cenar",
                frequency = "Días específicos de la semana",
                selectedDays = ALL_WEEK_DAYS
            ),
        )

        fun getCurrentDateIso(date: Date = Date()): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        }

        fun parseTimeToMinutes(timeStr: String): Int {
            val cleaned = timeStr.trim().uppercase()
            val regex = Regex("^(\\d{1,2}):(\\d{2})(?:\\s*(AM|PM))?$")
            val match = regex.find(cleaned) ?: return Int.MAX_VALUE
            var hour = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
            val minute = match.groupValues[2].toIntOrNull() ?: return Int.MAX_VALUE
            val amPm = match.groupValues[3]

            if (amPm == "PM" && hour < 12) {
                hour += 12
            } else if (amPm == "AM" && hour == 12) {
                hour = 0
            }
            return hour * 60 + minute
        }

        fun sortDosesChronologically(doses: List<MedicationDose>): List<MedicationDose> {
            return doses.sortedWith(
                compareBy<MedicationDose> { parseTimeToMinutes(it.scheduledTime) }
                    .thenBy { it.name.lowercase() }
            )
        }

        fun sortRemindersChronologically(reminders: List<MedicationReminder>): List<MedicationReminder> {
            return reminders.sortedWith(
                compareBy<MedicationReminder> { parseTimeToMinutes(it.scheduledTime) }
                    .thenBy { it.medicationName.lowercase() }
            )
        }

        fun filterDosesForUserAndDate(
            doses: List<MedicationDose>,
            userEmail: String?,
            date: String?,
        ): List<MedicationDose> {
            val normalizedEmail = userEmail?.trim().orEmpty()
            val normalizedDate = date?.trim().orEmpty()

            return doses.filter { dose ->
                val matchesUser = if (normalizedEmail.isEmpty()) {
                    true
                } else {
                    dose.userEmail.isNullOrBlank() ||
                            dose.userEmail.trim().equals(normalizedEmail, ignoreCase = true)
                }

                val matchesDate = if (normalizedDate.isEmpty() || dose.scheduledDate.isNullOrBlank()) {
                    true
                } else {
                    dose.scheduledDate.trim() == normalizedDate
                }

                matchesUser && matchesDate
            }
        }

        fun filterRemindersForAuthenticatedUser(
            reminders: List<MedicationReminder>,
            authenticatedUserEmail: String?,
        ): List<MedicationReminder> {
            val normalizedAuthEmail = authenticatedUserEmail?.trim().orEmpty()
            if (normalizedAuthEmail.isEmpty()) {
                return emptyList()
            }
            return reminders.filter { reminder ->
                reminder.userEmail.isNullOrBlank() ||
                        reminder.userEmail.trim().equals(normalizedAuthEmail, ignoreCase = true)
            }
        }

        fun applyPersistedCompletionToReminder(reminder: MedicationReminder): MedicationReminder {
            val persistedTime = completedRemindersRegistry[reminder.id]
                ?: completedRemindersRegistry[reminder.medicationId]
            return if (persistedTime != null) {
                reminder.copy(
                    isCompleted = true,
                    completedAt = reminder.completedAt ?: persistedTime
                )
            } else {
                reminder
            }
        }

        fun applyPersistedCompletionToDose(dose: MedicationDose): MedicationDose {
            val persistedTime = completedRemindersRegistry[dose.id]
            return if (persistedTime != null) {
                dose.copy(
                    isCompleted = true,
                    completedAt = dose.completedAt ?: persistedTime
                )
            } else {
                dose
            }
        }

        fun MedicationDose.toReminder(defaultUserEmail: String? = null): MedicationReminder {
            val syncedDose = applyPersistedCompletionToDose(this)
            return MedicationReminder(
                id = syncedDose.id,
                medicationId = syncedDose.id,
                medicationName = syncedDose.name,
                dosage = syncedDose.dosage,
                scheduledTime = syncedDose.scheduledTime,
                frequency = syncedDose.frequency ?: "Todos los días (Diario)",
                selectedDays = if (syncedDose.selectedDays.isNotEmpty()) syncedDose.selectedDays else ALL_WEEK_DAYS,
                instructions = syncedDose.instructions,
                isCompleted = syncedDose.isCompleted,
                userEmail = syncedDose.userEmail ?: defaultUserEmail,
                completedAt = syncedDose.completedAt
            )
        }
    }

    suspend fun getTodayMedications(
        date: String = getCurrentDateIso(),
        userEmail: String? = null,
    ): Result<List<MedicationDose>> {
        val queryDate = date.trim().ifEmpty { getCurrentDateIso() }
        val normalizedEmail = userEmail?.trim()?.ifBlank { null }

        return try {
            val response = withTimeoutOrNull(1500L) {
                apiService.getTodayMedications(date = queryDate, userEmail = normalizedEmail)
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                val synced = response.body()!!.map { applyPersistedCompletionToDose(it) }
                val filtered = filterDosesForUserAndDate(synced, normalizedEmail, queryDate)
                Result.success(sortDosesChronologically(filtered))
            } else {
                val synced = localDoses.map { applyPersistedCompletionToDose(it) }
                val filtered = filterDosesForUserAndDate(synced, normalizedEmail, queryDate)
                Result.success(sortDosesChronologically(filtered))
            }
        } catch (_: Exception) {
            val synced = localDoses.map { applyPersistedCompletionToDose(it) }
            val filtered = filterDosesForUserAndDate(synced, normalizedEmail, queryDate)
            Result.success(sortDosesChronologically(filtered))
        }
    }

    suspend fun getRemindersForUser(userEmail: String?): Result<List<MedicationReminder>> {
        val normalizedEmail = userEmail?.trim().orEmpty()
        if (normalizedEmail.isEmpty()) {
            return Result.failure(Exception("Debes iniciar sesión para visualizar tus recordatorios"))
        }

        return try {
            val response = withTimeoutOrNull(1500L) {
                apiService.getReminders(normalizedEmail)
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                val synced = response.body()!!.map { applyPersistedCompletionToReminder(it) }
                val filtered = filterRemindersForAuthenticatedUser(synced, normalizedEmail)
                Result.success(sortRemindersChronologically(filtered))
            } else if (response != null && !response.isSuccessful) {
                val rawBody = response.errorBody()?.string()
                val friendlyMsg = UserMessageMapper.mapApiError(
                    httpCode = response.code(),
                    rawErrorBody = rawBody,
                    defaultFriendlyMessage = "No se pudieron obtener tus recordatorios en este momento. Intenta nuevamente."
                )
                Result.failure(Exception(friendlyMsg))
            } else {
                val localReminders = localDoses.map { it.toReminder(normalizedEmail) }
                val filtered = filterRemindersForAuthenticatedUser(localReminders, normalizedEmail)
                Result.success(sortRemindersChronologically(filtered))
            }
        } catch (_: Exception) {
            val localReminders = localDoses.map { it.toReminder(normalizedEmail) }
            val filtered = filterRemindersForAuthenticatedUser(localReminders, normalizedEmail)
            Result.success(sortRemindersChronologically(filtered))
        }
    }

    suspend fun markReminderAsCompleted(
        reminderId: String,
        userEmail: String? = null,
        completedAt: String = SimpleDateFormat("hh:mm a", Locale.US).format(Date()),
    ): Result<CompleteReminderResponse> {
        val trimmedId = reminderId.trim()
        if (trimmedId.isEmpty()) {
            return Result.failure(Exception("No se pudo identificar el recordatorio a completar"))
        }

        val request = CompleteReminderRequest(
            reminderId = trimmedId,
            completedAt = completedAt,
            userEmail = userEmail?.trim()?.ifBlank { null }
        )

        return try {
            val response = withTimeoutOrNull(2000L) {
                apiService.markReminderAsCompleted(trimmedId, request)
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (!body.success) {
                    return Result.failure(
                        Exception(body.message.ifBlank { "No se pudo marcar el recordatorio como realizado" })
                    )
                }
                val finalCompletedAt = body.completedAt ?: completedAt
                persistCompletedState(trimmedId, finalCompletedAt)
                Result.success(
                    body.copy(
                        reminderId = body.reminderId ?: trimmedId,
                        completedAt = finalCompletedAt
                    )
                )
            } else if (response != null && !response.isSuccessful) {
                val rawBody = response.errorBody()?.string()
                val friendlyMsg = UserMessageMapper.mapApiError(
                    httpCode = response.code(),
                    rawErrorBody = rawBody,
                    defaultFriendlyMessage = "No se pudo completar la operación para marcar la toma como realizada. Intenta nuevamente."
                )
                Result.failure(Exception(friendlyMsg))
            } else {
                // Modo offline / simulación con persistencia en sesión
                val updatedDose = persistCompletedState(trimmedId, completedAt)
                val medName = updatedDose?.name ?: "medicamento"
                Result.success(
                    CompleteReminderResponse(
                        success = true,
                        message = "Toma de '$medName' marcada como realizada",
                        reminderId = trimmedId,
                        completedAt = completedAt,
                        updatedReminder = updatedDose?.toReminder(userEmail)
                    )
                )
            }
        } catch (_: Exception) {
            val updatedDose = persistCompletedState(trimmedId, completedAt)
            val medName = updatedDose?.name ?: "medicamento"
            Result.success(
                CompleteReminderResponse(
                    success = true,
                    message = "Toma de '$medName' marcada como realizada",
                    reminderId = trimmedId,
                    completedAt = completedAt,
                    updatedReminder = updatedDose?.toReminder(userEmail)
                )
            )
        }
    }

    private fun persistCompletedState(reminderId: String, completedAt: String): MedicationDose? {
        completedRemindersRegistry[reminderId] = completedAt
        val index = localDoses.indexOfFirst { it.id == reminderId }
        return if (index != -1) {
            val current = localDoses[index]
            val updated = current.copy(
                isCompleted = true,
                completedAt = completedAt
            )
            localDoses[index] = updated
            updated
        } else {
            null
        }
    }

    suspend fun toggleReminderStatus(reminderId: String, userEmail: String?): Result<List<MedicationReminder>> {
        toggleDoseStatus(reminderId)
        return getRemindersForUser(userEmail)
    }

    suspend fun registerMedication(request: MedicationRequest): Result<MedicationResponse> {
        return try {
            val response = withTimeoutOrNull(2000L) {
                apiService.registerMedication(request)
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val newDose = body.medication ?: MedicationDose(
                    id = body.medicationId ?: UUID.randomUUID().toString(),
                    name = request.name,
                    dosage = request.dosage,
                    scheduledTime = request.scheduledTime,
                    isCompleted = false,
                    instructions = request.instructions,
                    frequency = request.frequency,
                    selectedDays = ALL_WEEK_DAYS
                )
                localDoses.add(newDose)
                Result.success(body)
            } else if (response != null && !response.isSuccessful) {
                val rawBody = response.errorBody()?.string()
                val friendlyMsg = UserMessageMapper.mapApiError(
                    httpCode = response.code(),
                    rawErrorBody = rawBody,
                    defaultFriendlyMessage = "No se pudo registrar el medicamento en este momento. Por favor intenta nuevamente."
                )
                Result.failure(Exception(friendlyMsg))
            } else {
                // Modo offline / simulación
                val newDose = MedicationDose(
                    id = UUID.randomUUID().toString(),
                    name = request.name,
                    dosage = request.dosage,
                    scheduledTime = request.scheduledTime,
                    isCompleted = false,
                    instructions = request.instructions,
                    frequency = request.frequency,
                    selectedDays = ALL_WEEK_DAYS
                )
                localDoses.add(newDose)
                Result.success(
                    MedicationResponse(
                        success = true,
                        message = "Medicamento '${request.name}' registrado exitosamente",
                        medicationId = newDose.id,
                        medication = newDose
                    )
                )
            }
        } catch (e: Exception) {
            val newDose = MedicationDose(
                id = UUID.randomUUID().toString(),
                name = request.name,
                dosage = request.dosage,
                scheduledTime = request.scheduledTime,
                isCompleted = false,
                instructions = request.instructions,
                frequency = request.frequency,
                selectedDays = ALL_WEEK_DAYS
            )
            localDoses.add(newDose)
            Result.success(
                MedicationResponse(
                    success = true,
                    message = "Medicamento '${request.name}' registrado exitosamente",
                    medicationId = newDose.id,
                    medication = newDose
                )
            )
        }
    }

    suspend fun saveMedicationSchedule(request: ScheduleRequest): Result<ScheduleResponse> {
        return try {
            val response = withTimeoutOrNull(2000L) {
                apiService.saveMedicationSchedule(request)
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                updateLocalSchedule(request, body.updatedMedication)
                Result.success(body)
            } else if (response != null && !response.isSuccessful) {
                val rawBody = response.errorBody()?.string()
                val friendlyMsg = UserMessageMapper.mapApiError(
                    httpCode = response.code(),
                    rawErrorBody = rawBody,
                    defaultFriendlyMessage = "No se pudo guardar la configuración del horario. Por favor intenta nuevamente."
                )
                Result.failure(Exception(friendlyMsg))
            } else {
                // Modo offline / simulación sincronizada
                val updated = updateLocalSchedule(request, null)
                Result.success(
                    ScheduleResponse(
                        success = true,
                        message = "Horario configurado correctamente para '${request.medicationName}' a las ${request.scheduledTime}",
                        scheduleId = UUID.randomUUID().toString(),
                        updatedMedication = updated
                    )
                )
            }
        } catch (_: Exception) {
            val updated = updateLocalSchedule(request, null)
            Result.success(
                ScheduleResponse(
                    success = true,
                    message = "Horario configurado correctamente para '${request.medicationName}' a las ${request.scheduledTime}",
                    scheduleId = UUID.randomUUID().toString(),
                    updatedMedication = updated
                )
            )
        }
    }

    private fun updateLocalSchedule(
        request: ScheduleRequest,
        serverMedication: MedicationDose?,
    ): MedicationDose {
        val index = localDoses.indexOfFirst {
            it.id == request.medicationId || it.name.equals(request.medicationName, ignoreCase = true)
        }
        return if (index != -1) {
            val current = localDoses[index]
            val updated = serverMedication ?: current.copy(
                scheduledTime = request.scheduledTime,
                frequency = request.frequency,
                selectedDays = request.selectedDays,
                instructions = if (!request.notes.isNullOrBlank()) request.notes else current.instructions
            )
            localDoses[index] = updated
            updated
        } else {
            val newDose = serverMedication ?: MedicationDose(
                id = request.medicationId.ifBlank { UUID.randomUUID().toString() },
                name = request.medicationName,
                dosage = "Dosis estándar",
                scheduledTime = request.scheduledTime,
                isCompleted = false,
                instructions = request.notes,
                frequency = request.frequency,
                selectedDays = request.selectedDays
            )
            localDoses.add(newDose)
            newDose
        }
    }

    suspend fun toggleDoseStatus(doseId: String): Result<List<MedicationDose>> {
        return try {
            val response = withTimeoutOrNull(1500L) {
                apiService.toggleDoseStatus(doseId)
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                val updated = response.body()!!
                if (updated.isCompleted) {
                    completedRemindersRegistry[doseId] = updated.completedAt ?: "Realizado"
                } else {
                    completedRemindersRegistry.remove(doseId)
                }
                val index = localDoses.indexOfFirst { it.id == doseId }
                if (index != -1) {
                    localDoses[index] = updated
                }
            } else {
                toggleLocalStatus(doseId)
            }
            val synced = localDoses.map { applyPersistedCompletionToDose(it) }
            Result.success(sortDosesChronologically(synced))
        } catch (_: Exception) {
            toggleLocalStatus(doseId)
            val synced = localDoses.map { applyPersistedCompletionToDose(it) }
            Result.success(sortDosesChronologically(synced))
        }
    }

    private fun toggleLocalStatus(doseId: String) {
        val index = localDoses.indexOfFirst { it.id == doseId }
        if (index != -1) {
            val current = localDoses[index]
            val newCompleted = !current.isCompleted
            val completedAtStr = if (newCompleted) {
                SimpleDateFormat("hh:mm a", Locale.US).format(Date())
            } else {
                null
            }
            if (newCompleted && completedAtStr != null) {
                completedRemindersRegistry[doseId] = completedAtStr
            } else {
                completedRemindersRegistry.remove(doseId)
            }
            localDoses[index] = current.copy(
                isCompleted = newCompleted,
                completedAt = completedAtStr
            )
        }
    }
}
