package com.aba.medirecord

import android.app.Application
import com.aba.medirecord.data.network.ApiClient

class MediRecordApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}
