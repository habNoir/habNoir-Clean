package xyz.habnoir.clean

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration

class HabNoirApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerComponentCallbacks(container.healthMonitor)
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= TRIM_MEMORY_UI_HIDDEN) {
                    container.iconMemoryCache.clear()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {
                container.iconMemoryCache.clear()
            }
        })
    }
}
