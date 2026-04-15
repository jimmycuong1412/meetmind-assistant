// T015: Application class — creates the DI container at startup
package com.meetmind.assistant

import android.app.Application
import com.meetmind.assistant.di.AppContainer

class MeetMindApplication : Application() {

    /** Application-scoped dependency container. Accessible from any component. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
