package de.tonyapps.wetter.sky

import de.tonyapps.wetter.R
import android.content.Context
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import de.tonyapps.wetter.data.Kind
import de.tonyapps.wetter.data.Weather
import de.tonyapps.wetter.data.kindOf
import de.tonyapps.wetter.data.moonInfo
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** Alle Stellgroessen des Himmels. Weil es nur Zahlen sind, laesst sich zwischen zwei Wetterlagen stufenlos morphen. */
data class SkyParams(
    val cloud: Float = 0f,
    val grey: Float = 0f,
    val rain: Float = 0f,
    val snow: Float = 0f,
    val fog: Float = 0f,
    val storm: Float = 0f,
    val day: Float = 1f,
    val dusk: Float = 0f,
    val sunX: Float = 0.72f,
    val sunY: Float = 0.14f,
    val moon: Float = 0.5f,
    val sunVis: Float = 1f,
    val moonUp: Float = 0f,
)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

fun lerpSky(a: SkyParams, b: SkyParams, t: Float) = SkyParams(
    cloud = mix(a.cloud, b.cloud, t),
    grey = mix(a.grey, b.grey, t),
    rain = mix(a.rain, b.rain, t),
    snow = mix(a.snow, b.snow, t),
    fog = mix(a.fog, b.fog, t),
    storm = mix(a.storm, b.storm, t),
    day = mix(a.day, b.day, t),
    dusk = mix(a.dusk, b.dusk, t),
    sunX = mix(a.sunX, b.sunX, t),
    sunY = mix(a.sunY, b.sunY, t),
    moon = mix(a.moon, b.moon, t),
    sunVis = mix(a.sunVis, b.sunVis, t),
    moonUp = mix(a.moonUp, b.moonUp, t),
)

/**
 * Mischung beim Ortswechsel: Wolken und Licht gehen fliessend ineinander ueber.
 * Sonne und Mond duerfen nicht quer ueber den Himmel gleiten, sie blenden am alten
 * Platz aus und am neuen ein.
 */
fun lerpPages(a: SkyParams, b: SkyParams, t: Float): SkyParams {
    val near = if (t < .5f) a else b
    return lerpSky(a, b, t).copy(
        sunX = near.sunX,
        sunY = near.sunY,
        moon = near.moon,
        moonUp = near.moonUp,
        sunVis = near.sunVis * kotlin.math.abs(1f - 2f * t),
    )
}

/** Die Szenen des Vorschau-Modus (lange auf den Ortsnamen druecken). */
val DEMO_SCENES: List<Pair<Int, SkyParams>> = listOf(
    R.string.c_clear to SkyParams(sunX = 0.72f, sunY = 0.13f),
    R.string.c_partly to SkyParams(cloud = 0.52f, grey = 0.05f, sunX = 0.72f, sunY = 0.16f),
    R.string.c_overcast to SkyParams(cloud = 0.95f, grey = 0.75f, sunX = 0.5f, sunY = 0.1f),
    R.string.c_rain to SkyParams(cloud = 1f, grey = 0.9f, rain = 0.65f, sunX = 0.5f, sunY = 0.1f),
    R.string.c_thunder to SkyParams(cloud = 1f, grey = 1f, rain = 0.95f, storm = 1f, day = 0.45f, sunX = 0.5f, sunY = 0.1f),
    R.string.s_snow to SkyParams(cloud = 1f, grey = 0.6f, snow = 0.75f, sunX = 0.5f, sunY = 0.1f),
    R.string.c_fog to SkyParams(cloud = 0.7f, grey = 0.6f, fog = 0.85f, sunX = 0.5f, sunY = 0.2f),
    R.string.s_clear_night to SkyParams(cloud = 0.08f, grey = 0.1f, day = 0f, moonUp = 1f, sunX = 0.7f, sunY = 0.13f, moon = 0.36f),
    R.string.s_cloudy_night to SkyParams(cloud = 0.62f, grey = 0.4f, day = 0f, moonUp = 1f, sunX = 0.28f, sunY = 0.14f),
    R.string.s_sunset to SkyParams(cloud = 0.42f, grey = 0.05f, day = 0.72f, dusk = 0.9f, sunX = 0.3f, sunY = 0.62f),
    R.string.s_frost to SkyParams(cloud = 0.18f, grey = 0.05f, sunX = 0.7f, sunY = 0.22f),
    R.string.s_heat to SkyParams(cloud = 0.04f, sunX = 0.55f, sunY = 0.1f),
)

/** Feste Temperaturen fuer Szenen, deren Karteneffekt an der Temperatur haengt. */
val DEMO_TEMPS: Map<Int, Double> = mapOf(
    R.string.s_snow to -2.0,
    R.string.s_frost to -6.0,
    R.string.s_heat to 33.0,
)

/** Uebersetzt die Wetterdaten in Himmel-Parameter. */
fun skyFor(w: Weather): SkyParams {
    val now = w.current.time
    val today = w.dayOf(now)
    val rise = today?.sunrise ?: (now - 21600)
    val set = today?.sunset ?: (now + 21600)
    val halfHour = 1800f
    val fromRise = (now - rise).toFloat()
    val toSet = (set - now).toFloat()
    val day = ((fromRise + halfHour) / (2 * halfHour)).coerceIn(0f, 1f) *
        ((toSet + halfHour) / (2 * halfHour)).coerceIn(0f, 1f)
    val dusk = max(exp(-(fromRise / 2700f) * (fromRise / 2700f)), exp(-(toSet / 2700f) * (toSet / 2700f)))

    // Sonne auf einem Bogen ueber den Bildschirm, nachts der Mond auf einem flacheren.
    val isSunUp = now in rise..set
    val p = if (isSunUp) {
        (now - rise).toFloat() / (set - rise).coerceAtLeast(1)
    } else {
        val nightStart = if (now > set) set else set - 86400
        val nightEnd = if (now > set) rise + 86400 else rise
        ((now - nightStart).toFloat() / (nightEnd - nightStart).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val arc = sin(p * PI.toFloat())
    val sunX = 0.14f + 0.72f * p
    val sunY = if (isSunUp) 0.62f - 0.50f * arc else 0.34f - 0.20f * arc

    // Sonne und Mond teilen sich die Bahn nicht: Nach Sonnenuntergang gibt es nur den Mond.
    // Am Horizont blenden beide aus, damit beim Wechsel keiner sichtbar springt.
    val fromEvent = if (isSunUp) minOf(now - rise, set - now) else minOf(kotlin.math.abs(now - set), kotlin.math.abs(now - rise))
    val bodyVis = (fromEvent / 1500f).coerceIn(0f, 1f)

    val cloudPct = (if (w.current.cloud.isNaN()) 50.0 else w.current.cloud).toFloat() / 100f
    val base = SkyParams(
        cloud = cloudPct * 0.96f,
        grey = ((cloudPct - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.72f,
        day = day,
        dusk = dusk,
        sunX = sunX,
        sunY = sunY,
        moon = moonInfo(now).phase.toFloat(),
        sunVis = bodyVis,
        moonUp = if (isSunUp) 0f else 1f,
    )
    return when (kindOf(w.current.code)) {
        Kind.CLEAR, Kind.MOSTLY_CLEAR, Kind.PARTLY -> base
        Kind.CLOUDY -> base.copy(cloud = max(base.cloud, 0.82f), grey = max(base.grey, 0.55f))
        Kind.FOG -> base.copy(cloud = max(base.cloud, 0.6f), grey = 0.6f, fog = 0.85f)
        Kind.DRIZZLE -> base.copy(cloud = 0.95f, grey = 0.8f, rain = 0.28f)
        Kind.RAIN -> base.copy(cloud = 1f, grey = 0.9f, rain = 0.62f)
        Kind.HEAVY_RAIN -> base.copy(cloud = 1f, grey = 1f, rain = 0.95f)
        Kind.SLEET -> base.copy(cloud = 1f, grey = 0.85f, rain = 0.4f, snow = 0.35f)
        Kind.SNOW -> base.copy(cloud = 1f, grey = 0.6f, snow = 0.75f)
        Kind.THUNDER -> base.copy(cloud = 1f, grey = 1f, rain = 0.9f, storm = 1f, day = base.day * 0.6f)
    }
}

/** Gleitet weich auf neue Zielwerte, z. B. beim Durchschalten der Vorschau-Szenen. */
@Composable
fun rememberSmoothSky(target: SkyParams, durationMs: Int = 1600): State<SkyParams> {
    val state = remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        val from = state.value
        if (from == target) return@LaunchedEffect
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) {
            state.value = lerpSky(from, target, value)
        }
    }
    return state
}

private fun loadShader(context: Context): RuntimeShader? = runCatching {
    RuntimeShader(context.assets.open("shaders/sky.agsl").bufferedReader().use { it.readText() })
}.getOrNull()

/**
 * Ein Shader malt den kompletten Himmel. `params` wird erst beim Zeichnen gelesen, die
 * Animation laeuft also ohne Recomposition.
 *
 * Er rendert in eine eigene Ebene und skaliert hoch. Startwert ist 2/3 der Display-Aufloesung.
 * Haelt ein Geraet das nicht, senkt die Bildraten-Sicherung die Aufloesung stufenweise.
 */
@Composable
fun SkyBackground(params: () -> SkyParams, modifier: Modifier = Modifier, fixedScale: Float? = null) {
    val context = LocalContext.current
    val shader = remember { loadShader(context) }
    val currentParams by rememberUpdatedState(params)

    if (shader == null) {
        Spacer(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2566C2), Color(0xFF85BDED)))))
        return
    }
    val brush = remember { ShaderBrush(shader) }
    var time by remember { mutableFloatStateOf(0f) }
    // Der Software-Renderer des Emulators schafft die hohe Stufe nicht einmal fuers erste Bild
    var scale by remember { mutableFloatStateOf(fixedScale ?: if (android.os.Build.HARDWARE == "ranchu") 5f else 1.5f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        var windowStart = start
        var frames = 0
        while (true) {
            withFrameNanos { now ->
                time = ((now - start) / 1_000_000_000f + 40f) % 21600f
                frames++
                val window = now - windowStart
                if (window > 1_200_000_000L) {
                    // Unter rund 40 Bildern pro Sekunde eine Stufe groeber rendern
                    if (fixedScale == null && window / frames > 25_000_000L && scale < 3f) scale += .5f
                    windowStart = now
                    frames = 0
                }
            }
        }
    }

    Spacer(
        modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val small = measurable.measure(Constraints.fixed(ceil(w / scale).toInt(), ceil(h / scale).toInt()))
                layout(w, h) {
                    small.placeWithLayer(0, 0) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                }
            }
            .drawBehind {
                val p = currentParams()
                shader.setFloatUniform("uRes", size.width, size.height)
                shader.setFloatUniform("uTime", time)
                shader.setFloatUniform("uCloudTime", time)
                shader.setFloatUniform("uLoopT", 0f)
                shader.setFloatUniform("uLoopW", 0f)
                shader.setFloatUniform("uCloud", p.cloud)
                shader.setFloatUniform("uGrey", p.grey)
                shader.setFloatUniform("uRain", p.rain)
                shader.setFloatUniform("uSnow", p.snow)
                shader.setFloatUniform("uFog", p.fog)
                shader.setFloatUniform("uStorm", p.storm)
                shader.setFloatUniform("uDay", p.day)
                shader.setFloatUniform("uDusk", p.dusk)
                shader.setFloatUniform("uSun", p.sunX, p.sunY)
                shader.setFloatUniform("uMoon", p.moon)
                shader.setFloatUniform("uSunVis", p.sunVis)
                shader.setFloatUniform("uMoonUp", if (p.moonUp > .5f) 1f else 0f)
                drawRect(brush, Offset.Zero, size)
            },
    )
}

/** Einfarbiger Verlauf fuer die Ortsliste, angelehnt an den Himmel des Ortes. */
fun skyBrush(p: SkyParams): Brush {
    fun c(r: Float, g: Float, b: Float) = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    val grey = p.grey * p.cloud
    val lum = 0.16f + 0.84f * p.day
    val top = c(
        mix(mix(0.016f, 0.145f, p.day), 0.34f * lum, grey),
        mix(mix(0.031f, 0.400f, p.day), 0.41f * lum, grey),
        mix(mix(0.094f, 0.760f, p.day), 0.49f * lum, grey),
    )
    val low = c(
        mix(mix(mix(0.075f, 0.52f, p.day), 0.98f, p.dusk), 0.59f * lum, grey),
        mix(mix(mix(0.13f, 0.74f, p.day), 0.58f, p.dusk), 0.65f * lum, grey),
        mix(mix(mix(0.27f, 0.93f, p.day), 0.36f, p.dusk), 0.71f * lum, grey),
    )
    return Brush.verticalGradient(listOf(top, low))
}
