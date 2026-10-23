package com.aba.medirecord

import com.aba.medirecord.data.model.ScheduleRequest
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.ui.schedule.ScheduleConfigViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScheduleConfigurationTest {

    @Test
    fun testScheduleRequestCreation() {
        val days = listOf("Lunes", "Miércoles", "Viernes")
        val request = ScheduleRequest(
            medicationId = "1",
            medicationName = "Paracetamol",
            scheduledTime = "08:00 AM",
            frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
            selectedDays = days,
            reminderEnabled = true,
            notes = "Tomar después del desayuno"
        )

        assertEquals("1", request.medicationId)
        assertEquals("Paracetamol", request.medicationName)
        assertEquals("08:00 AM", request.scheduledTime)
        assertEquals(ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS, request.frequency)
        assertEquals(days, request.selectedDays)
        assertTrue(request.reminderEnabled)
        assertEquals("Tomar después del desayuno", request.notes)
    }

    @Test
    fun testScheduleValidationSuccess() {
        val errors = ScheduleConfigViewModel.validateScheduleFields(
            medicationId = "2",
            medicationName = "Omeprazol",
            scheduledTime = "09:00 AM",
            frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
            selectedDays = listOf("Lunes", "Miércoles", "Viernes")
        )

        assertFalse(errors.hasErrors)
        assertNull(errors.medicationError)
        assertNull(errors.scheduledTimeError)
        assertNull(errors.frequencyError)
        assertNull(errors.selectedDaysError)
    }

    @Test
    fun testScheduleValidationMissingMedicationAndDays() {
        val errors = ScheduleConfigViewModel.validateScheduleFields(
            medicationId = "",
            medicationName = "",
            scheduledTime = "08:00 AM",
            frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
            selectedDays = emptyList()
        )

        assertTrue(errors.hasErrors)
        assertNotNull(errors.medicationError)
        assertNotNull(errors.selectedDaysError)
    }

    @Test
    fun testScheduleValidationInvalidTimeFormat() {
        val errors = ScheduleConfigViewModel.validateScheduleFields(
            medicationId = "1",
            medicationName = "Paracetamol",
            scheduledTime = "hora-invalida",
            frequency = ScheduleConfigViewModel.FREQ_DAILY,
            selectedDays = ScheduleConfigViewModel.WEEK_DAYS
        )

        assertTrue(errors.hasErrors)
        assertNotNull(errors.scheduledTimeError)
    }

    @Test
    fun testRepositorySaveScheduleSuccess() = runBlocking {
        val fakeApi = FakeApiService()
        val repository = MedicationRepository(fakeApi)

        val request = ScheduleRequest(
            medicationId = "1",
            medicationName = "Paracetamol",
            scheduledTime = "10:30 PM",
            frequency = ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS,
            selectedDays = listOf("Martes", "Jueves"),
            notes = "Antes de dormir"
        )

        val result = repository.saveMedicationSchedule(request)
        assertTrue(result.isSuccess)

        val response = result.getOrNull()
        assertNotNull(response)
        assertTrue(response!!.success)

        val updatedInMemory = MedicationRepository.localDoses.find { it.id == "1" }
        assertNotNull(updatedInMemory)
        assertEquals("10:30 PM", updatedInMemory!!.scheduledTime)
        assertEquals(ScheduleConfigViewModel.FREQ_SPECIFIC_DAYS, updatedInMemory.frequency)
        assertEquals(listOf("Martes", "Jueves"), updatedInMemory.selectedDays)
    }

    @Test
    fun testRepositorySaveScheduleFailure() = runBlocking {
        val fakeApi = FakeApiService().apply { shouldFail = true }
        val repository = MedicationRepository(fakeApi)

        val request = ScheduleRequest(
            medicationId = "2",
            medicationName = "Omeprazol",
            scheduledTime = "07:00 AM",
            frequency = ScheduleConfigViewModel.FREQ_DAILY,
            selectedDays = ScheduleConfigViewModel.WEEK_DAYS
        )

        val result = repository.saveMedicationSchedule(request)
        assertTrue(result.isFailure)
    }
}
