package com.aba.medirecord.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aba.medirecord.MainActivity
import com.aba.medirecord.data.repository.MedicationRepository
import com.aba.medirecord.util.UserMessageMapper
import com.aba.medirecord.util.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.UUID

object ReminderNotificationManager {

    const val CHANNEL_ID = "medirecord_reminders_channel"
    const val CHANNEL_NAME = "Recordatorios de Medicamentos"
    const val CHANNEL_DESCRIPTION = "Alertas y notificaciones para recordar la toma puntual de tus medicamentos"

    const val ACTION_SHOW_MEDICATION_REMINDER = "com.aba.medirecord.ACTION_SHOW_MEDICATION_REMINDER"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_MEDICATION_ID = "extra_medication_id"
    const val EXTRA_MEDICATION_NAME = "extra_medication_name"
    const val EXTRA_DOSAGE = "extra_dosage"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_FREQUENCY = "extra_frequency"
    const val EXTRA_INSTRUCTIONS = "extra_instructions"

    private var isChannelConfigured: Boolean = false
    var simulateDeliveryFailure: Boolean = false

    private val _sentNotifications = MutableStateFlow<List<NotificationEventRecord>>(emptyList())
    val sentNotifications: StateFlow<List<NotificationEventRecord>> = _sentNotifications.asStateFlow()

    private val _receivedNotifications = MutableStateFlow<List<NotificationEventRecord>>(emptyList())
    val receivedNotifications: StateFlow<List<NotificationEventRecord>> = _receivedNotifications.asStateFlow()

    fun configureNotificationChannel(context: Context?): Boolean {
        return try {
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                    description = CHANNEL_DESCRIPTION
                    enableVibration(true)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
            isChannelConfigured = true
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isMechanismConfigured(): Boolean = isChannelConfigured

    fun buildNotificationTitle(payload: ReminderNotificationPayload): String {
        return "⏰ Hora de tu medicamento: ${payload.medicationName}"
    }

    fun buildNotificationBody(payload: ReminderNotificationPayload): String {
        val base = "Toma pendiente a las ${payload.scheduledTime} • Dosis: ${payload.dosage}"
        return if (!payload.instructions.isNullOrBlank()) {
            "$base • Indicaciones: ${payload.instructions}"
        } else {
            base
        }
    }

    fun validatePayload(payload: ReminderNotificationPayload): Result<Unit> {
        if (payload.reminderId.isBlank()) {
            return Result.failure(Exception("No se pudo identificar el recordatorio para la notificación"))
        }
        if (payload.medicationName.isBlank()) {
            return Result.failure(Exception("La notificación requiere el nombre del medicamento"))
        }
        if (payload.dosage.isBlank()) {
            return Result.failure(Exception("La notificación requiere la dosis del medicamento"))
        }
        if (payload.scheduledTime.isBlank() || !ValidationUtils.isValidTimeFormat(payload.scheduledTime)) {
            return Result.failure(Exception("El horario configurado para la notificación no tiene un formato válido"))
        }
        return Result.success(Unit)
    }

    /**
     * Calcula el instante en milisegundos correspondiente al horario configurado (ej. "08:00 AM").
     */
    fun calculateNextTriggerMillis(
        scheduledTime: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        if (!ValidationUtils.isValidTimeFormat(scheduledTime)) {
            return null
        }
        val totalMinutes = MedicationRepository.parseTimeToMinutes(scheduledTime)
        if (totalMinutes == Int.MAX_VALUE) return null

        val targetHour = totalMinutes / 60
        val targetMinute = totalMinutes % 60

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    /**
     * Programa una notificación asociada al horario configurado del medicamento.
     */
    fun scheduleNotificationForConfiguredTime(
        context: Context?,
        payload: ReminderNotificationPayload,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result<NotificationEventRecord> {
        if (simulateDeliveryFailure) {
            return Result.failure(
                Exception("No se pudo programar la notificación del recordatorio. Verifica los permisos de alertas.")
            )
        }

        val validation = validatePayload(payload)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        val triggerMillis = calculateNextTriggerMillis(payload.scheduledTime, nowMillis)
            ?: return Result.failure(Exception("No se pudo calcular el horario para programar la notificación"))

        return try {
            configureNotificationChannel(context)

            if (context != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val intent = createReminderBroadcastIntent(context, payload.copy(triggerAtMillis = triggerMillis))
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    payload.reminderId.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager?.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }

            val record = NotificationEventRecord(
                eventId = UUID.randomUUID().toString(),
                reminderId = payload.reminderId,
                medicationName = payload.medicationName,
                dosage = payload.dosage,
                scheduledTime = payload.scheduledTime,
                title = buildNotificationTitle(payload),
                message = buildNotificationBody(payload),
                status = NotificationDeliveryStatus.SCHEDULED,
                timestampMillis = triggerMillis
            )

            _sentNotifications.value = _sentNotifications.value + record
            Result.success(record)
        } catch (e: SecurityException) {
            Result.failure(
                Exception("El permiso de notificaciones o alarmas está desactivado en el dispositivo.")
            )
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    UserMessageMapper.sanitizeMessageForUser(
                        e.message,
                        "Ocurrió un problema al programar la notificación del medicamento."
                    )
                )
            )
        }
    }

    /**
     * Envía y procesa la recepción inmediata de una notificación para el recordatorio indicado,
     * permitiendo comprobar tanto el envío como la recepción en el dispositivo y en pruebas.
     */
    fun triggerAndReceiveReminderNotification(
        context: Context?,
        payload: ReminderNotificationPayload,
    ): Result<NotificationEventRecord> {
        if (simulateDeliveryFailure) {
            return Result.failure(
                Exception("No fue posible enviar la notificación del recordatorio en este momento.")
            )
        }

        val validation = validatePayload(payload)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        return try {
            configureNotificationChannel(context)

            val title = buildNotificationTitle(payload)
            val body = buildNotificationBody(payload)

            val sentRecord = NotificationEventRecord(
                eventId = UUID.randomUUID().toString(),
                reminderId = payload.reminderId,
                medicationName = payload.medicationName,
                dosage = payload.dosage,
                scheduledTime = payload.scheduledTime,
                title = title,
                message = body,
                status = NotificationDeliveryStatus.SENT
            )
            _sentNotifications.value = _sentNotifications.value + sentRecord

            // Procesar recepción de la notificación
            onNotificationReceived(context, payload)
        } catch (e: SecurityException) {
            Result.failure(
                Exception("Debes permitir las notificaciones en tu dispositivo para recibir alertas de medicamentos.")
            )
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    UserMessageMapper.sanitizeMessageForUser(
                        e.message,
                        "No se pudo mostrar la notificación del recordatorio."
                    )
                )
            )
        }
    }

    /**
     * Registra la recepción de la notificación y la muestra en la barra de estado del dispositivo Android.
     */
    fun onNotificationReceived(
        context: Context?,
        payload: ReminderNotificationPayload,
    ): Result<NotificationEventRecord> {
        if (simulateDeliveryFailure) {
            return Result.failure(
                Exception("Error al recibir la notificación del recordatorio.")
            )
        }

        val validation = validatePayload(payload)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        return try {
            configureNotificationChannel(context)

            val title = buildNotificationTitle(payload)
            val body = buildNotificationBody(payload)

            if (context != null) {
                showSystemNotificationIfPermitted(context, payload, title, body)
            }

            val receivedRecord = NotificationEventRecord(
                eventId = UUID.randomUUID().toString(),
                reminderId = payload.reminderId,
                medicationName = payload.medicationName,
                dosage = payload.dosage,
                scheduledTime = payload.scheduledTime,
                title = title,
                message = body,
                status = NotificationDeliveryStatus.RECEIVED
            )
            _receivedNotifications.value = _receivedNotifications.value + receivedRecord
            Result.success(receivedRecord)
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    UserMessageMapper.sanitizeMessageForUser(
                        e.message,
                        "No se pudo procesar la recepción de la notificación."
                    )
                )
            )
        }
    }

    private fun showSystemNotificationIfPermitted(
        context: Context,
        payload: ReminderNotificationPayload,
        title: String,
        body: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                return
            }
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            payload.reminderId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(payload.reminderId.hashCode(), notification)
    }

    fun createReminderBroadcastIntent(
        context: Context,
        payload: ReminderNotificationPayload,
    ): Intent {
        return Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_SHOW_MEDICATION_REMINDER
            putExtra(EXTRA_REMINDER_ID, payload.reminderId)
            putExtra(EXTRA_MEDICATION_ID, payload.medicationId)
            putExtra(EXTRA_MEDICATION_NAME, payload.medicationName)
            putExtra(EXTRA_DOSAGE, payload.dosage)
            putExtra(EXTRA_SCHEDULED_TIME, payload.scheduledTime)
            putExtra(EXTRA_FREQUENCY, payload.frequency)
            putExtra(EXTRA_INSTRUCTIONS, payload.instructions)
        }
    }

    fun wasNotificationSent(reminderId: String): Boolean {
        return _sentNotifications.value.any {
            it.reminderId == reminderId &&
                    (it.status == NotificationDeliveryStatus.SENT || it.status == NotificationDeliveryStatus.SCHEDULED)
        }
    }

    fun wasNotificationReceived(reminderId: String): Boolean {
        return _receivedNotifications.value.any {
            it.reminderId == reminderId && it.status == NotificationDeliveryStatus.RECEIVED
        }
    }

    fun clearLogs() {
        _sentNotifications.value = emptyList()
        _receivedNotifications.value = emptyList()
        simulateDeliveryFailure = false
    }
}
