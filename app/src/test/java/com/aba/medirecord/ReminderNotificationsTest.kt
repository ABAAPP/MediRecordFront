package com.aba.medirecord

import com.aba.medirecord.notifications.NotificationDeliveryStatus
import com.aba.medirecord.notifications.ReminderNotificationManager
import com.aba.medirecord.notifications.ReminderNotificationPayload
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class ReminderNotificationsTest {

    @Before
    fun setUp() {
        ReminderNotificationManager.clearLogs()
    }

    @Test
    fun testNotificationMechanismIsConfigured() {
        val configured = ReminderNotificationManager.configureNotificationChannel(context = null)
        assertTrue(configured)
        assertTrue(ReminderNotificationManager.isMechanismConfigured())
        assertEquals("medirecord_reminders_channel", ReminderNotificationManager.CHANNEL_ID)
    }

    @Test
    fun testNotificationContainsMedicationInfoAndMatchesConfiguredSchedule() {
        val payload = ReminderNotificationPayload(
            reminderId = "rem-10",
            medicationId = "1",
            medicationName = "Amoxicilina",
            dosage = "500 mg - 1 cápsula",
            scheduledTime = "02:00 PM",
            frequency = "Cada 8 horas (3 veces al día)",
            instructions = "Tomar con abundante agua"
        )

        val title = ReminderNotificationManager.buildNotificationTitle(payload)
        val body = ReminderNotificationManager.buildNotificationBody(payload)

        // Criterio 3: Las notificaciones contienen información del medicamento
        assertTrue(title.contains("Amoxicilina"))
        assertTrue(body.contains("02:00 PM"))
        assertTrue(body.contains("500 mg - 1 cápsula"))
        assertTrue(body.contains("Tomar con abundante agua"))

        // Criterio 4: Las notificaciones corresponden a los horarios configurados
        val baseCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val triggerMillis = ReminderNotificationManager.calculateNextTriggerMillis(
            scheduledTime = "02:00 PM",
            nowMillis = baseCalendar.timeInMillis
        )
        assertNotNull(triggerMillis)

        val triggerCalendar = Calendar.getInstance().apply { timeInMillis = triggerMillis!! }
        assertEquals(14, triggerCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCalendar.get(Calendar.MINUTE))
    }

    @Test
    fun testVerifySendingAndReceivingNotificationForConfiguredSchedule() {
        val payload = ReminderNotificationPayload(
            reminderId = "rem-25",
            medicationId = "2",
            medicationName = "Omeprazol",
            dosage = "20 mg - 1 cápsula",
            scheduledTime = "09:00 AM",
            instructions = "En ayunas"
        )

        // Programar para el horario configurado
        val scheduleResult = ReminderNotificationManager.scheduleNotificationForConfiguredTime(
            context = null,
            payload = payload
        )
        assertTrue(scheduleResult.isSuccess)
        assertEquals(NotificationDeliveryStatus.SCHEDULED, scheduleResult.getOrNull()!!.status)

        // Criterio 2, 5, 6 y 7: Enviar y recibir la notificación correspondiente al horario configurado
        val deliveryResult = ReminderNotificationManager.triggerAndReceiveReminderNotification(
            context = null,
            payload = payload
        )
        assertTrue(deliveryResult.isSuccess)

        // Se puede comprobar el envío de la notificación
        assertTrue(ReminderNotificationManager.wasNotificationSent("rem-25"))

        // Se puede comprobar la recepción de la notificación
        assertTrue(ReminderNotificationManager.wasNotificationReceived("rem-25"))

        val receivedEvent = deliveryResult.getOrNull()!!
        assertEquals(NotificationDeliveryStatus.RECEIVED, receivedEvent.status)
        assertEquals("Omeprazol", receivedEvent.medicationName)
        assertEquals("09:00 AM", receivedEvent.scheduledTime)
    }

    @Test
    fun testNotificationErrorsAreHandledCorrectly() {
        // 1. Error por medicamento sin nombre
        val invalidPayload = ReminderNotificationPayload(
            reminderId = "rem-err",
            medicationId = "99",
            medicationName = "   ",
            dosage = "100 mg",
            scheduledTime = "08:00 AM"
        )
        val invalidResult = ReminderNotificationManager.triggerAndReceiveReminderNotification(
            context = null,
            payload = invalidPayload
        )
        assertTrue(invalidResult.isFailure)
        assertNotNull(invalidResult.exceptionOrNull()?.message)

        // 2. Error por formato de horario inválido
        val badTimePayload = invalidPayload.copy(
            medicationName = "Paracetamol",
            scheduledTime = "hora-incorrecta"
        )
        val badTimeResult = ReminderNotificationManager.scheduleNotificationForConfiguredTime(
            context = null,
            payload = badTimePayload
        )
        assertTrue(badTimeResult.isFailure)

        // 3. Error simulado en el sistema de envío de notificaciones
        ReminderNotificationManager.simulateDeliveryFailure = true
        val validPayload = ReminderNotificationPayload(
            reminderId = "rem-ok",
            medicationId = "1",
            medicationName = "Paracetamol",
            dosage = "500 mg",
            scheduledTime = "08:00 AM"
        )
        val simulatedErrorResult = ReminderNotificationManager.triggerAndReceiveReminderNotification(
            context = null,
            payload = validPayload
        )
        assertTrue(simulatedErrorResult.isFailure)
        ReminderNotificationManager.simulateDeliveryFailure = false
    }
}
