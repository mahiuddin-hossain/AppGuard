package com.mahiuddin.appguard

import android.app.Application
import com.mahiuddin.appguard.data.AppBlockRepository

class AppGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize repository singleton with applicationContext
        AppBlockRepository.getInstance(this)
    }
}
