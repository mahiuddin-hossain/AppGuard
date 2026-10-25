package com.mahiuddin.appguard

import android.app.Application
import com.mahiuddin.appguard.data.AppBlockRepository
import com.mahiuddin.appguard.data.NotificationRepository

class AppGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize repositories and auto-sync triggers with applicationContext
        AppBlockRepository.getInstance(this)
        NotificationRepository.getInstance(this)
    }
}
