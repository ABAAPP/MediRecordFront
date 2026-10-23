package com.aba.medirecord.notifications

enum class NotificationDeliveryStatus {
    SCHEDULED,
    SENT,
    RECEIVED,
    FAILED
}

data class ReminderNotificationPayload(
    val reminderId: String,
    val medicationId: String,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val frequency: String = "Todos los días (Diario)",
    val selectedDays: List<String> = emptyList(),
    val instructions: String? = null,
    val triggerAtMillis: Long = 0L,
)

data class NotificationEventRecord(
    val eventId: String,
    val reminderId: String,
    val medicationName: String,
    val dosage: String,
    val scheduledTime: String,
    val title: String,
    val message: String,
    val status: NotificationDeliveryStatus,
    val timestampMillis: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
)
