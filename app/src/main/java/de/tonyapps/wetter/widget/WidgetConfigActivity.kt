package de.tonyapps.wetter.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.Place
import de.tonyapps.wetter.data.Store
import de.tonyapps.wetter.ui.Hairline
import de.tonyapps.wetter.ui.WetterTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Ortswahl fuer ein Widget. Erscheint beim Bearbeiten des Widgets auf dem Startbildschirm. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // OK schon vorab: Zurueck behaelt das Widget mit dem Standardort, statt es zu entfernen.
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_OK, result)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val store = Store(this)
        val places = store.allPlaces()
        setContent {
            WetterTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .padding(16.dp),
                ) {
                    Text("Ort für das Widget", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn {
                        items(places, key = { it.id }) { place ->
                            Column(Modifier.fillMaxWidth().clickable { choose(store, id, place) }.padding(vertical = 13.dp)) {
                                Text(place.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                if (place.region.isNotBlank()) {
                                    Text(place.region, fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
                                }
                            }
                            Hairline()
                        }
                    }
                }
            }
        }
    }

    private fun choose(store: Store, id: Int, place: Place) {
        store.setWidgetPlace(id, place.id)
        val context = applicationContext
        val manager = AppWidgetManager.getInstance(context)
        val style = WidgetStyle.entries.firstOrNull { style ->
            manager.getAppWidgetIds(ComponentName(context, Widgets.providerOf(style))).contains(id)
        }
        if (style != null) {
            CoroutineScope(Dispatchers.IO).launch { Widgets.update(context, style, intArrayOf(id), fetch = true) }
        }
        finish()
    }
}
