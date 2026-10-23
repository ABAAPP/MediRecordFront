package com.aba.medirecord

import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.repository.MedicationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TodayMedicationsQueryTest {

    @Test
    fun testGetTodayMedicationsUsesCurrentDateAndOrdersBySchedule() = runBlocking {
        val todayIso = MedicationRepository.getCurrentDateIso()
        val fakeApi = FakeApiService().apply {
            simulatedTodayDoses = listOf(
                MedicationDose(
                    id = "d3",
                    name = "Ibuprofeno",
                    dosage = "400 mg - 1 tableta",
                    scheduledTime = "08:00 PM",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com",
                    scheduledDate = todayIso
                ),
                MedicationDose(
                    id = "d1",
                    name = "Paracetamol",
                    dosage = "500 mg - 1 tableta",
                    scheduledTime = "08:00 AM",
                    isCompleted = true,
                    userEmail = "aurora@medirecord.com",
                    scheduledDate = todayIso
                ),
                MedicationDose(
                    id = "d2",
                    name = "Amoxicilina",
                    dosage = "500 mg - 1 cápsula",
                    scheduledTime = "02:00 PM",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com",
                    scheduledDate = todayIso
                )
            )
        }

        val repository = MedicationRepository(fakeApi)
        val result = repository.getTodayMedications(userEmail = "aurora@medirecord.com")

        assertTrue(result.isSuccess)
        // Criterio 1 y 2: La aplicación solicita los medicamentos del día a la API utilizando la fecha actual
        assertEquals(todayIso, fakeApi.lastQueriedDate)
        assertEquals("aurora@medirecord.com", fakeApi.lastQueriedUserEmail)

        val doses = result.getOrNull()!!
        assertEquals(3, doses.size)

        // Criterio 4, 5 y 6: Asociados a su horario, estado identificado y ordenados de acuerdo con su horario
        assertEquals("Paracetamol", doses[0].name)
        assertEquals("08:00 AM", doses[0].scheduledTime)
        assertTrue(doses[0].isCompleted)

        assertEquals("Amoxicilina", doses[1].name)
        assertEquals("02:00 PM", doses[1].scheduledTime)
        assertFalse(doses[1].isCompleted)

        assertEquals("Ibuprofeno", doses[2].name)
        assertEquals("08:00 PM", doses[2].scheduledTime)
        assertFalse(doses[2].isCompleted)
    }

    @Test
    fun testOnlyUserMedicationsForCurrentDateAreShown() = runBlocking {
        val todayIso = MedicationRepository.getCurrentDateIso()
        val fakeApi = FakeApiService().apply {
            simulatedTodayDoses = listOf(
                MedicationDose(
                    id = "d1",
                    name = "Omeprazol",
                    dosage = "20 mg",
                    scheduledTime = "09:00 AM",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com",
                    scheduledDate = todayIso
                ),
                MedicationDose(
                    id = "d2",
                    name = "Metformina",
                    dosage = "850 mg",
                    scheduledTime = "10:00 AM",
                    isCompleted = false,
                    userEmail = "otro@correo.com",
                    scheduledDate = todayIso
                ),
                MedicationDose(
                    id = "d3",
                    name = "Loratadina",
                    dosage = "10 mg",
                    scheduledTime = "11:00 AM",
                    isCompleted = false,
                    userEmail = "aurora@medirecord.com",
                    scheduledDate = "2099-12-31"
                )
            )
        }

        val repository = MedicationRepository(fakeApi)
        val result = repository.getTodayMedications(date = todayIso, userEmail = "aurora@medirecord.com")

        assertTrue(result.isSuccess)
        val doses = result.getOrNull()!!
        assertEquals(1, doses.size)
        assertEquals("Omeprazol", doses[0].name)
        assertEquals("aurora@medirecord.com", doses[0].userEmail)
    }

    @Test
    fun testEmptyMedicationsForTodayIsHandled() = runBlocking {
        val todayIso = MedicationRepository.getCurrentDateIso()
        val fakeApi = FakeApiService().apply {
            simulatedTodayDoses = emptyList()
        }

        val repository = MedicationRepository(fakeApi)
        val result = repository.getTodayMedications(date = todayIso, userEmail = "aurora@medirecord.com")

        assertTrue(result.isSuccess)
        val doses = result.getOrNull()!!
        assertTrue(doses.isEmpty())
    }
}
