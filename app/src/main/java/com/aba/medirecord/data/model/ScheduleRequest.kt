package com.aba.medirecord.data.model

data class ScheduleRequest(
    val medicationId: String,
    val medicationName: String,
    val scheduledTime: String,
    val frequency: String,
    val selectedDays: List<String> = emptyList(),
    val reminderEnabled: Boolean = true,
    val notes: String? = null,
)
