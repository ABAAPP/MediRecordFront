package com.aba.medirecord

import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.ui.medication.AddMedicationViewModel
import com.aba.medirecord.util.UserMessageMapper
import com.aba.medirecord.util.ValidationUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ValidationAndUserMessagesTest {

    @Test
    fun testIncompleteDataShowsClearMessages() {
        // Login con campos vacíos
        val loginErrors = ValidationUtils.validateLoginFields(email = "", password = "")
        assertTrue(loginErrors.hasErrors)
        assertEquals("Por favor ingresa tu correo electrónico", loginErrors.emailError)
        assertEquals("Por favor ingresa tu contraseña", loginErrors.passwordError)

        // Registro con nombre vacío
        val registerErrors = ValidationUtils.validateRegisterFields(
            name = "   ",
            email = "aurora@medirecord.com",
            password = "password123"
        )
        assertTrue(registerErrors.hasErrors)
        assertEquals("Por favor ingresa tu nombre completo", registerErrors.nameError)
        assertNull(registerErrors.emailError)
        assertNull(registerErrors.passwordError)
    }

    @Test
    fun testInvalidDataFormatShowsClearMessages() {
        // Correo con formato incorrecto y contraseña corta
        val registerErrors = ValidationUtils.validateRegisterFields(
            name = "Aurora",
            email = "correo-sin-arroba",
            password = "123"
        )
        assertTrue(registerErrors.hasErrors)
        assertNotNull(registerErrors.emailError)
        assertTrue(registerErrors.emailError!!.contains("formato"))
        assertNotNull(registerErrors.passwordError)
        assertTrue(registerErrors.passwordError!!.contains("6 caracteres"))

        // Medicamento con formato de fecha y hora incorrectos
        val medErrors = AddMedicationViewModel.validateMedicationFields(
            name = "Paracetamol",
            dosage = "500 mg",
            startDate = "2026-99-99",
            endDate = "fecha-invalida",
            isContinuous = false,
            frequency = "Cada 8 horas",
            scheduledTime = "25:99 XX"
        )
        assertTrue(medErrors.hasErrors)
        assertNotNull(medErrors.startDateError)
        assertNotNull(medErrors.endDateError)
        assertNotNull(medErrors.scheduledTimeError)
    }

    @Test
    fun testConnectionErrorsAreHandledWithFriendlyMessages() {
        val timeoutMsg = UserMessageMapper.mapConnectionError(SocketTimeoutException("Read timed out"))
        val hostMsg = UserMessageMapper.mapConnectionError(UnknownHostException("Unable to resolve host 10.0.2.2"))
        val connectMsg = UserMessageMapper.mapConnectionError(ConnectException("Failed to connect to /10.0.2.2:8080"))

        assertFalse(timeoutMsg.contains("SocketTimeoutException"))
        assertFalse(hostMsg.contains("UnknownHostException"))
        assertFalse(connectMsg.contains("10.0.2.2"))
        assertTrue(timeoutMsg.contains("conexión", ignoreCase = true))
        assertTrue(hostMsg.contains("servidor", ignoreCase = true))
    }

    @Test
    fun testApiErrorsAndTechnicalDetailsAreSanitizedForUser() {
        // Error 500 con traza técnica y JSON crudo
        val rawTechnicalError = """
            {"status":500,"error":"Internal Server Error","trace":"java.lang.NullPointerException at com.api.Controller(Controller.java:42)"}
        """.trimIndent()

        val userMessage = UserMessageMapper.mapApiError(
            httpCode = 500,
            rawErrorBody = rawTechnicalError
        )

        // Criterio 6 y 7: Comprensible para el usuario y sin información técnica innecesaria
        assertFalse(userMessage.contains("NullPointerException"))
        assertFalse(userMessage.contains("500"))
        assertFalse(userMessage.contains("{"))
        assertTrue(userMessage.contains("No se pudo completar la operación"))
    }

    @Test
    fun testApplicationDoesNotBlockOnBackendCommunicationError() = runBlocking {
        // Si el backend falla al consultar medicamentos del día, la aplicación no se bloquea y responde de forma segura
        val fakeApi = FakeApiService().apply {
            shouldFail = true
        }
        val repository = MedicationRepository(fakeApi)

        val todayResult = repository.getTodayMedications(userEmail = "aurora@medirecord.com")
        assertTrue(todayResult.isSuccess)
        assertNotNull(todayResult.getOrNull())
    }
}
