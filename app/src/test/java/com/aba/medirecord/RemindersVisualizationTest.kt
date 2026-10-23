package com.aba.medirecord

import com.aba.medirecord.data.model.MedicationReminder
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.ui.reminders.ReminderFilter
import com.aba.medirecord.ui.reminders.RemindersViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemindersVisualizationTest {

    @Test
    fun testGetRemindersFromApiAndChronologicalOrder() = runBlocking {
        val fakeApi = FakeApiService().apply {
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "r3",
                    medicationId = "3",
                    medicationName = "Ibuprofeno",
                    dosage = "400 mg - 1 tableta",
                    scheduledTime = "08:00 PM",
                    frequency = "Días específicos de la semana",
                    selectedDays = listOf("Lunes", "Miércoles", "Viernes"),
                    instructions = "Después de cenar",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com"
                ),
                MedicationReminder(
                    id = "r1",
                    medicationId = "1",
                    medicationName = "Paracetamol",
                    dosage = "500 mg - 1 tableta",
                    scheduledTime = "08:00 AM",
                    frequency = "Cada 8 horas (3 veces al día)",
                    selectedDays = MedicationRepository.ALL_WEEK_DAYS,
                    instructions = "Con alimentos",
                    isCompleted = true,
                    userEmail = "aurora@medirecord.com"
                ),
                MedicationReminder(
                    id = "r2",
                    medicationId = "2",
                    medicationName = "Amoxicilina",
                    dosage = "500 mg - 1 cápsula",
                    scheduledTime = "02:00 PM",
                    frequency = "Cada 8 horas (3 veces al día)",
                    selectedDays = MedicationRepository.ALL_WEEK_DAYS,
                    instructions = "Tomar con agua",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com"
                ),
            )
        }

        val repository = MedicationRepository(fakeApi)
        val result = repository.getRemindersForUser("aurora@medirecord.com")

        assertTrue(result.isSuccess)
        val reminders = result.getOrNull()!!
        assertEquals(3, reminders.size)

        // Verificar que se presentan ordenados cronológicamente (08:00 AM -> 02:00 PM -> 08:00 PM)
        assertEquals("Paracetamol", reminders[0].medicationName)
        assertEquals("08:00 AM", reminders[0].scheduledTime)
        assertEquals("500 mg - 1 tableta", reminders[0].dosage)

        assertEquals("Amoxicilina", reminders[1].medicationName)
        assertEquals("02:00 PM", reminders[1].scheduledTime)

        assertEquals("Ibuprofeno", reminders[2].medicationName)
        assertEquals("08:00 PM", reminders[2].scheduledTime)
    }

    @Test
    fun testOnlyAuthenticatedUserRemindersAreShown() = runBlocking {
        val fakeApi = FakeApiService().apply {
            simulatedReminders = listOf(
                MedicationReminder(
                    id = "r1",
                    medicationId = "1",
                    medicationName = "Losartán",
                    dosage = "50 mg - 1 tableta",
                    scheduledTime = "09:00 AM",
                    userEmail = "aurora@medirecord.com"
                ),
                MedicationReminder(
                    id = "r2",
                    medicationId = "2",
                    medicationName = "Metformina",
                    dosage = "850 mg - 1 tableta",
                    scheduledTime = "10:00 AM",
                    userEmail = "otro.usuario@correo.com"
                )
            )
        }

        val repository = MedicationRepository(fakeApi)
        val result = repository.getRemindersForUser("aurora@medirecord.com")

        assertTrue(result.isSuccess)
        val userReminders = result.getOrNull()!!
        assertEquals(1, userReminders.size)
        assertEquals("Losartán", userReminders[0].medicationName)
        assertEquals("aurora@medirecord.com", userReminders[0].userEmail)
    }

    @Test
    fun testUnauthenticatedUserCannotAccessReminders() = runBlocking {
        val fakeApi = FakeApiService()
        val repository = MedicationRepository(fakeApi)

        val result = repository.getRemindersForUser("")
        assertTrue(result.isFailure)
    }

    @Test
    fun testDistinguishPendingAndCompletedReminders() {
        val sample = listOf(
            MedicationReminder(
                id = "1",
                medicationId = "1",
                medicationName = "Paracetamol",
                dosage = "500 mg",
                scheduledTime = "08:00 AM",
                isCompleted = true
            ),
            MedicationReminder(
                id = "2",
                medicationId = "2",
                medicationName = "Omeprazol",
                dosage = "20 mg",
                scheduledTime = "09:00 AM",
                isCompleted = false
            )
        )

        val pending = RemindersViewModel.applyFilter(sample, ReminderFilter.PENDING)
        val completed = RemindersViewModel.applyFilter(sample, ReminderFilter.COMPLETED)

        assertEquals(1, pending.size)
        assertFalse(pending[0].isCompleted)
        assertEquals("Omeprazol", pending[0].medicationName)

        assertEquals(1, completed.size)
        assertTrue(completed[0].isCompleted)
        assertEquals("Paracetamol", completed[0].medicationName)
    }
}
