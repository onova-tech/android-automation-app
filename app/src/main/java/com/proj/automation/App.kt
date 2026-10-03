package com.proj.automation

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        com.proj.automation.agent.AgentCoordinator.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
