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
import com.aba.medirecord.notifications.ReminderNotificationManager
import com.aba.medirecord.qa.MvpFullFlowValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.UUID

/**
 * Simulador con estado que modela el Backend Spring Boot + Base de Datos PostgreSQL
 * (tablas: pacientes, medicamentos, horarios_medicamento y registro_tomas) para validar
 * el flujo integrado completo del PMV (F-14).
 */
class StatefulPostgresBackendSimulator : ApiService {

    data class PgPacienteEntity(
        val id: String,
        val nombre: String,
        val email: String,
        val passwordHash: String,
    )

    data class PgMedicamentoEntity(
        val id: String,
        val pacienteEmail: String,
        val nombre: String,
        val dosis: String,
        val fechaInicio: String,
        val fechaFin: String?,
        val instrucciones: String?,
    )

    data class PgHorarioEntity(
        val id: String,
        val medicamentoId: String,
        val horaToma: String,
        val frecuencia: String,
        val diasSeleccionados: List<String>,
        val notas: String?,
        val realizado: Boolean = false,
        val horaRealizado: String? = null,
    )

    val tablaPacientes = mutableMapOf<String, PgPacienteEntity>()
    val tablaMedicamentos = mutableMapOf<String, PgMedicamentoEntity>()
    val tablaHorarios = mutableMapOf<String, PgHorarioEntity>()

    override suspend fun register(request: RegisterRequest): Response<RegisterResponse> {
        val emailKey = request.email.trim().lowercase()
        if (tablaPacientes.containsKey(emailKey)) {
            return Response.error(409, okhttp3.ResponseBody.create(null, "El correo ya está registrado"))
        }
        val entity = PgPacienteEntity(
            id = UUID.randomUUID().toString(),
            nombre = request.name.trim(),
            email = emailKey,
            passwordHash = "bcrypt_${request.password.hashCode()}"
        )
        tablaPacientes[emailKey] = entity
        return Response.success(
            RegisterResponse(
                success = true,
                message = "Paciente '${entity.nombre}' almacenado en PostgreSQL exitosamente"
            )
        )
    }

    override suspend fun login(request: LoginRequest): Response<LoginResponse> {
        val emailKey = request.email.trim().lowercase()
        val paciente = tablaPacientes[emailKey]
        val expectedHash = "bcrypt_${request.password.hashCode()}"
        return if (paciente != null && paciente.passwordHash == expectedHash) {
            Response.success(
                LoginResponse(
                    success = true,
                    token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.${paciente.id}.medirecord",
                    message = "Inicio de sesión exitoso"
                )
            )
        } else {
            Response.error(401, okhttp3.ResponseBody.create(null, "Credenciales incorrectas"))
        }
    }

    override suspend fun registerMedication(request: MedicationRequest): Response<MedicationResponse> {
        val medId = "pg-med-${tablaMedicamentos.size + 1}"
        val ownerEmail = request.userEmail?.trim()?.lowercase() ?: "aurora.e2e@medirecord.com"

        val medEntity = PgMedicamentoEntity(
            id = medId,
            pacienteEmail = ownerEmail,
            nombre = request.name,
            dosis = request.dosage,
            fechaInicio = request.startDate,
            fechaFin = request.endDate,
            instrucciones = request.instructions
        )
        tablaMedicamentos[medId] = medEntity

        val horarioEntity = PgHorarioEntity(
            id = "pg-sched-${tablaHorarios.size + 1}",
            medicamentoId = medId,
            horaToma = request.scheduledTime,
            frecuencia = request.frequency,
            diasSeleccionados = MedicationRepository.ALL_WEEK_DAYS,
            notas = request.instructions,
            realizado = false
        )
        tablaHorarios[medId] = horarioEntity

        val dose = buildMedicationDose(medEntity, horarioEntity)
        return Response.success(
            MedicationResponse(
                success = true,
                message = "Medicamento '${request.name}' guardado en PostgreSQL",
                medicationId = medId,
                medication = dose
            )
        )
    }

    override suspend fun saveMedicationSchedule(request: ScheduleRequest): Response<ScheduleResponse> {
        val medId = request.medicationId
        val existingMed = tablaMedicamentos[medId]
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, "Medicamento no encontrado"))

        val updatedSchedule = PgHorarioEntity(
            id = tablaHorarios[medId]?.id ?: "pg-sched-${tablaHorarios.size + 1}",
            medicamentoId = medId,
            horaToma = request.scheduledTime,
            frecuencia = request.frequency,
            diasSeleccionados = request.selectedDays,
            notas = request.notes ?: existingMed.instrucciones,
            realizado = tablaHorarios[medId]?.realizado ?: false,
            horaRealizado = tablaHorarios[medId]?.horaRealizado
        )
        tablaHorarios[medId] = updatedSchedule

        val updatedDose = buildMedicationDose(existingMed, updatedSchedule)
        return Response.success(
            ScheduleResponse(
                success = true,
                message = "Horario persistido en PostgreSQL para '${existingMed.nombre}'",
                scheduleId = updatedSchedule.id,
                updatedMedication = updatedDose
            )
        )
    }

    override suspend fun getTodayMedications(
        date: String?,
        userEmail: String?,
    ): Response<List<MedicationDose>> {
        val doses = tablaMedicamentos.values
            .filter { userEmail.isNullOrBlank() || it.pacienteEmail.equals(userEmail, ignoreCase = true) }
            .mapNotNull { med ->
                val horario = tablaHorarios[med.id] ?: return@mapNotNull null
                buildMedicationDose(med, horario, date)
            }
        return Response.success(doses)
    }

    override suspend fun getReminders(userEmail: String?): Response<List<MedicationReminder>> {
        val reminders = tablaMedicamentos.values
            .filter { userEmail.isNullOrBlank() || it.pacienteEmail.equals(userEmail, ignoreCase = true) }
            .mapNotNull { med ->
                val horario = tablaHorarios[med.id] ?: return@mapNotNull null
                MedicationReminder(
                    id = med.id,
                    medicationId = med.id,
                    medicationName = med.nombre,
                    dosage = med.dosis,
                    scheduledTime = horario.horaToma,
                    frequency = horario.frecuencia,
                    selectedDays = horario.diasSeleccionados,
                    instructions = horario.notas ?: med.instrucciones,
                    isCompleted = horario.realizado,
                    userEmail = med.pacienteEmail,
                    completedAt = horario.horaRealizado
                )
            }
        return Response.success(reminders)
    }

    override suspend fun markReminderAsCompleted(
        reminderId: String,
        request: CompleteReminderRequest,
    ): Response<CompleteReminderResponse> {
        val horario = tablaHorarios[reminderId]
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, "Horario no encontrado"))
        val med = tablaMedicamentos[reminderId]
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, "Medicamento no encontrado"))

        val updatedHorario = horario.copy(
            realizado = true,
            horaRealizado = request.completedAt
        )
        tablaHorarios[reminderId] = updatedHorario

        val updatedReminder = MedicationReminder(
            id = med.id,
            medicationId = med.id,
            medicationName = med.nombre,
            dosage = med.dosis,
            scheduledTime = updatedHorario.horaToma,
            frequency = updatedHorario.frecuencia,
            selectedDays = updatedHorario.diasSeleccionados,
            instructions = updatedHorario.notas ?: med.instrucciones,
            isCompleted = true,
            userEmail = med.pacienteEmail,
            completedAt = request.completedAt
        )

        return Response.success(
            CompleteReminderResponse(
                success = true,
                message = "Estado de toma actualizado a REALIZADO en PostgreSQL",
                reminderId = reminderId,
                completedAt = request.completedAt,
                updatedReminder = updatedReminder
            )
        )
    }

    override suspend fun toggleDoseStatus(doseId: String): Response<MedicationDose> {
        val horario = tablaHorarios[doseId]
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, "No encontrado"))
        val med = tablaMedicamentos[doseId]
            ?: return Response.error(404, okhttp3.ResponseBody.create(null, "No encontrado"))
        val toggled = horario.copy(realizado = !horario.realizado)
        tablaHorarios[doseId] = toggled
        return Response.success(buildMedicationDose(med, toggled))
    }

    private fun buildMedicationDose(
        med: PgMedicamentoEntity,
        horario: PgHorarioEntity,
        scheduledDate: String? = MedicationRepository.getCurrentDateIso(),
    ): MedicationDose {
        return MedicationDose(
            id = med.id,
            name = med.nombre,
            dosage = med.dosis,
            scheduledTime = horario.horaToma,
            isCompleted = horario.realizado,
            instructions = horario.notas ?: med.instrucciones,
            frequency = horario.frecuencia,
            selectedDays = horario.diasSeleccionados,
            userEmail = med.pacienteEmail,
            completedAt = horario.horaRealizado,
            scheduledDate = scheduledDate
        )
    }
}

class MvpEndToEndFullFlowTest {

    @Before
    fun setUp() {
        MedicationRepository.completedRemindersRegistry.clear()
        ReminderNotificationManager.clearLogs()
    }

    @Test
    fun testCompleteMvpEndToEndIntegratedFlow() = runBlocking {
        val postgresBackend = StatefulPostgresBackendSimulator()

        val validator = MvpFullFlowValidator(
            apiService = postgresBackend,
            postgresPersistenceVerifier = { medicationId ->
                val medStored = postgresBackend.tablaMedicamentos.containsKey(medicationId)
                val schedStored = postgresBackend.tablaHorarios[medicationId]?.horaToma == "08:30 AM"
                medStored && schedStored
            },
            backendCompletionVerifier = { reminderId ->
                val scheduleRow = postgresBackend.tablaHorarios[reminderId]
                scheduleRow != null && scheduleRow.realizado && scheduleRow.horaRealizado == "08:32 AM"
            }
        )

        val report = validator.executeCompleteMvpFlow()

        // Criterio 11: El flujo completo se ejecuta sin errores bloqueantes
        assertTrue(
            "El flujo completo debía pasar sin errores: ${report.blockingErrors}",
            report.allStepsPassed
        )
        assertEquals(0, report.blockingErrorsCount)
        assertEquals(10, report.steps.size)

        // Verificar que cada uno de los 10 pasos del flujo del PMV fue exitoso
        report.steps.forEach { step ->
            assertTrue("El paso ${step.stepNumber} (${step.stepCode}) falló: ${step.details}", step.passed)
        }

        // Verificar que la notificación fue enviada y recibida durante el flujo
        assertTrue(ReminderNotificationManager.wasNotificationSent("pg-med-1"))
        assertTrue(ReminderNotificationManager.wasNotificationReceived("pg-med-1"))
    }
}
