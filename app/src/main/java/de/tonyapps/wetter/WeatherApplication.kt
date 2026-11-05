package de.tonyapps.wetter

import android.app.Application
import de.tonyapps.wetter.data.AppContext

class WeatherApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.app = this
    }
}
