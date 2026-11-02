package de.tonyapps.wetter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.tonyapps.wetter.data.WeatherViewModel
import de.tonyapps.wetter.ui.MapScreen
import de.tonyapps.wetter.ui.WetterTheme

/** Zeigt nur die Karte, ohne Himmel: So bleibt der Emulator beim Test bedienbar. */
class DebugMapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WetterTheme { MapScreen(WeatherViewModel.DEFAULT_PLACE) { finish() } } }
    }
}
