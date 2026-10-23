package com.aba.medirecord.data.model

data class MedicationDose(
    val id: String,
    val name: String,
    val dosage: String,
    val scheduledTime: String,
    val isCompleted: Boolean = false,
    val instructions: String? = null,
    val frequency: String? = null,
    val selectedDays: List<String> = emptyList(),
)
