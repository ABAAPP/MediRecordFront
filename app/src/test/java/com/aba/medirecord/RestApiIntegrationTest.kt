package com.aba.medirecord

import com.aba.medirecord.data.local.TokenManager
import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.model.MedicationRequest
import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.network.JwtAuthInterceptor
import com.aba.medirecord.data.repository.AuthRepository
import com.aba.medirecord.data.repository.MedicationRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class RestApiIntegrationTest {

    @Test
    fun testJwtAuthenticationHeaderAndPublicEndpoints() {
        val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature"

        // 1. Endpoint protegido debe incluir Authorization: Bearer <jwt> y Accept: application/json
        val protectedRequest = Request.Builder()
            .url("http://10.0.2.2:8080/api/medications/today")
            .build()

        val authenticatedRequest = JwtAuthInterceptor.applyHeadersToRequest(protectedRequest, sampleJwt)
        assertEquals("Bearer $sampleJwt", authenticatedRequest.header("Authorization"))
        assertEquals("application/json", authenticatedRequest.header("Accept"))

        // 2. Endpoint público de login no requiere adjuntar token previo
        val loginRequest = Request.Builder()
            .url("http://10.0.2.2:8080/api/auth/login")
            .build()

        val publicRequest = JwtAuthInterceptor.applyHeadersToRequest(loginRequest, sampleJwt)
        assertNull(publicRequest.header("Authorization"))
        assertEquals("application/json", publicRequest.header("Accept"))
    }

    @Test
    fun testUserRegistrationAndJwtLoginIntegration() = runBlocking {
        val fakeApi = FakeApiService()
        val authRepository = AuthRepository(apiService = fakeApi)

        // Criterio 3: Se pueden registrar usuarios desde la aplicación
        val registerResult = authRepository.registerUser(
            name = "Aurora Bautista",
            email = "aurora@medirecord.com",
            pass = "segura123"
        )
        assertTrue(registerResult.isSuccess)
        assertTrue(registerResult.getOrNull()!!.success)

        // Criterio 2 y 4: Se puede iniciar sesión y la autenticación JWT funciona correctamente
        val loginResult = authRepository.loginUser(
            email = "aurora@medirecord.com",
            pass = "segura123"
        )
        assertTrue(loginResult.isSuccess)
        val loginResponse = loginResult.getOrNull()!!
        assertTrue(loginResponse.success)
        assertNotNull(loginResponse.token)
        assertTrue(authRepository.isLoggedIn())
        assertEquals("aurora@medirecord.com", authRepository.getUserEmail())
        assertEquals(
            TokenManager.formatBearerHeader(loginResponse.token),
            authRepository.getAuthorizationHeader()
        )
    }

    @Test
    fun testFullEndToEndMedicationScheduleReminderAndCompletionFlow() = runBlocking {
        val todayIso = MedicationRepository.getCurrentDateIso()
        val userEmail = "aurora@medirecord.com"

        val fakeApi = FakeApiService().apply {
            simulatedTodayDoses = listOf(
                MedicationDose(
                    id = "med-900",
                    name = "Atorvastatina",
                    dosage = "20 mg - 1 tableta",
                    scheduledTime = "09:00 PM",
                    isCompleted = false,
                    userEmail = userEmail,
                    scheduledDate = todayIso
                )
            )
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "med-900",
                    medicationId = "med-900",
                    medicationName = "Atorvastatina",
                    dosage = "20 mg - 1 tableta",
                    scheduledTime = "09:00 PM",
                    frequency = "Todos los días (Diario)",
                    selectedDays = MedicationRepository.ALL_WEEK_DAYS,
                    isCompleted = false,
                    userEmail = userEmail
                )
            )
        }

        val medicationRepository = MedicationRepository(fakeApi)

        // Criterio 5: Se pueden registrar medicamentos
        val medRegisterResult = medicationRepository.registerMedication(
            MedicationRequest(
                name = "Atorvastatina",
                dosage = "20 mg - 1 tableta",
                startDate = "22/10/2026",
                endDate = null,
                frequency = "Todos los días (Diario)",
                scheduledTime = "09:00 PM",
                instructions = "Antes de dormir"
            )
        )
        assertTrue(medRegisterResult.isSuccess)

        // Criterio 6: Se pueden registrar horarios
        val scheduleResult = medicationRepository.saveMedicationSchedule(
            ScheduleRequest(
                medicationId = "med-900",
                medicationName = "Atorvastatina",
                scheduledTime = "09:30 PM",
                frequency = "Todos los días (Diario)",
                selectedDays = MedicationRepository.ALL_WEEK_DAYS,
                notes = "Tomar con agua"
            )
        )
        assertTrue(scheduleResult.isSuccess)

        // Criterio 7: Se pueden consultar recordatorios
        val remindersResult = medicationRepository.getRemindersForUser(userEmail)
        assertTrue(remindersResult.isSuccess)
        assertFalse(remindersResult.getOrNull()!!.isEmpty())

        // Criterio 8: Se pueden consultar los medicamentos del día
        val todayResult = medicationRepository.getTodayMedications(date = todayIso, userEmail = userEmail)
        assertTrue(todayResult.isSuccess)
        assertFalse(todayResult.getOrNull()!!.isEmpty())

        // Criterio 9 y 10: Se pueden registrar tomas realizadas y las respuestas exitosas se procesan
        val completeResult = medicationRepository.markReminderAsCompleted(
            reminderId = "med-900",
            userEmail = userEmail,
            completedAt = "09:32 PM"
        )
        assertTrue(completeResult.isSuccess)
        assertTrue(completeResult.getOrNull()!!.success)
    }

    @Test
    fun testApiErrorsAreHandledCorrectlyAcrossEndpoints() = runBlocking {
        val fakeApi = FakeApiService().apply {
            shouldFail = true
        }
        val authRepository = AuthRepository(apiService = fakeApi)
        val medicationRepository = MedicationRepository(fakeApi)

        // Criterio 11: Los errores de la API son manejados correctamente
        val failedLogin = authRepository.loginUser("aurora@medirecord.com", "wrongpass")
        assertTrue(failedLogin.isFailure)

        val failedRegister = authRepository.registerUser("Aurora", "aurora@medirecord.com", "123456")
        assertTrue(failedRegister.isFailure)

        val failedMed = medicationRepository.registerMedication(
            MedicationRequest(
                name = "Ibuprofeno",
                dosage = "400 mg",
                startDate = "22/10/2026",
                frequency = "Cada 8 horas",
                scheduledTime = "08:00 AM"
            )
        )
        assertTrue(failedMed.isFailure)

        val failedSchedule = medicationRepository.saveMedicationSchedule(
            ScheduleRequest(
                medicationId = "1",
                medicationName = "Ibuprofeno",
                scheduledTime = "08:00 AM",
                frequency = "Cada 8 horas",
                selectedDays = MedicationRepository.ALL_WEEK_DAYS
            )
        )
        assertTrue(failedSchedule.isFailure)

        val failedComplete = medicationRepository.markReminderAsCompleted("1", "aurora@medirecord.com")
        assertTrue(failedComplete.isFailure)
    }
}
