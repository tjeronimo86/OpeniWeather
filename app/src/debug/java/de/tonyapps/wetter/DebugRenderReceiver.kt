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
        if (intent.action == "de.tonyapps.wetter.DEBUG_PIN") {
            val manager = AppWidgetManager.getInstance(context)
            val style = WidgetStyle.valueOf(intent.getStringExtra("style") ?: "LARGE")
            Log.i("WetterDebug", "pin ${manager.requestPinAppWidget(ComponentName(context, Widgets.providerOf(style)), null, null)}")
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dir = File(context.getExternalFilesDir(null), "widgets").apply { mkdirs() }
                val store = Store(context)
                val place = store.allPlaces().first()
                val weather = store.cached(place.id)?.let { Parser.parse(it) }
                Log.i("WetterDebug", "place=${place.name} weather=${weather != null} shader=${SkyBitmap.render(context, 64, 64, DEMO_SCENES[1].second) != null}")
                val renderer = WidgetRenderer(context)
                WidgetStyle.entries.forEach { style ->
                    save(renderer.render(style, place, weather, style.defaultW, style.defaultH), File(dir, "${style.name}.png"))
                }
                DEMO_SCENES.forEachIndexed { i, (_, sky) ->
                    save(renderer.render(WidgetStyle.SMALL, place, weather, 158, 158, sky), File(dir, "scene$i.png"))
                }
                Log.i("WetterDebug", "render done")
            } catch (t: Throwable) {
                Log.e("WetterDebug", "render failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
