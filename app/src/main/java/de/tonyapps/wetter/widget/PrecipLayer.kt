package de.tonyapps.wetter.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import de.tonyapps.wetter.sky.SkyParams
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class PrecipLoop(val frames: List<Bitmap>, val intervalMs: Int)

/**
 * Niederschlag als durchsichtige Bilder ueber den Wolken. Jeder Tropfen legt pro Durchlauf
 * eine ganze Zahl Bildschirmhoehen zurueck, deshalb schliesst die Schleife exakt.
 */
object PrecipLayer {
    private const val RAIN_FRAMES = 10
    private const val SNOW_FRAMES = 16

    fun render(width: Int, height: Int, p: SkyParams): PrecipLoop? = when {
        p.snow > .05f && p.rain < .05f -> PrecipLoop(snow(width, height, p.snow), 140)
        p.rain > .05f -> {
            val frames = rain(width, height, p.rain, p.snow)
            PrecipLoop(if (p.storm > .5f) withLightning(frames, width, height) else frames, 80)
        }
        else -> null
    }

    private fun blank(width: Int, height: Int) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun rain(width: Int, height: Int, rain: Float, snow: Float): List<Bitmap> {
        val random = Random(7)
        val unit = height / 160f
        val count = (width * height / (unit * unit) * rain * .05f).toInt().coerceIn(30, 420)
        class Drop(val x: Float, val y: Float, val length: Float, val alpha: Int, val laps: Int, val thick: Float)
        val drops = List(count) {
            val near = random.nextFloat()
            Drop(
                x = random.nextFloat() * width * 1.2f,
                y = random.nextFloat(),
                length = (8f + near * 12f) * unit,
                alpha = (46 + near * 90).toInt(),
                laps = 1 + (near * 1.99f).toInt(),
                thick = (.45f + near * .4f) * unit,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        return List(RAIN_FRAMES) { frame ->
            val bitmap = blank(width, height)
            val c = Canvas(bitmap)
            val t = frame / RAIN_FRAMES.toFloat()
            drops.forEach { d ->
                val span = height + d.length
                val y = ((d.y + t * d.laps) % 1f) * span - d.length
                val x = d.x - y * .07f - width * .1f
                paint.color = 0xC8DDF5 or (d.alpha shl 24)
                paint.strokeWidth = d.thick
                c.drawLine(x, y, x - d.length * .07f, y + d.length, paint)
            }
            if (snow > .05f) {
                paint.color = 0xB0FFFFFF.toInt()
                val flakes = Random(3)
                repeat(40) {
                    val y = ((flakes.nextFloat() + t) % 1f) * height
                    c.drawCircle(flakes.nextFloat() * width, y, 1.1f * unit, paint)
                }
            }
            bitmap
        }
    }

    private fun snow(width: Int, height: Int, snow: Float): List<Bitmap> {
        val random = Random(5)
        val unit = height / 160f
        val count = (width * height / (unit * unit) * snow * .012f).toInt().coerceIn(24, 160)
        class Flake(val x: Float, val y: Float, val r: Float, val alpha: Int, val sway: Float, val phase: Float)
        val flakes = List(count) {
            val near = random.nextFloat()
            Flake(
                x = random.nextFloat() * width,
                y = random.nextFloat(),
                r = (.8f + near * 1.5f) * unit,
                alpha = (120 + near * 125).toInt(),
                sway = (2f + near * 3f) * unit,
                phase = random.nextFloat() * 6.28f,
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        return List(SNOW_FRAMES) { frame ->
            val bitmap = blank(width, height)
            val c = Canvas(bitmap)
            val t = frame / SNOW_FRAMES.toFloat()
            flakes.forEach { f ->
                val y = ((f.y + t) % 1f) * (height + 2 * f.r) - f.r
                val x = f.x + sin(t * 2 * PI.toFloat() + f.phase) * f.sway
                paint.color = 0xFFFFFF or (f.alpha shl 24)
                c.drawCircle(x, y, f.r, paint)
            }
            bitmap
        }
    }

    /** Drei Durchlaeufe Regen, im mittleren ein Blitz mit Nachleuchten. */
    private fun withLightning(rain: List<Bitmap>, width: Int, height: Int): List<Bitmap> {
        val base = rain[0]
        val bolt = Path()
        val random = Random(11)
        var x = width * (.3f + random.nextFloat() * .4f)
        var y = 0f
        bolt.moveTo(x, y)
        val end = height * .72f
        while (y < end) {
            y += height * (.05f + random.nextFloat() * .07f)
            x += width * (random.nextFloat() - .5f) * .09f
            bolt.lineTo(x, y)
        }
        fun flash(sky: Int, boltAlpha: Int): Bitmap {
            val bitmap = base.copy(Bitmap.Config.ARGB_8888, true)
            val c = Canvas(bitmap)
            c.drawColor(0xC8D2FF or (sky shl 24))
            val unit = height / 160f
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3.5f * unit
                strokeJoin = Paint.Join.ROUND
                color = 0xC8D8FF or (boltAlpha / 2 shl 24)
                maskFilter = BlurMaskFilter(4f * unit, BlurMaskFilter.Blur.NORMAL)
            }
            val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * unit
                strokeJoin = Paint.Join.ROUND
                color = 0xFFFFFF or (boltAlpha shl 24)
            }
            c.drawPath(bolt, glow)
            c.drawPath(bolt, core)
            return bitmap
        }
        val strike = listOf(flash(120, 255), flash(40, 120), flash(95, 235), flash(26, 60))
        return rain + rain.subList(0, 3) + strike + rain.subList(7, 10) + rain + rain
    }
}
