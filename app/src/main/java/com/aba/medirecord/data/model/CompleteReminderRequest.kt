package com.aba.medirecord.data.model

data class CompleteReminderRequest(
    val reminderId: String,
    val completedAt: String,
    val userEmail: String? = null,
)
