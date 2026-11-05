package de.tonyapps.wetter.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.Kind
import de.tonyapps.wetter.data.kindOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SUN = Color(0xFFFFD33D)
private val MOON = Color(0xFFE9EDF7)
private val CLOUD = Color(0xFFFFFFFF)
private val CLOUD_DARK = Color(0xFFD5DBE5)
private val DROP = Color(0xFF58C4FF)
private val BOLT = Color(0xFFFFD33D)

/** Mehrfarbige Wettersymbole, gezeichnet statt als Bild; Regen faellt, Sonnenstrahlen drehen sich. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier, animated: Boolean = true) {
    val kind = kindOf(code)
    val t: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "icon").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "t",
        )
    } else {
        remember { mutableFloatStateOf(0.25f) }
    }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val phase = t.value
        drawWeather(kind, isDay, s, phase)
    }
}

/** Zeichnet ein Wettersymbol der Kantenlaenge `s`; auch von den Widgets genutzt. */
fun DrawScope.drawWeather(kind: Kind, isDay: Boolean, s: Float, phase: Float) {
    when (kind) {
        Kind.CLEAR -> if (isDay) sun(Offset(s * .5f, s * .5f), s * .24f, phase) else moon(Offset(s * .5f, s * .5f), s * .32f)
        Kind.MOSTLY_CLEAR -> {
            if (isDay) sun(Offset(s * .5f, s * .46f), s * .21f, phase) else moon(Offset(s * .5f, s * .46f), s * .28f)
            cloud(Offset(s * .63f, s * .78f), s * .5f, CLOUD)
        }
        Kind.PARTLY -> {
            if (isDay) sun(Offset(s * .63f, s * .36f), s * .19f, phase) else moon(Offset(s * .7f, s * .28f), s * .25f)
            cloud(Offset(s * .43f, s * .8f), s * .72f, CLOUD)
        }
        Kind.CLOUDY -> {
            cloud(Offset(s * .6f, s * .55f), s * .66f, CLOUD_DARK)
            cloud(Offset(s * .46f, s * .74f), s * .82f, CLOUD)
        }
        Kind.FOG -> {
            cloud(Offset(s * .5f, s * .54f), s * .8f, CLOUD)
            for (i in 0..2) {
                val y = s * (.66f + i * .11f)
                val shift = sin((phase + i * .3f) * 2 * PI.toFloat()) * s * .03f
                drawLine(CLOUD.copy(alpha = .85f), Offset(s * (.14f + i * .06f) + shift, y), Offset(s * (.86f - i * .08f) + shift, y), s * .055f, StrokeCap.Round)
            }
        }
        Kind.DRIZZLE -> { cloud(Offset(s * .5f, s * .54f), s * .84f, CLOUD); drops(s, phase, 3, .10f) }
        Kind.RAIN -> { cloud(Offset(s * .5f, s * .52f), s * .84f, CLOUD); drops(s, phase, 3, .17f) }
        Kind.HEAVY_RAIN -> { cloud(Offset(s * .5f, s * .5f), s * .84f, CLOUD); drops(s, phase, 4, .23f) }
        Kind.SLEET -> { cloud(Offset(s * .5f, s * .52f), s * .84f, CLOUD); drops(s, phase, 2, .15f); flakes(s, phase, 1) }
        Kind.SNOW -> { cloud(Offset(s * .5f, s * .52f), s * .84f, CLOUD); flakes(s, phase, 3) }
        Kind.THUNDER -> {
            cloud(Offset(s * .5f, s * .5f), s * .84f, CLOUD)
            drops(s, phase, 2, .15f)
            val flash = if (phase in .1f..0.18f || phase in .24f..0.3f) .55f else 1f
            val p = Path().apply {
                moveTo(s * .55f, s * .56f)
                lineTo(s * .41f, s * .76f)
                lineTo(s * .51f, s * .76f)
                lineTo(s * .43f, s * .96f)
                lineTo(s * .64f, s * .71f)
                lineTo(s * .53f, s * .71f)
                lineTo(s * .62f, s * .56f)
                close()
            }
            drawPath(p, BOLT.copy(alpha = flash))
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, phase: Float) {
    drawCircle(SUN, r, c)
    val rotation = phase * 45f
    for (i in 0 until 8) {
        val a = ((i * 45f + rotation) * PI.toFloat() / 180f)
        val d = Offset(cos(a), sin(a))
        drawLine(SUN, c + d * (r * 1.42f), c + d * (r * 1.95f), r * .26f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val full = Path().apply { addOval(Rect(c, r)) }
    val cut = Path().apply { addOval(Rect(c + Offset(r * .55f, -r * .25f), r * .88f)) }
    drawPath(Path.combine(PathOperation.Difference, full, cut), MOON)
}

/** `c` ist die Mitte der Grundlinie, `w` die Breite. */
private fun DrawScope.cloud(c: Offset, w: Float, color: Color) {
    val h = w * .27f
    val left = c.x - w / 2
    drawRoundRect(color, Offset(left, c.y - h), Size(w, h), CornerRadius(h / 2))
    drawCircle(color, w * .19f, Offset(left + w * .34f, c.y - h * 1.0f))
    drawCircle(color, w * .25f, Offset(left + w * .58f, c.y - h * 1.15f))
}

private fun DrawScope.drops(s: Float, phase: Float, n: Int, len: Float) {
    for (i in 0 until n) {
        val x = s * (.34f + i * (.36f / (n - 1).coerceAtLeast(1)))
        val p = (phase * 2 + i * .37f) % 1f
        val y = s * (.60f + p * .2f)
        val alpha = (1f - p).coerceIn(.25f, 1f)
        drawLine(DROP.copy(alpha = alpha), Offset(x, y), Offset(x - s * len * .32f, y + s * len), s * .065f, StrokeCap.Round)
    }
}

private fun DrawScope.flakes(s: Float, phase: Float, n: Int) {
    for (i in 0 until n) {
        val x = s * (.34f + i * .17f) + sin((phase + i * .4f) * 2 * PI.toFloat()) * s * .025f
        val p = (phase + i * .33f) % 1f
        val y = s * (.62f + p * .28f)
        drawCircle(Color.White.copy(alpha = (1f - p * .7f)), s * .05f, Offset(x, y))
    }
}

enum class Glyph { CALENDAR, CLOCK, SUN, SUNSET, WIND, DROP, THERMO, HUMIDITY, EYE, GAUGE, MOON, AIR, MAP, WARN, REFRESH }

/** Kleine Liniensymbole in den Kartenkoepfen. */
@Composable
fun GlyphIcon(glyph: Glyph, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(s * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val line = { a: Offset, b: Offset -> drawLine(color, a, b, s * .09f, StrokeCap.Round) }
        when (glyph) {
            Glyph.CALENDAR -> {
                drawRoundRect(color, Offset(s * .1f, s * .18f), Size(s * .8f, s * .72f), CornerRadius(s * .12f), style = stroke)
                line(Offset(s * .1f, s * .4f), Offset(s * .9f, s * .4f))
            }
            Glyph.CLOCK -> {
                drawCircle(color, s * .42f, style = stroke)
                line(center, Offset(s * .5f, s * .24f))
                line(center, Offset(s * .68f, s * .58f))
            }
            Glyph.SUN -> {
                drawCircle(color, s * .2f, style = stroke)
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4
                    val d = Offset(cos(a), sin(a))
                    line(center + d * (s * .34f), center + d * (s * .46f))
                }
            }
            Glyph.SUNSET -> {
                line(Offset(s * .06f, s * .78f), Offset(s * .94f, s * .78f))
                drawArc(color, 180f, 180f, false, Offset(s * .26f, s * .32f), Size(s * .48f, s * .48f), style = stroke)
                line(Offset(s * .5f, s * .08f), Offset(s * .5f, s * .2f))
            }
            Glyph.WIND -> {
                for (i in 0..2) {
                    val y = s * (.28f + i * .22f)
                    line(Offset(s * .08f, y), Offset(s * (.8f - i * .16f), y))
                }
            }
            Glyph.DROP -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .06f)
                    cubicTo(s * .78f, s * .4f, s * .86f, s * .55f, s * .82f, s * .68f)
                    cubicTo(s * .74f, s * 1.02f, s * .26f, s * 1.02f, s * .18f, s * .68f)
                    cubicTo(s * .14f, s * .55f, s * .22f, s * .4f, s * .5f, s * .06f)
                    close()
                }
                drawPath(p, color)
            }
            Glyph.THERMO -> {
                drawRoundRect(color, Offset(s * .4f, s * .06f), Size(s * .2f, s * .62f), CornerRadius(s * .1f), style = stroke)
                drawCircle(color, s * .17f, Offset(s * .5f, s * .76f))
            }
            Glyph.HUMIDITY -> {
                for (i in 0..2) {
                    val y = s * (.3f + i * .22f)
                    val p = Path().apply {
                        moveTo(s * .1f, y)
                        cubicTo(s * .3f, y - s * .14f, s * .6f, y + s * .14f, s * .9f, y)
                    }
                    drawPath(p, color, style = stroke)
                }
            }
            Glyph.EYE -> {
                val p = Path().apply {
                    moveTo(s * .04f, s * .5f)
                    quadraticBezierTo(s * .5f, s * .02f, s * .96f, s * .5f)
                    quadraticBezierTo(s * .5f, s * .98f, s * .04f, s * .5f)
                    close()
                }
                drawPath(p, color, style = stroke)
                drawCircle(color, s * .13f)
            }
            Glyph.GAUGE -> {
                drawArc(color, 150f, 240f, false, Offset(s * .08f, s * .1f), Size(s * .84f, s * .84f), style = stroke)
                line(Offset(s * .5f, s * .52f), Offset(s * .68f, s * .3f))
            }
            Glyph.MOON -> {
                val full = Path().apply { addOval(Rect(center, s * .42f)) }
                val cut = Path().apply { addOval(Rect(center + Offset(s * .2f, -s * .1f), s * .36f)) }
                drawPath(Path.combine(PathOperation.Difference, full, cut), color)
            }
            Glyph.AIR -> {
                for ((x, y, r) in listOf(Triple(.26f, .3f, .09f), Triple(.62f, .22f, .12f), Triple(.42f, .62f, .13f), Triple(.8f, .62f, .08f))) {
                    drawCircle(color, s * r, Offset(s * x, s * y))
                }
            }
            Glyph.MAP -> {
                val p = Path().apply {
                    moveTo(s * .08f, s * .24f)
                    lineTo(s * .36f, s * .12f)
                    lineTo(s * .64f, s * .24f)
                    lineTo(s * .92f, s * .12f)
                    lineTo(s * .92f, s * .76f)
                    lineTo(s * .64f, s * .88f)
                    lineTo(s * .36f, s * .76f)
                    lineTo(s * .08f, s * .88f)
                    close()
                }
                drawPath(p, color, style = stroke)
                line(Offset(s * .36f, s * .12f), Offset(s * .36f, s * .76f))
                line(Offset(s * .64f, s * .24f), Offset(s * .64f, s * .88f))
            }
            Glyph.REFRESH -> {
                drawArc(color, -50f, 310f, false, Offset(s * .14f, s * .14f), Size(s * .72f, s * .72f), style = stroke)
                val p = Path().apply {
                    moveTo(s * .95f, s * .12f)
                    lineTo(s * .92f, s * .46f)
                    lineTo(s * .60f, s * .34f)
                    close()
                }
                drawPath(p, color)
            }
            Glyph.WARN -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .08f)
                    lineTo(s * .94f, s * .88f)
                    lineTo(s * .06f, s * .88f)
                    close()
                }
                drawPath(p, color, style = stroke)
                line(Offset(s * .5f, s * .38f), Offset(s * .5f, s * .6f))
                drawCircle(color, s * .05f, Offset(s * .5f, s * .74f))
            }
        }
    }
}

/** Liste-Symbol unten rechts. */
@Composable
fun ListIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        for (i in 0..2) {
            val y = s * (.22f + i * .28f)
            drawCircle(color, s * .05f, Offset(s * .1f, y))
            drawLine(color, Offset(s * .26f, y), Offset(s * .94f, y), s * .08f, StrokeCap.Round)
        }
    }
}

/** Standort-Pfeil als erster Punkt der Seitenanzeige. */
@Composable
fun ArrowIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val p = Path().apply {
            moveTo(s * .9f, s * .1f)
            lineTo(s * .55f, s * .92f)
            lineTo(s * .45f, s * .55f)
            lineTo(s * .08f, s * .45f)
            close()
        }
        drawPath(p, color)
    }
}
