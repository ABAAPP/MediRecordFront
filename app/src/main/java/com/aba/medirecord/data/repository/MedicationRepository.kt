package com.aba.medirecord.data.repository

import com.aba.medirecord.data.model.MedicationDose
import com.aba.medirecord.data.network.ApiService
import kotlinx.coroutines.withTimeoutOrNull

class MedicationRepository(
    private val apiService: ApiService,
) {
    // Lista en memoria para simulación cuando no hay backend activo
    private val localDoses = mutableListOf(
        MedicationDose("1", "Paracetamol", "500 mg - 1 tableta", "08:00 AM", isCompleted = true, instructions = "Con alimentos"),
        MedicationDose("2", "Omeprazol", "20 mg - 1 cápsula", "09:00 AM", isCompleted = false, instructions = "En ayunas"),
        MedicationDose("3", "Amoxicilina", "500 mg - 1 cápsula", "02:00 PM", isCompleted = false, instructions = "Tomar con agua"),
        MedicationDose("4", "Ibuprofeno", "400 mg - 1 tableta", "08:00 PM", isCompleted = false, instructions = "Después de cenar"),
    )

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
