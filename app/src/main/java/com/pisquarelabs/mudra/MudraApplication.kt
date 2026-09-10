package com.pisquarelabs.mudra

import android.app.Application
import com.pisquarelabs.mudra.di.AppContainer

class MudraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
