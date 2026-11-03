package de.tonyapps.wetter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import de.tonyapps.wetter.MainActivity
import de.tonyapps.wetter.R
import de.tonyapps.wetter.data.Api
import de.tonyapps.wetter.data.Parser
import de.tonyapps.wetter.data.Place
import de.tonyapps.wetter.data.Store
import de.tonyapps.wetter.data.Weather
import de.tonyapps.wetter.data.conditionText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_AGE_MS = 20 * 60 * 1000L

object Widgets {
    private val lock = Mutex()

    private val PROVIDERS: Map<WidgetStyle, Class<out WeatherWidget>> = mapOf(
        WidgetStyle.SMALL to SmallWidget::class.java,
        WidgetStyle.MEDIUM to MediumWidget::class.java,
        WidgetStyle.LARGE to LargeWidget::class.java,
        WidgetStyle.DAILY to DailyWidget::class.java,
        WidgetStyle.SUN to SunWidget::class.java,
    )

    private fun placeFor(store: Store, widgetId: Int): Place {
        val places = store.allPlaces()
        return places.firstOrNull { it.id == store.widgetPlaceId(widgetId) } ?: places.first()
    }

    /** Wetter aus dem Cache, bei `fetch` und alten Daten frisch aus dem Netz. */
    private suspend fun weatherFor(store: Store, place: Place, fetch: Boolean): Weather? {
        val cached = store.cached(place.id)
        val age = System.currentTimeMillis() - (cached?.fetchedAt ?: 0L)
        val raw = if (fetch && age > MAX_AGE_MS) {
            runCatching { Api.fetch(place).also { store.cache(place.id, it) } }.getOrNull() ?: cached
        } else {
            cached
        }
        return raw?.let { runCatching { Parser.parse(it) }.getOrNull() }
    }

    suspend fun update(context: Context, style: WidgetStyle, ids: IntArray, fetch: Boolean) = lock.withLock {
        withContext(Dispatchers.IO) {
            val store = Store(context)
            val manager = AppWidgetManager.getInstance(context)
            val renderer = WidgetRenderer(context)
            ids.forEach { id ->
                val place = placeFor(store, id)
                val weather = weatherFor(store, place, fetch)
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: style.defaultW
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: style.defaultH
                val views = animatedViews(context, renderer, style, place, weather, width, height)
                val description = weather?.let {
                    "${place.name}: ${it.current.temp.toInt()} Grad, ${conditionText(it.current.code, it.current.isDay, it.current.cloud)}"
                } ?: place.name
                decorate(context, views, place, id, description)
                try {
                    manager.updateAppWidget(id, views)
                } catch (e: IllegalArgumentException) {
                    // Android begrenzt den Bildspeicher pro Widget: dann eben als Standbild
                    Log.w("Widgets", "Schleife zu gross", e)
                    val still = stillViews(context, renderer, style, place, weather, width, height)
                    decorate(context, still, place, id, description)
                    manager.updateAppWidget(id, still)
                }
            }
        }
    }

    private fun animatedViews(
        context: Context,
        renderer: WidgetRenderer,
        style: WidgetStyle,
        place: Place,
        weather: Weather?,
        width: Int,
        height: Int,
    ): RemoteViews {
        val frames = renderer.skyLoop(weather, width, height)
        if (frames.isEmpty()) return stillViews(context, renderer, style, place, weather, width, height)
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        frames.forEach { frame ->
            val child = RemoteViews(context.packageName, R.layout.widget_frame)
            child.setImageViewBitmap(R.id.widget_frame, frame)
            views.addView(R.id.widget_sky, child)
        }
        views.setImageViewBitmap(R.id.widget_image, renderer.overlay(style, place, weather, width, height))
        return views
    }

    private fun stillViews(
        context: Context,
        renderer: WidgetRenderer,
        style: WidgetStyle,
        place: Place,
        weather: Weather?,
        width: Int,
        height: Int,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setImageViewBitmap(R.id.widget_image, renderer.render(style, place, weather, width, height))
        return views
    }

    private fun decorate(context: Context, views: RemoteViews, place: Place, id: Int, description: String) {
        views.setContentDescription(R.id.widget_root, description)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PLACE, place.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
    }

    /** Aktualisiert alle Widgets aus dem Cache; die App ruft das nach jedem Laden auf. */
    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        PROVIDERS.forEach { (style, cls) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) update(context, style, ids, fetch = false)
        }
    }

    fun providerOf(style: WidgetStyle): Class<out WeatherWidget> = PROVIDERS.getValue(style)
}

abstract class WeatherWidget(private val style: WidgetStyle) : AppWidgetProvider() {
    private fun run(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(45_000) { block() }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        run { Widgets.update(context, style, ids, fetch = true) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        run { Widgets.update(context, style, intArrayOf(id), fetch = false) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = Store(context)
        ids.forEach { store.dropWidget(it) }
    }
}

class SmallWidget : WeatherWidget(WidgetStyle.SMALL)
class MediumWidget : WeatherWidget(WidgetStyle.MEDIUM)
class LargeWidget : WeatherWidget(WidgetStyle.LARGE)
class DailyWidget : WeatherWidget(WidgetStyle.DAILY)
class SunWidget : WeatherWidget(WidgetStyle.SUN)
