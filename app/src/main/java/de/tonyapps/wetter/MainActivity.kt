package de.tonyapps.wetter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import de.tonyapps.wetter.ui.WetterApp

class MainActivity : ComponentActivity() {
    /** Ort, den ein Widget-Tipp oeffnen soll. */
    private val openPlace = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPlace.value = intent?.getStringExtra(EXTRA_PLACE)
        setContent { WetterApp(openPlace = openPlace.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPlace.value = intent.getStringExtra(EXTRA_PLACE)
    }

    companion object {
        const val EXTRA_PLACE = "place"
    }
}
