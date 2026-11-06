package de.tonyapps.wetter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Regenstaerke 0..1 und eine Uhr in Sekunden; null heisst: kein Regen, nichts zeichnen. */
class RainFx(val intensity: Float, val time: State<Float>)

val LocalRainFx = compositionLocalOf<RainFx?> { null }

/** Startet die Uhr nur, solange es regnet. */
@Composable
fun ProvideRain(intensity: Float, content: @Composable () -> Unit) {
    if (intensity < .03f) {
        CompositionLocalProvider(LocalRainFx provides null, content = content)
        return
    }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val fx = remember(intensity) { RainFx(intensity, time) }
    CompositionLocalProvider(LocalRainFx provides fx, content = content)
}

/** Fester Zufallswert 0..1 aus zwei Zahlen. */
private fun hash(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
}

private val Water = Color(0xFFEAF3FF)

/**
 * Regen auf einer Karte: Tropfen schlagen auf der Oberkante auf und spritzen hoch,
 * an den Seiten rinnen Tropfen herunter und fallen unten ab. Alles haengt nur an der Zeit,
 * es gibt keinen Zustand pro Tropfen.
 *
 * `top` ist die sichtbare Oberkante (sie wandert beim Scrollen unter den Kopf),
 * `alpha` die Deckkraft der Karte.
 */
fun DrawScope.drawCardRain(fx: RainFx, top: Float, alpha: Float, radius: Float, seed: Int) {
    if (alpha <= .01f) return
    val t = fx.time.value
    val rain = fx.intensity.coerceIn(0f, 1f)
    val w = size.width
    val h = size.height
    val dp = density

    // Spritzer auf der Oberkante
    if (top < h - radius) {
        val slots = ceil(w / (20 * dp) * (.4f + rain)).toInt().coerceIn(3, 28)
        for (i in 0 until slots) {
            val period = (.8f + hash(seed + i, 1) * 1.5f) / (.45f + rain)
            val tt = t + hash(seed + i, 2) * period
            val cycle = floor(tt / period).toInt()
            val local = tt / period - cycle
            if (local > .34f) continue
            val p = local / .34f
            val x = radius + (w - 2 * radius) * hash(seed * 7 + i * 31 + cycle, 3)
            val fade = (1f - p) * alpha
            // flacher Wasserkranz am Aufschlag
            val ringW = (3f + 12f * p) * dp
            drawOval(
                Water.copy(alpha = .6f * fade),
                Offset(x - ringW / 2, top - 1.1f * dp),
                Size(ringW, (2.2f - 1.4f * p) * dp),
            )
            // kleine Tropfen fliegen im Bogen hoch und fallen zurueck
            val drops = 2 + (hash(i, cycle) * 2.99f).toInt()
            for (k in 0 until drops) {
                val side = (k - (drops - 1) / 2f) + (hash(i * 13 + k, cycle) - .5f) * .6f
                val vx = side * (12f + 12f * hash(k, i + cycle)) * dp
                val vy = (20f + 18f * hash(k + 5, i + cycle)) * dp
                val dx = vx * p
                val dy = -vy * p + vy * 1.7f * p * p
                if (dy > 0) continue
                drawCircle(Water.copy(alpha = .95f * fade), (1.9f - .7f * p) * dp, Offset(x + dx, top + dy))
            }
        }
    }

    // Rinnsale an den Seiten
    val start = top + radius * .7f
    val length = h - start
    if (length < 24 * dp) return
    val perSide = (h / (80 * dp) * (.6f + rain * 1.3f)).roundToInt().coerceIn(2, 8)
    for (side in 0..1) {
        for (j in 0 until perSide) {
            val id = seed * 3 + side * 101 + j
            val period = (3.2f + hash(id, 4) * 4f) / (.6f + rain * .7f)
            val tt = t + hash(id, 5) * period
            val cycle = floor(tt / period).toInt()
            val local = tt / period - cycle
            val run = .82f
            val rx = if (side == 0) .2f * dp else w - .2f * dp
            val head = (2.2f + hash(id, cycle) * 1.3f) * dp
            if (local < run) {
                // Haften und Losrutschen: wird schneller, stockt dabei kurz
                val q = local / run
                val slip = q.pow(1.5f) + .035f * sin(q * 2 * PI.toFloat() * 3f)
                val y = start + length * slip.coerceIn(0f, 1f)
                val fade = alpha * minOf(1f, q * 8f)
                // nasse Spur von oben bis zum Tropfen
                drawLine(
                    Water.copy(alpha = .2f * fade),
                    Offset(rx, start), Offset(rx, y), 1.6f * dp, StrokeCap.Round,
                )
                val trail = (10f + 18f * q) * dp
                drawLine(
                    Brush.verticalGradient(listOf(Color.Transparent, Water.copy(alpha = .75f * fade)), y - trail, y),
                    Offset(rx, maxOf(start, y - trail)), Offset(rx, y), head * 1.1f, StrokeCap.Round,
                )
                drawCircle(Water.copy(alpha = .85f * fade), head, Offset(rx, y))
                drawCircle(Color.White.copy(alpha = .9f * fade), head * .38f, Offset(rx - head * .3f, y - head * .3f))
            } else {
                // unten angekommen: loesen und abfallen
                val f = (local - run) / (1f - run)
                val y = h + 34f * dp * f * f
                val drift = (if (side == 0) -1 else 1) * 3f * dp * f
                val fade = alpha * (1f - f)
                drawCircle(Water.copy(alpha = .8f * fade), head * (1f - .3f * f), Offset(rx + drift, y))
            }
        }
    }
}
