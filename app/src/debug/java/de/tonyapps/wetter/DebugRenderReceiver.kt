package de.tonyapps.wetter

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import de.tonyapps.wetter.data.Parser
import de.tonyapps.wetter.data.Store
import de.tonyapps.wetter.sky.DEMO_SCENES
import de.tonyapps.wetter.update.UpdateState
import de.tonyapps.wetter.update.Updater
import de.tonyapps.wetter.widget.SkyBitmap
import de.tonyapps.wetter.widget.WidgetRenderer
import de.tonyapps.wetter.widget.WidgetStyle
import de.tonyapps.wetter.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Schreibt alle Widget-Stile als PNG; so laesst sich das Ergebnis ohne Launcher ansehen. */
class DebugRenderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "de.tonyapps.wetter.DEBUG_UPDATE") {
            Updater.apiUrl = intent.getStringExtra("api").orEmpty()
            Updater.allowedDownload = "http://10.0.2.2"
            Updater.check(context)
            return
        }
        if (intent.action == "de.tonyapps.wetter.DEBUG_INSTALL") {
            (Updater.state as? UpdateState.Available)?.let { Updater.install(context, it.release) }
            return
        }
        if (intent.action == "de.tonyapps.wetter.DEBUG_SCENE") {
            Widgets.debugSky = DEMO_SCENES[intent.getIntExtra("scene", 3)].second
            CoroutineScope(Dispatchers.IO).launch { Widgets.updateAll(context) }
            return
        }
        if (intent.action == "de.tonyapps.wetter.DEBUG_PIN") {
            val manager = AppWidgetManager.getInstance(context)
            val style = WidgetStyle.valueOf(intent.getStringExtra("style") ?: "LARGE")
            val result = runCatching { manager.requestPinAppWidget(ComponentName(context, Widgets.providerOf(style)), null, null) }
            Log.i("WetterDebug", "pin $result")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(context.getExternalFilesDir(null), "widgets").apply { mkdirs() }
                val store = Store(context)
                val place = store.allPlaces().first()
                val weather = store.cached(place.id)?.let { Parser.parse(it) }
                Log.i("WetterDebug", "place=${place.name} weather=${weather != null} shader=${SkyBitmap.render(context, 64, 64, DEMO_SCENES[1].second) != null}")
                val renderer = WidgetRenderer(context)
                val language = java.util.Locale.getDefault().language
                listOf(
                    WidgetStyle.SMALL to 1, WidgetStyle.MEDIUM to 3, WidgetStyle.LARGE to 7,
                    WidgetStyle.DAILY to 1, WidgetStyle.SUN to 9,
                ).forEach { (style, scene) ->
                    val bitmap = renderer.render(style, place, weather, style.defaultW, style.defaultH, DEMO_SCENES[scene].second)
                    save(bitmap, File(dir, "${language}_${style.name}.png"))
                }
                Log.i("WetterDebug", "render done")
            } catch (t: Throwable) {
                Log.e("WetterDebug", "render failed", t)
            }
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
