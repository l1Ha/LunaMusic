package com.luna.music

import android.app.Application

class LunaApplication : Application() {

    companion object {
        lateinit var instance: LunaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
