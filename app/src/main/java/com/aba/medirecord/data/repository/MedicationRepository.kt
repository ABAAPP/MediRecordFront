package com.aba.medirecord.data.repository

import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.MedicationResponse
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.model.ScheduleResponse
import com.aba.medirecord.data.network.ApiService
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MedicationRepository(
    private val apiService: ApiService,
) {
    companion object {
        val ALL_WEEK_DAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

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
                selectedDays = ALL_WEEK_DAYS
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
                selectedDays = listOf("Lunes", "Miércoles", "Viernes")
            ),
        )

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

        fun sortRemindersChronologically(reminders: List<MedicationReminder>): List<MedicationReminder> {
            return reminders.sortedWith(
                compareBy<MedicationReminder> { parseTimeToMinutes(it.scheduledTime) }
                    .thenBy { it.medicationName.lowercase() }
            )
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

        fun MedicationDose.toReminder(defaultUserEmail: String? = null): MedicationReminder {
            return MedicationReminder(
                id = this.id,
                medicationId = this.id,
                medicationName = this.name,
                dosage = this.dosage,
                scheduledTime = this.scheduledTime,
                frequency = this.frequency ?: "Todos los días (Diario)",
                selectedDays = if (this.selectedDays.isNotEmpty()) this.selectedDays else ALL_WEEK_DAYS,
                instructions = this.instructions,
                isCompleted = this.isCompleted,
                userEmail = this.userEmail ?: defaultUserEmail
            )
        }
    }

    suspend fun getTodayMedications(): Result<List<MedicationDose>> {
        return try {
            val response = withTimeoutOrNull(1500L) {
                apiService.getTodayMedications()
            }
            if (response != null && response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(localDoses.toList())
            }
        } catch (_: Exception) {
            Result.success(localDoses.toList())
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
                val filtered = filterRemindersForAuthenticatedUser(response.body()!!, normalizedEmail)
                Result.success(sortRemindersChronologically(filtered))
            } else if (response != null && !response.isSuccessful) {
                val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: "Error al obtener los recordatorios desde el servidor (${response.code()})"
                Result.failure(Exception(errorMsg))
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
                val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: "Error del servidor al registrar medicamento (${response.code()})"
                Result.failure(Exception(errorMsg))
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
                val errorMsg = response.errorBody()?.string()?.takeIf { it.isNotBlank() }
                    ?: "Error del servidor al guardar el horario (${response.code()})"
                Result.failure(Exception(errorMsg))
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
                val index = localDoses.indexOfFirst { it.id == doseId }
                if (index != -1) {
                    localDoses[index] = updated
                }
            } else {
                toggleLocalStatus(doseId)
            }
            Result.success(localDoses.toList())
        } catch (_: Exception) {
            toggleLocalStatus(doseId)
            Result.success(localDoses.toList())
        }
    }

    private fun toggleLocalStatus(doseId: String) {
        val index = localDoses.indexOfFirst { it.id == doseId }
        if (index != -1) {
            val current = localDoses[index]
            localDoses[index] = current.copy(isCompleted = !current.isCompleted)
        }
    }
}
