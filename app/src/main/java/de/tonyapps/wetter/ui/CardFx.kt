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

/** Wetter auf den Karten. `time` laeuft in Sekunden; temp in Grad Celsius. */
class CardFx(val rain: Float, val snow: Float, val temp: Double, val time: State<Float>) {
    val frost: Float get() = if (temp <= 0.0) ((0.0 - temp) / 8.0 + .4).toFloat().coerceIn(.4f, 1f) else 0f
    val heat: Float get() = if (temp >= HOT) ((temp - HOT) / 8.0 + .35).toFloat().coerceIn(.35f, 1f) else 0f
}

private const val HOT = 28.0

/** Feste Werte statt echter Daten, fuer die Vorschau-Szenen und Screenshots. */
data class CardFxOverride(val rain: Float, val snow: Float, val temp: Double?)

val LocalCardFx = compositionLocalOf<CardFx?> { null }

/** Startet die Uhr nur, wenn auf den Karten etwas zu sehen ist. */
@Composable
fun ProvideCardFx(rain: Float, snow: Float, temp: Double, content: @Composable () -> Unit) {
    val active = rain >= .03f || snow >= .03f || temp <= 0.0 || temp >= HOT
    if (!active) {
        CompositionLocalProvider(LocalCardFx provides null, content = content)
        return
    }
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1e9f }
    }
    val fx = remember(rain, snow, temp) { CardFx(rain, snow, temp, time) }
    CompositionLocalProvider(LocalCardFx provides fx, content = content)
}

/** Fester Zufallswert 0..1 aus zwei Zahlen. */
private fun hash(a: Int, b: Int): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
}

private val Water = Color(0xFFEAF3FF)

/** Zeichnet alle Effekte einer Karte. Reihenfolge: Schwitzen, Eis, Regen, Schnee obenauf. */
fun DrawScope.drawCardFx(fx: CardFx, top: Float, alpha: Float, radius: Float, seed: Int) {
    if (alpha <= .01f) return
    if (fx.heat > 0f) drawSweat(fx, top, alpha, radius, seed)
    if (fx.frost > 0f) drawIcicles(fx, alpha, radius, seed)
    if (fx.rain >= .03f) drawRain(fx, top, alpha, radius, seed)
    if (fx.snow >= .03f) drawSnowCap(fx, top, alpha, radius, seed)
}

/** Waechst beim Oeffnen in `seconds` von 0 auf 1. */
private fun grow(t: Float, seconds: Float): Float {
    val p = (t / seconds).coerceIn(0f, 1f)
    return 1f - (1f - p) * (1f - p)
}

/**
 * Eiszapfen an der Unterkante: ein Eisrand, darunter Zapfen verschiedener Laenge.
 * Sie haengen in die Luecke zur naechsten Karte; dort verdeckt die naechste Karte die Spitze.
 */
private fun DrawScope.drawIcicles(fx: CardFx, alpha: Float, radius: Float, seed: Int) {
    val t = fx.time.value
    val dp = density
    val w = size.width
    val h = size.height
    val k = fx.frost
    val g = grow(t, 2.2f)
    val left = radius * .75f
    val right = w - radius * .75f
    // Eisrand entlang der Unterkante
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0x00FFFFFF), Color(0x99E8F4FF)), h - 3f * dp, h + 1.2f * dp),
        Offset(left, h - 2.5f * dp), Size(right - left, 3.7f * dp),
        androidx.compose.ui.geometry.CornerRadius(2f * dp),
        alpha = alpha * g,
    )
    val count = ((right - left) / (11f * dp) * (.55f + k * .6f)).toInt().coerceIn(3, 40)
    val step = (right - left) / count
    for (i in 0 until count) {
        val r = hash(seed + i, 7)
        if (r < .18f) continue
        val x = left + step * (i + .2f + .6f * hash(seed + i, 8))
        val long = hash(seed + i, 9)
        val length = (2.5f + (if (long > .82f) 11f else 6.5f) * long * k) * dp * g
        val base = (1.5f + 2.3f * hash(seed + i, 10)) * dp
        val tilt = (hash(seed + i, 11) - .5f) * 1.2f * dp
        val tipY = h + length
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(x - base / 2, h - .4f * dp)
            quadraticTo(x - base * .28f, h + length * .55f, x + tilt, tipY)
            quadraticTo(x + base * .28f, h + length * .55f, x + base / 2, h - .4f * dp)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(Color(0xF2FFFFFF), Color(0xB3CFE6FF), Color(0xE6F4FAFF)),
                h, tipY,
            ),
            alpha = alpha,
        )
        // Lichtkante und gelegentliches Funkeln an der Spitze
        drawLine(Color.White.copy(alpha = .7f * alpha), Offset(x - base * .18f, h + .5f * dp), Offset(x + tilt * .5f - base * .05f, h + length * .7f), .6f * dp, StrokeCap.Round)
        val twinkle = sin((t * (.6f + hash(seed + i, 12)) + hash(seed + i, 13) * 9f) * 2.2f)
        if (twinkle > .93f) {
            drawCircle(Color.White.copy(alpha = alpha * (twinkle - .93f) * 14f), 1.1f * dp, Offset(x + tilt, tipY - .6f * dp))
        }
    }
}

/** Schneeschicht auf der Oberkante, an den runden Ecken haengt sie etwas herab. */
private fun DrawScope.drawSnowCap(fx: CardFx, top: Float, alpha: Float, radius: Float, seed: Int) {
    val t = fx.time.value
    val dp = density
    val w = size.width
    val g = grow(t, 3f)
    val thickness = (2.5f + 5.5f * fx.snow.coerceIn(0f, 1f)) * dp * g
    if (thickness < .3f * dp) return
    // Kartenkante samt runder Ecken
    fun edge(x: Float): Float = when {
        x < radius -> top + radius - kotlin.math.sqrt((radius * radius - (radius - x) * (radius - x)).coerceAtLeast(0f))
        x > w - radius -> top + radius - kotlin.math.sqrt((radius * radius - (x - (w - radius)) * (x - (w - radius))).coerceAtLeast(0f))
        else -> top
    }
    // weiche Huegel aus zwei Wellen und etwas Zufall
    fun height(x: Float): Float {
        val u = x / w
        val bumps = .72f + .16f * sin(u * 23f + seed % 7) + .12f * sin(u * 61f + seed % 13)
        val corner = minOf(1f, minOf(x, w - x) / (radius * 1.1f)).coerceAtLeast(0f)
        return thickness * bumps * (.25f + .75f * corner)
    }
    val x0 = radius * .12f
    val x1 = w - radius * .12f
    val stepPx = 2.5f * dp
    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(x0, edge(x0) + .5f * dp)
    var x = x0
    while (x <= x1) {
        path.lineTo(x, edge(x) - height(x))
        x += stepPx
    }
    path.lineTo(x1, edge(x1) + .5f * dp)
    x = x1
    while (x >= x0) {
        path.lineTo(x, edge(x) + .9f * dp)
        x -= stepPx
    }
    path.close()
    drawPath(
        path,
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEFF5FC), Color(0xFFC9D7E8)), top - thickness, top + 1f * dp),
        alpha = alpha * .97f,
    )
    // Glitzern auf der Schneedecke
    val sparkles = (w / (16f * dp)).toInt().coerceIn(4, 30)
    for (i in 0 until sparkles) {
        val sx = x0 + (x1 - x0) * hash(seed + i, 20)
        val tw = sin(t * (1.3f + hash(seed + i, 21) * 2f) + hash(seed + i, 22) * 20f)
        if (tw < .82f) continue
        val sy = edge(sx) - height(sx) * (.3f + .5f * hash(seed + i, 23))
        drawCircle(Color.White.copy(alpha = alpha * (tw - .82f) * 5.5f), .9f * dp, Offset(sx, sy))
    }
}

/**
 * Kondenswasser bei Hitze: Perlen wachsen langsam. Manche verdunsten wieder,
 * die groessten werden zu schwer und laufen mit einer Spur nach unten.
 */
private fun DrawScope.drawSweat(fx: CardFx, top: Float, alpha: Float, radius: Float, seed: Int) {
    val t = fx.time.value
    val dp = density
    val w = size.width
    val h = size.height
    val k = fx.heat
    val y0 = top + 4f * dp
    if (h - y0 < 20f * dp) return
    val count = (w * (h - y0) / (dp * dp) / 900f * k).toInt().coerceIn(6, 90)
    for (i in 0 until count) {
        val period = 7f + 9f * hash(seed + i, 30)
        val tt = t + hash(seed + i, 31) * period
        val cycle = floor(tt / period).toInt()
        val local = tt / period - cycle
        val id = seed + i * 17 + cycle * 7919
        val x = (4f * dp) + (w - 8f * dp) * hash(id, 32)
        val yStart = y0 + (h - y0 - 6f * dp) * hash(id, 33)
        val size = (.9f + 2.4f * hash(id, 34) * hash(id, 35)) * dp
        val runs = hash(id, 36) > .8f
        val growPart = if (runs) .55f else .75f
        val r: Float
        var y = yStart
        var a: Float
        if (local < growPart) {
            val p = local / growPart
            r = size * (.35f + .65f * p)
            a = minOf(1f, p * 4f)
        } else if (runs) {
            val p = (local - growPart) / (1f - growPart)
            r = size * (1f - .25f * p)
            y = yStart + (h - 3f * dp - yStart) * p * p
            a = 1f - maxOf(0f, (p - .85f) / .15f)
            drawLine(Color.White.copy(alpha = .12f * alpha * a), Offset(x, yStart), Offset(x, y), r * .9f, StrokeCap.Round)
        } else {
            val p = (local - growPart) / (1f - growPart)
            r = size * (1f - .5f * p)
            a = 1f - p
        }
        if (a <= 0f) continue
        a *= alpha
        drawCircle(Color.White.copy(alpha = .16f * a), r, Offset(x, y))
        drawCircle(Color.Black.copy(alpha = .10f * a), r * .9f, Offset(x + r * .12f, y + r * .28f), style = androidx.compose.ui.graphics.drawscope.Stroke(r * .22f))
        drawCircle(Color.White.copy(alpha = .75f * a), r * .32f, Offset(x - r * .35f, y - r * .38f))
    }
}

/**
 * Regen auf einer Karte: Tropfen schlagen auf der Oberkante auf und spritzen hoch,
 * an den Seiten rinnen Tropfen herunter und fallen unten ab. Alles haengt nur an der Zeit,
 * es gibt keinen Zustand pro Tropfen.
 *
 * `top` ist die sichtbare Oberkante (sie wandert beim Scrollen unter den Kopf),
 * `alpha` die Deckkraft der Karte.
 */
private fun DrawScope.drawRain(fx: CardFx, top: Float, alpha: Float, radius: Float, seed: Int) {
    val t = fx.time.value
    val rain = fx.rain.coerceIn(0f, 1f)
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
