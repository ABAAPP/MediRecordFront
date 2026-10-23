package com.aba.medirecord

import android.app.Application
import com.aba.medirecord.data.network.ApiClient
import com.aba.medirecord.notifications.ReminderNotificationManager

class MediRecordApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        ReminderNotificationManager.configureNotificationChannel(this)
    }
}
