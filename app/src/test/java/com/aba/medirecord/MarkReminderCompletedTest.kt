package com.aba.medirecord

import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.repository.MedicationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MarkReminderCompletedTest {

    @Test
    fun testMarkReminderAsCompletedSuccessAndBackendConfirmation() = runBlocking {
        val fakeApi = FakeApiService().apply {
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "rem-200",
                    medicationId = "2",
                    medicationName = "Omeprazol",
                    dosage = "20 mg - 1 cápsula",
                    scheduledTime = "09:00 AM",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com"
                )
            )
        }

        val repository = MedicationRepository(fakeApi)
        val result = repository.markReminderAsCompleted(
            reminderId = "rem-200",
            userEmail = "aurora@medirecord.com",
            completedAt = "09:05 AM"
        )

        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response!!.success)
        assertEquals("rem-200", response.reminderId)
        assertEquals("09:05 AM", response.completedAt)
        assertTrue(response.message.isNotBlank())
    }

    @Test
    fun testCompletedStatePersistsAfterRefreshAndRequery() = runBlocking {
        // Incluso si el servidor devuelve isCompleted = false al volver a consultar,
        // el repositorio conserva el estado "Realizado" sincronizado en la sesión
        val fakeApi = FakeApiService().apply {
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "rem-300",
                    medicationId = "3",
                    medicationName = "Amoxicilina",
                    dosage = "500 mg - 1 cápsula",
                    scheduledTime = "02:00 PM",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com"
                )
            )
        }

        val repository = MedicationRepository(fakeApi)

        // 1. Marcar como realizado
        val completeResult = repository.markReminderAsCompleted(
            reminderId = "rem-300",
            userEmail = "aurora@medirecord.com",
            completedAt = "02:03 PM"
        )
        assertTrue(completeResult.isSuccess)

        // 2. Volver a consultar los recordatorios (simulando actualización / recarga de pantalla)
        val refreshResult = repository.getRemindersForUser("aurora@medirecord.com")
        assertTrue(refreshResult.isSuccess)

        val refreshedReminder = refreshResult.getOrNull()!!.find { it.id == "rem-300" }
        assertNotNull(refreshedReminder)
        assertTrue(refreshedReminder!!.isCompleted)
        assertEquals("02:03 PM", refreshedReminder.completedAt)
    }

    @Test
    fun testMarkReminderAsCompletedFailureReturnsErrorMessage() = runBlocking {
        val fakeApi = FakeApiService().apply {
            shouldFail = true
        }
        val repository = MedicationRepository(fakeApi)

        val result = repository.markReminderAsCompleted(
            reminderId = "rem-500",
            userEmail = "aurora@medirecord.com"
        )

        assertTrue(result.isFailure)
        val errorMessage = result.exceptionOrNull()?.message
        assertNotNull(errorMessage)
        assertTrue(errorMessage!!.contains("No se pudo completar la operación"))
    }

    @Test
    fun testMarkReminderWithBlankIdFails() = runBlocking {
        val fakeApi = FakeApiService()
        val repository = MedicationRepository(fakeApi)

        val result = repository.markReminderAsCompleted(
            reminderId = "   ",
            userEmail = "aurora@medirecord.com"
        )

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }
}
