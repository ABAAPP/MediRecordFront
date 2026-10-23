package com.aba.medirecord

import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.repository.AuthRepository
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.ui.medication.AddMedicationViewModel
import com.aba.medirecord.ui.reminders.ReminderFilter
import com.aba.medirecord.ui.reminders.RemindersViewModel
import com.aba.medirecord.ui.schedule.ScheduleConfigViewModel
import com.aba.medirecord.util.ValidationUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Suite de Pruebas Funcionales Integrales del PMV (F-13).
 * Verifica escenarios exitosos, escenarios con datos inválidos y re-prueba de defectos corregidos.
 */
class MvpFunctionalTestSuite {

    // =========================================================================
    // 1. PRUEBAS FUNCIONALES DE REGISTRO DE USUARIO (Escenarios Exitosos e Inválidos)
    // =========================================================================
    @Test
    fun testFunctionalUserRegistration_SuccessAndInvalidScenarios() = runBlocking {
        val fakeApi = FakeApiService()
        val authRepository = AuthRepository(apiService = fakeApi)

        // Escenario con datos inválidos: nombre vacío, correo mal formado, contraseña corta
        val invalidValidation = ValidationUtils.validateRegisterFields(
            name = "A",
            email = "correo_invalido.com",
            password = "123"
        )
        assertTrue(invalidValidation.hasErrors)
        assertNotNull(invalidValidation.nameError)
        assertNotNull(invalidValidation.emailError)
        assertNotNull(invalidValidation.passwordError)

        // Escenario exitoso: datos válidos y confirmación de registro
        val validValidation = ValidationUtils.validateRegisterFields(
            name = "Aurora Bautista",
            email = "aurora.pmv@medirecord.com",
            password = "passwordSegura123"
        )
        assertFalse(validValidation.hasErrors)

        val registerResult = authRepository.registerUser(
            name = "Aurora Bautista",
            email = "aurora.pmv@medirecord.com",
            pass = "passwordSegura123"
        )
        assertTrue(registerResult.isSuccess)
        assertTrue(registerResult.getOrNull()!!.success)
    }

    // =========================================================================
    // 2. PRUEBAS FUNCIONALES DE INICIO DE SESIÓN (Escenarios Exitosos e Inválidos)
    // =========================================================================
    @Test
    fun testFunctionalUserLogin_SuccessAndInvalidScenarios() = runBlocking {
        val fakeApi = FakeApiService()
        val authRepository = AuthRepository(apiService = fakeApi)

        // Escenario inválido: correo vacío o formato incorrecto
        val invalidLogin = ValidationUtils.validateLoginFields(email = "sin-arroba", password = "")
        assertTrue(invalidLogin.hasErrors)
        assertNotNull(invalidLogin.emailError)
        assertNotNull(invalidLogin.passwordError)

        // Escenario inválido: credenciales rechazadas por el servidor (401)
        fakeApi.shouldFail = true
        val rejectedResult = authRepository.loginUser("aurora.pmv@medirecord.com", "claveErronea")
        assertTrue(rejectedResult.isFailure)

        // Escenario exitoso: inicio de sesión con generación de JWT
        fakeApi.shouldFail = false
        val successResult = authRepository.loginUser("aurora.pmv@medirecord.com", "passwordSegura123")
        assertTrue(successResult.isSuccess)
        assertTrue(authRepository.isLoggedIn())
        assertNotNull(authRepository.getAuthorizationHeader())
    }

    // =========================================================================
    // 3. PRUEBAS FUNCIONALES DE REGISTRO DE MEDICAMENTOS
    // =========================================================================
    @Test
    fun testFunctionalMedicationRegistration_SuccessAndInvalidScenarios() = runBlocking {
        val fakeApi = FakeApiService()
        val repository = MedicationRepository(fakeApi)

        // Escenario inválido: fecha de finalización anterior a la fecha de inicio
        val invalidMedErrors = AddMedicationViewModel.validateMedicationFields(
            name = "Naproxeno",
            dosage = "500 mg",
            startDate = "25/10/2026",
            endDate = "20/10/2026",
            isContinuous = false,
            frequency = "Cada 12 horas (2 veces al día)",
            scheduledTime = "08:00 AM"
        )
        assertTrue(invalidMedErrors.hasErrors)
        assertNotNull(invalidMedErrors.endDateError)

        // Escenario exitoso: registro de medicamento válido
        val validMedErrors = AddMedicationViewModel.validateMedicationFields(
            name = "Naproxeno",
            dosage = "500 mg - 1 tableta",
            startDate = "22/10/2026",
            endDate = "30/10/2026",
            isContinuous = false,
            frequency = "Cada 12 horas (2 veces al día)",
            scheduledTime = "08:00 AM"
        )
        assertFalse(validMedErrors.hasErrors)

        val response = repository.registerMedication(
            MedicationRequest(
                name = "Naproxeno",
                dosage = "500 mg - 1 tableta",
                startDate = "22/10/2026",
                endDate = "30/10/2026",
                frequency = "Cada 12 horas (2 veces al día)",
                scheduledTime = "08:00 AM",
                instructions = "Después de desayunar",
                userEmail = "aurora.pmv@medirecord.com"
            )
        )
        assertTrue(response.isSuccess)
        assertTrue(response.getOrNull()!!.success)
    }

    // =========================================================================
    // 4. PRUEBAS FUNCIONALES DE CONFIGURACIÓN DE HORARIOS
    // =========================================================================
    @Test
    fun testFunctionalScheduleConfiguration_SuccessAndInvalidScenarios() = runBlocking {
        val fakeApi = FakeApiService()
        val repository = MedicationRepository(fakeApi)

        // Escenario inválido: frecuencia de días específicos sin ningún día seleccionado
        val invalidSchedule = ScheduleConfigViewModel.validateScheduleFields(
            medicationId = "1",
            medicationName = "Paracetamol",
            scheduledTime = "08:00 AM",
            frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
            selectedDays = emptyList()
        )
        assertTrue(invalidSchedule.hasErrors)
        assertNotNull(invalidSchedule.selectedDaysError)

        // Escenario exitoso: configuración de horario con días específicos
        val validSchedule = ScheduleConfigViewModel.validateScheduleFields(
            medicationId = "1",
            medicationName = "Paracetamol",
            scheduledTime = "08:30 AM",
            frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
            selectedDays = listOf("Lunes", "Miércoles", "Viernes")
        )
        assertFalse(validSchedule.hasErrors)

        val saveResult = repository.saveMedicationSchedule(
            ScheduleRequest(
                medicationId = "1",
                medicationName = "Paracetamol",
                scheduledTime = "08:30 AM",
                frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
                selectedDays = listOf("Lunes", "Miércoles", "Viernes"),
                notes = "Con un vaso de agua"
            )
        )
        assertTrue(saveResult.isSuccess)
    }

    // =========================================================================
    // 5. PRUEBAS FUNCIONALES DE VISUALIZACIÓN DE RECORDATORIOS Y MEDICAMENTOS DEL DÍA
    // =========================================================================
    @Test
    fun testFunctionalRemindersAndTodayMedicationsQuery() = runBlocking {
        val todayIso = MedicationRepository.getCurrentDateIso()
        val userEmail = "aurora.pmv@medirecord.com"

        val fakeApi = FakeApiService().apply {
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "r-pm",
                    medicationId = "2",
                    medicationName = "Loratadina",
                    dosage = "10 mg",
                    scheduledTime = "09:00 PM",
                    isCompleted = false,
                    userEmail = userEmail
                ),
                MedicationReminder(
                    id = "r-am",
                    medicationId = "1",
                    medicationName = "Vitamina C",
                    dosage = "500 mg",
                    scheduledTime = "07:30 AM",
                    isCompleted = true,
                    userEmail = userEmail
                )
            )
            simulatedTodayDoses = listOf(
                MedicationDose(
                    id = "d-pm",
                    name = "Loratadina",
                    dosage = "10 mg",
                    scheduledTime = "09:00 PM",
                    isCompleted = false,
                    userEmail = userEmail,
                    scheduledDate = todayIso
                ),
                MedicationDose(
                    id = "d-am",
                    name = "Vitamina C",
                    dosage = "500 mg",
                    scheduledTime = "07:30 AM",
                    isCompleted = true,
                    userEmail = userEmail,
                    scheduledDate = todayIso
                )
            )
        }

        val repository = MedicationRepository(fakeApi)

        // Visualización de recordatorios ordenados (07:30 AM antes de 09:00 PM)
        val remindersResult = repository.getRemindersForUser(userEmail)
        assertTrue(remindersResult.isSuccess)
        val reminders = remindersResult.getOrNull()!!
        assertEquals(2, reminders.size)
        assertEquals("Vitamina C", reminders[0].medicationName)
        assertEquals("Loratadina", reminders[1].medicationName)

        // Distinción de pendientes vs realizadas
        val pendingOnly = RemindersViewModel.applyFilter(reminders, ReminderFilter.PENDING)
        assertEquals(1, pendingOnly.size)
        assertEquals("Loratadina", pendingOnly[0].medicationName)

        // Consulta de medicamentos del día con fecha actual
        val todayResult = repository.getTodayMedications(date = todayIso, userEmail = userEmail)
        assertTrue(todayResult.isSuccess)
        val todayDoses = todayResult.getOrNull()!!
        assertEquals(2, todayDoses.size)
        assertEquals("Vitamina C", todayDoses[0].name)
        assertEquals("Loratadina", todayDoses[1].name)
    }

    // =========================================================================
    // 6. PRUEBAS FUNCIONALES DE MARCADO DE TOMAS REALIZADAS Y RE-PRUEBA DE DEFECTOS
    // =========================================================================
    @Test
    fun testFunctionalMarkCompletedAndRetestFixedDefects() = runBlocking {
        // Re-prueba DEF-01: Horas fuera de rango (ej. "15:00 AM" o "10:75 AM") deben ser rechazadas
        assertFalse(ValidationUtils.isValidTimeFormat("15:00 AM"))
        assertFalse(ValidationUtils.isValidTimeFormat("10:75 AM"))
        assertFalse(ValidationUtils.isValidTimeFormat("00:30 PM"))
        assertTrue(ValidationUtils.isValidTimeFormat("08:30 AM"))
        assertTrue(ValidationUtils.isValidTimeFormat("12:00 PM"))

        // Re-prueba DEF-02: Normalización de correo electrónico con mayúsculas/espacios en AuthRepository
        val fakeApi = FakeApiService().apply {
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "rem-retest",
                    medicationId = "10",
                    medicationName = "Metformina",
                    dosage = "850 mg",
                    scheduledTime = "01:00 PM",
                    isCompleted = false,
                    userEmail = "aurora.pmv@medirecord.com"
                )
            )
        }
        val authRepo = AuthRepository(fakeApi)
        authRepo.saveSession("jwt_test", "  AURORA.PMV@MediRecord.com ")
        assertEquals("aurora.pmv@medirecord.com", authRepo.getUserEmail())

        // Re-prueba DEF-03: Marcado de toma realizada y actualización de hora más reciente tras re-consulta
        val medRepo = MedicationRepository(fakeApi)
        val firstComplete = medRepo.markReminderAsCompleted(
            reminderId = "rem-retest",
            userEmail = authRepo.getUserEmail(),
            completedAt = "01:02 PM"
        )
        assertTrue(firstComplete.isSuccess)

        val secondComplete = medRepo.markReminderAsCompleted(
            reminderId = "rem-retest",
            userEmail = authRepo.getUserEmail(),
            completedAt = "01:05 PM"
        )
        assertTrue(secondComplete.isSuccess)

        val refreshed = medRepo.getRemindersForUser(authRepo.getUserEmail()).getOrNull()!!
        val updatedItem = refreshed.find { it.id == "rem-retest" }
        assertNotNull(updatedItem)
        assertTrue(updatedItem!!.isCompleted)
        assertEquals("01:05 PM", updatedItem.completedAt)
    }
}
