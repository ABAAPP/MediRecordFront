package com.aba.medirecord

import com.aba.medirecord.data.model.CompleteReminderRequest
import com.aba.medirecord.data.model.CompleteReminderResponse
import com.aba.medirecord.data.model.LoginRequest
import com.aba.medirecord.data.model.LoginResponse
import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.MedicationResponse
import com.aba.medirecord.data.model.RegisterRequest
import com.aba.medirecord.data.model.RegisterResponse
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.model.ScheduleResponse
import com.aba.medirecord.data.network.ApiService
import com.aba.medirecord.data.repository.MedicationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FakeApiService : ApiService {
    var shouldFail: Boolean = false
    var simulatedReminders: List<MedicationReminder> = emptyList()
    var simulatedTodayDoses: List<MedicationDose> = emptyList()
    var lastQueriedDate: String? = null
    var lastQueriedUserEmail: String? = null

    override suspend fun register(request: RegisterRequest): Response<RegisterResponse> {
        return Response.success(RegisterResponse(true, "OK"))
    }

    override suspend fun login(request: LoginRequest): Response<LoginResponse> {
        return Response.success(LoginResponse(true, "token", "OK"))
    }

    override suspend fun getTodayMedications(
        date: String?,
        userEmail: String?,
    ): Response<List<MedicationDose>> {
        lastQueriedDate = date
        lastQueriedUserEmail = userEmail
        return if (shouldFail) {
            Response.error(500, okhttp3.ResponseBody.create(null, "Error al consultar medicamentos del día"))
        } else {
            Response.success(simulatedTodayDoses)
        }
    }

    override suspend fun toggleDoseStatus(doseId: String): Response<MedicationDose> {
        return Response.success(MedicationDose(doseId, "Test", "10mg", "08:00 AM"))
    }

    override suspend fun registerMedication(request: MedicationRequest): Response<MedicationResponse> {
        return if (shouldFail) {
            Response.error(500, okhttp3.ResponseBody.create(null, "Error en el servidor"))
        } else {
            Response.success(
                MedicationResponse(
                    success = true,
                    message = "Medicamento '${request.name}' registrado exitosamente",
                    medicationId = "test-id-123",
                    medication = MedicationDose(
                        id = "test-id-123",
                        name = request.name,
                        dosage = request.dosage,
                        scheduledTime = request.scheduledTime,
                        instructions = request.instructions
                    )
                )
            )
        }
    }

    override suspend fun saveMedicationSchedule(request: ScheduleRequest): Response<ScheduleResponse> {
        return if (shouldFail) {
            Response.error(500, okhttp3.ResponseBody.create(null, "Error al guardar el horario en el servidor"))
        } else {
            Response.success(
                ScheduleResponse(
                    success = true,
                    message = "Horario guardado para '${request.medicationName}'",
                    scheduleId = "sched-123",
                    updatedMedication = MedicationDose(
                        id = request.medicationId,
                        name = request.medicationName,
                        dosage = "500 mg",
                        scheduledTime = request.scheduledTime,
                        instructions = request.notes,
                        frequency = request.frequency,
                        selectedDays = request.selectedDays
                    )
                )
            )
        }
    }

    override suspend fun getReminders(userEmail: String?): Response<List<MedicationReminder>> {
        return if (shouldFail) {
            Response.error(500, okhttp3.ResponseBody.create(null, "Error al obtener recordatorios"))
        } else {
            Response.success(simulatedReminders)
        }
    }

    override suspend fun markReminderAsCompleted(
        reminderId: String,
        request: CompleteReminderRequest,
    ): Response<CompleteReminderResponse> {
        return if (shouldFail) {
            Response.error(500, okhttp3.ResponseBody.create(null, "No se pudo completar la operación en el servidor"))
        } else {
            simulatedReminders = simulatedReminders.map {
                if (it.id == reminderId) {
                    it.copy(isCompleted = true, completedAt = request.completedAt)
                } else {
                    it
                }
            }
            Response.success(
                CompleteReminderResponse(
                    success = true,
                    message = "Recordatorio marcado como realizado exitosamente",
                    reminderId = reminderId,
                    completedAt = request.completedAt
                )
            )
        }
    }
}

class MedicationRegisterTest {

    @Test
    fun testMedicationRequestCreation() {
        val request = MedicationRequest(
            name = "Amoxicilina",
            dosage = "500 mg - 1 cápsula",
            startDate = "15/10/2026",
            endDate = "22/10/2026",
            frequency = "Cada 8 horas (3 veces al día)",
            scheduledTime = "08:00 AM",
            instructions = "Tomar después del desayuno"
        )

        assertEquals("Amoxicilina", request.name)
        assertEquals("500 mg - 1 cápsula", request.dosage)
        assertEquals("15/10/2026", request.startDate)
        assertEquals("22/10/2026", request.endDate)
        assertEquals("Cada 8 horas (3 veces al día)", request.frequency)
        assertEquals("08:00 AM", request.scheduledTime)
        assertEquals("Tomar después del desayuno", request.instructions)
    }

    @Test
    fun testRepositoryRegisterMedicationSuccess() = runBlocking {
        val fakeApi = FakeApiService()
        val repository = MedicationRepository(fakeApi)

        val request = MedicationRequest(
            name = "Losartán",
            dosage = "50 mg",
            startDate = "15/10/2026",
            endDate = null,
            frequency = "Cada 24 horas (1 vez al día)",
            scheduledTime = "09:00 AM",
            instructions = "En ayunas"
        )

        val result = repository.registerMedication(request)
        assertTrue(result.isSuccess)

        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response!!.success)

        val found = MedicationRepository.localDoses.any { it.name == "Losartán" }
        assertTrue(found)
    }

    @Test
    fun testRepositoryRegisterMedicationFailure() = runBlocking {
        val fakeApi = FakeApiService().apply { shouldFail = true }
        val repository = MedicationRepository(fakeApi)

        val request = MedicationRequest(
            name = "Medicamento Fallido",
            dosage = "100 mg",
            startDate = "15/10/2026",
            endDate = null,
            frequency = "Cada 12 horas",
            scheduledTime = "10:00 AM"
        )

        val result = repository.registerMedication(request)
        assertTrue(result.isFailure)
    }

    @Test
    fun testDateComparisonValidation() {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val start: Date = dateFormat.parse("15/10/2026")!!
        val endValid: Date = dateFormat.parse("20/10/2026")!!
        val endInvalid: Date = dateFormat.parse("10/10/2026")!!

        assertTrue(endValid.after(start) || endValid == start)
        assertTrue(endInvalid.before(start))
    }
}
