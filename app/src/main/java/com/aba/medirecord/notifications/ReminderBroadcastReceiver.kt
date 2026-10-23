package com.aba.medirecord.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(ReminderNotificationManager.EXTRA_REMINDER_ID).orEmpty()
        val medicationId = intent.getStringExtra(ReminderNotificationManager.EXTRA_MEDICATION_ID).orEmpty()
        val medicationName = intent.getStringExtra(ReminderNotificationManager.EXTRA_MEDICATION_NAME).orEmpty()
        val dosage = intent.getStringExtra(ReminderNotificationManager.EXTRA_DOSAGE).orEmpty()
        val scheduledTime = intent.getStringExtra(ReminderNotificationManager.EXTRA_SCHEDULED_TIME).orEmpty()
        val frequency = intent.getStringExtra(ReminderNotificationManager.EXTRA_FREQUENCY)
            ?: "Todos los días (Diario)"
        val instructions = intent.getStringExtra(ReminderNotificationManager.EXTRA_INSTRUCTIONS)

        if (reminderId.isBlank() || medicationName.isBlank()) {
            return
        }

        val payload = ReminderNotificationPayload(
            reminderId = reminderId,
            medicationId = medicationId.ifBlank { reminderId },
            medicationName = medicationName,
            dosage = dosage.ifBlank { "Dosis programada" },
            scheduledTime = scheduledTime.ifBlank { "08:00 AM" },
            frequency = frequency,
            instructions = instructions
        )

        ReminderNotificationManager.onNotificationReceived(context, payload)
    }
}
