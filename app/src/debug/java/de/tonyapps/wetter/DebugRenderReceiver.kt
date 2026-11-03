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
                listOf(WidgetStyle.MEDIUM).forEach { style ->
                    save(renderer.render(style, place, weather, style.defaultW, style.defaultH), File(dir, "${style.name}.png"))
                }
                emptyList<Int>().forEach { i ->
                    val sky = DEMO_SCENES[i].second
                    save(renderer.render(WidgetStyle.SMALL, place, weather, 158, 158, sky), File(dir, "scene$i.png"))
                }
                listOf(3, 4, 5, 1).forEach { i ->
                    val started = System.currentTimeMillis()
                    val layers = renderer.layers(weather, 338, 158, DEMO_SCENES[i].second)
                    val all = (layers.clouds.toSet() + layers.precip?.frames.orEmpty().toSet())
                    Log.i("WetterDebug", "layers scene$i clouds=${layers.clouds.size} ${layers.clouds.firstOrNull()?.width}x${layers.clouds.firstOrNull()?.height} precip=${layers.precip?.frames?.size} ms=${System.currentTimeMillis() - started} bytes=${all.sumOf { it.allocationByteCount }}")
                    layers.clouds.firstOrNull()?.let { save(it, File(dir, "cloud$i.png")) }
                    layers.precip?.frames?.forEachIndexed { n, frame -> if (n % 7 == 0 || n in 13..16) save(frame, File(dir, "precip${i}_$n.png")) }
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
