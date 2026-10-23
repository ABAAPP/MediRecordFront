package com.aba.medirecord.data.model

data class CompleteReminderResponse(
    val success: Boolean,
    val message: String,
    val reminderId: String? = null,
    val completedAt: String? = null,
    val updatedReminder: MedicationReminder? = null,
)
