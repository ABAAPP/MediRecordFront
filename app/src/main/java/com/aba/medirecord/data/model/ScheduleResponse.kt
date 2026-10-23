package com.aba.medirecord.data.model

data class ScheduleResponse(
    val success: Boolean,
    val message: String,
    val scheduleId: String? = null,
    val updatedMedication: MedicationDose? = null,
)
