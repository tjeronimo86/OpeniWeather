package de.tonyapps.wetter.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
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
import de.tonyapps.wetter.data.tr
import de.tonyapps.wetter.sky.SkyParams
import de.tonyapps.wetter.sky.skyFor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_AGE_MS = 20 * 60 * 1000L

/** Durchlaeufe der Wolkenbilder im ViewFlipper: 24 x 6,3 s, also gut 2,5 Minuten ohne Sprung. */
private const val SKY_REPEATS = 24

object Widgets {
    private val lock = Mutex()

    /** Nur fuer den Emulator-Test: erzwingt eine Wetterlage. */
    var debugSky: SkyParams? = null

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
                val sky = calmSky(debugSky ?: weather?.let { skyFor(it) } ?: SkyParams(cloud = .3f))
                val description = weather?.let {
                    tr(R.string.w_desc, place.name, it.current.temp.toInt(), conditionText(it.current.code, it.current.isDay, it.current.cloud))
                } ?: place.name
                // Gleicher Himmel wie beim letzten Mal: nur Text und Symbole tauschen. Ein neuer
                // Satz Wolkenbilder startet die Schleife immer beim ersten Bild, und das sieht man
                // als harten Schnitt mitten in der laufenden Bewegung. Der Startbildschirm holt
                // Updates aus dem Standby gesammelt nach, deshalb fiel es genau dort auf.
                val signature = skySignature(context, style, place, width, height, sky)
                if (store.widgetSky(id) == signature) {
                    val partial = RemoteViews(context.packageName, R.layout.widget_root)
                    partial.setImageViewBitmap(R.id.widget_image, renderer.overlay(style, place, weather, width, height, sky))
                    decorate(context, partial, place, id, description)
                    try {
                        manager.partiallyUpdateAppWidget(id, partial)
                        return@forEach
                    } catch (e: RuntimeException) {
                        Log.w("Widgets", "Teil-Update fehlgeschlagen, baue neu", e)
                    }
                }
                val animated = animatedViews(context, renderer, style, place, weather, width, height, sky)
                val views = animated ?: stillViews(context, renderer, style, place, weather, width, height)
                decorate(context, views, place, id, description)
                try {
                    manager.updateAppWidget(id, views)
                    store.setWidgetSky(id, if (animated != null) signature else null)
                } catch (e: RuntimeException) {
                    // Android begrenzt den Bildspeicher pro Widget: dann eben als Standbild
                    Log.w("Widgets", "Schleife zu gross", e)
                    val still = stillViews(context, renderer, style, place, weather, width, height)
                    decorate(context, still, place, id, description)
                    manager.updateAppWidget(id, still)
                    store.setWidgetSky(id, null)
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
        sky: SkyParams,
    ): RemoteViews? {
        val layers = renderer.layers(weather, width, height, sky)
        if (layers.clouds.isEmpty()) return null
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        // Android spielt ein Update auf das vorhandene Widget auf. Ohne Leeren wuerden die
        // neuen Bilder an die alten angehaengt, und der Himmel von gestern liefe weiter mit.
        views.removeAllViews(R.id.widget_sky)
        views.removeAllViews(R.id.widget_precip)
        // Der ViewFlipper blendet nur sauber, wenn das neue Bild ueber dem alten liegt. Beim
        // Sprung vom letzten aufs erste Bild liegt es darunter. Deshalb stehen die Bilder
        // mehrfach hintereinander: Jeder Durchlauf geht als normaler Wechsel in den naechsten
        // ueber. RemoteViews speichert jede Bitmap nur einmal, das kostet keinen Speicher.
        // Ganz am Ende steht das erste Bild noch einmal: Der seltene echte Sprung fuehrt dann
        // von einem Bild auf dasselbe Bild und faellt nur als kurze Pause auf.
        repeat(SKY_REPEATS) {
            layers.clouds.forEach { views.addView(R.id.widget_sky, frameView(context, it)) }
        }
        views.addView(R.id.widget_sky, frameView(context, layers.clouds.first()))
        layers.precip?.let { loop ->
            loop.frames.forEach { views.addView(R.id.widget_precip, frameView(context, it)) }
            views.setInt(R.id.widget_precip, "setFlipInterval", loop.intervalMs)
        }
        views.setImageViewBitmap(R.id.widget_image, renderer.overlay(style, place, weather, width, height, sky))
        return views
    }

    /**
     * Rundet die Himmelswerte grob. Die Sonne wandert sonst mit jeder Minute ein Stueck weiter,
     * und jedes Update braeuchte neue Wolkenbilder. So wird die Schleife nur getauscht, wenn
     * sich der Himmel sichtbar aendert: andere Bewoelkung, Regen, Daemmerung, ein bis zwei
     * Stunden Sonnenlauf.
     */
    private fun calmSky(p: SkyParams): SkyParams {
        fun q(v: Float, step: Float) = (v / step).roundToInt() * step
        return SkyParams(
            cloud = q(p.cloud, .1f),
            grey = q(p.grey, .1f),
            rain = q(p.rain, .1f),
            snow = q(p.snow, .1f),
            fog = q(p.fog, .1f),
            storm = q(p.storm, .25f),
            day = q(p.day, .1f),
            dusk = q(p.dusk, .1f),
            sunX = q(p.sunX, .1f),
            sunY = q(p.sunY, .1f),
            moon = q(p.moon, .05f),
            sunVis = q(p.sunVis, .25f),
            moonUp = q(p.moonUp, 1f),
        )
    }

    /**
     * Alles, was die Wolkenschleife bestimmt. Neustart und App-Update gehoeren dazu: Danach
     * kennt Android das alte Widget nicht mehr, und ein Teil-Update liefe ins Leere.
     */
    private fun skySignature(context: Context, style: WidgetStyle, place: Place, width: Int, height: Int, sky: SkyParams): String {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        val night = context.resources.configuration.uiMode
        return "$boot|$installed|$night|$style|${place.id}|${width}x$height|$sky"
    }

    private fun frameView(context: Context, bitmap: Bitmap): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.widget_frame)
        view.setImageViewBitmap(R.id.widget_frame, bitmap)
        return view
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
        views.removeAllViews(R.id.widget_sky)
        views.removeAllViews(R.id.widget_precip)
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
