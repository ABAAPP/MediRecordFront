package com.aba.medirecord.data.model

data class MedicationReminder(
    val id: String,
    val medicationId: String,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val frequency: String = "Todos los días (Diario)",
    val selectedDays: List<String> = emptyList(),
    val instructions: String? = null,
    val isCompleted: Boolean = false,
    val userEmail: String? = null,
)
