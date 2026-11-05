package de.tonyapps.wetter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tonyapps.wetter.data.WeatherViewModel
import de.tonyapps.wetter.sky.DEMO_SCENES
import de.tonyapps.wetter.sky.SkyBackground
import de.tonyapps.wetter.ui.WeatherPage
import de.tonyapps.wetter.ui.WetterTheme

/** Feste Szene fuer Screenshots: echte Vorhersage, aber Wetterlage und Himmel passend gesetzt. */
class DebugShotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scene = intent.getIntExtra("scene", 1)
        val scroll = intent.getIntExtra("scroll", 0)
        val scale = if (intent.getIntExtra("hi", 0) == 1) 1.5f else 5f
        val sky = DEMO_SCENES[scene].second
        val code = mapOf(0 to 0, 1 to 2, 2 to 3, 3 to 63, 4 to 95, 5 to 73, 6 to 45, 7 to 0, 8 to 2, 9 to 1)[scene] ?: 2
        setContent {
            val vm: WeatherViewModel = viewModel()
            WetterTheme {
                Box(Modifier.fillMaxSize()) {
                    SkyBackground({ sky }, fixedScale = scale)
                    val place = vm.places.first()
                    val weather = vm.weather[place.id]?.let {
                        it.copy(current = it.current.copy(code = code, isDay = sky.day > .5f, cloud = sky.cloud * 100.0), alerts = emptyList())
                    }
                    WeatherPage(
                        place = place, weather = weather, errorText = null, refreshing = false, onRefresh = {},
                        demoName = null, onCycleDemo = {}, onDetail = {}, onMap = {},
                        bottomPadding = 20.dp, startItem = scroll,
                    )
                }
            }
        }
    }
}
