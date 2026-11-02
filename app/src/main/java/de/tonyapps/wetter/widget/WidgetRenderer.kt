package de.tonyapps.wetter.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.res.ResourcesCompat
import de.tonyapps.wetter.R
import de.tonyapps.wetter.data.Day
import de.tonyapps.wetter.data.Place
import de.tonyapps.wetter.data.Weather
import de.tonyapps.wetter.data.clock
import de.tonyapps.wetter.data.conditionText
import de.tonyapps.wetter.data.deg
import de.tonyapps.wetter.data.hourLabel
import de.tonyapps.wetter.data.kindOf
import de.tonyapps.wetter.data.weekdayShort
import de.tonyapps.wetter.sky.SkyParams
import de.tonyapps.wetter.sky.skyFor
import de.tonyapps.wetter.ui.drawWeather
import de.tonyapps.wetter.ui.tempColor
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

enum class WidgetStyle(val defaultW: Int, val defaultH: Int) {
    SMALL(158, 158),
    MEDIUM(338, 158),
    LARGE(338, 354),
    DAILY(338, 158),
    SUN(158, 158),
}

/**
 * Zeichnet ein Widget komplett als Bitmap. So gibt es dieselben Symbole, Balken und Schriften
 * wie in der App, was mit den wenigen RemoteViews-Bausteinen nicht ginge.
 * Alle Masse unten sind dp, die Leinwand ist entsprechend skaliert.
 */
class WidgetRenderer(private val context: Context) {
    private val light = font(R.font.inter_300)
    private val regular = font(R.font.inter_400)
    private val medium = font(R.font.inter_500)
    private val semi = font(R.font.inter_600)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private fun font(id: Int): Typeface =
        ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT

    fun render(style: WidgetStyle, place: Place, w: Weather?, widthDp: Int, heightDp: Int, sky: SkyParams? = null): Bitmap {
        val width = widthDp.coerceIn(100, 600).toFloat()
        val height = heightDp.coerceIn(100, 700).toFloat()
        val density = min(context.resources.displayMetrics.density, 2.75f)
        val bitmap = Bitmap.createBitmap((width * density).toInt(), (height * density).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.scale(density, density)

        val params = sky ?: w?.let { skyFor(it) } ?: SkyParams(cloud = .3f)
        background(c, width, height, params, density)

        val pad = 15f
        if (w == null || w.days.isEmpty()) {
            label(c, place.name, pad, pad + 14f, 15f, semi, width - 2 * pad)
            label(c, "Wird geladen …", pad, height - pad, 13f, medium, width - 2 * pad)
            return bitmap
        }
        when (style) {
            WidgetStyle.SMALL -> small(c, place, w, width, height, pad)
            WidgetStyle.MEDIUM -> {
                header(c, place, w, width, pad)
                hourly(c, w, pad, height - pad, width - 2 * pad)
            }
            WidgetStyle.LARGE -> {
                header(c, place, w, width, pad)
                val hourlyBottom = 142f
                hourly(c, w, pad, hourlyBottom, width - 2 * pad)
                line(c, pad, hourlyBottom + 9f, width - pad)
                days(c, w, pad, hourlyBottom + 14f, width - 2 * pad, height - pad + 3f, 30f, 7)
            }
            WidgetStyle.DAILY -> {
                compactHeader(c, place, w, width, pad)
                days(c, w, pad, pad + 24f, width - 2 * pad, height - pad + 4f, 25f, 6)
            }
            WidgetStyle.SUN -> sun(c, w, width, height, pad)
        }
        return bitmap
    }

    private fun background(c: Canvas, width: Float, height: Float, p: SkyParams, density: Float) {
        val box = RectF(0f, 0f, width, height)
        val radius = 22f
        // Der Himmel braucht wenig Aufloesung, er ist ohnehin weich
        val sky = SkyBitmap.render(context, (width * density / 2).toInt(), (height * density / 2).toInt(), p)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (sky != null) {
            val shader = BitmapShader(sky, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply { setScale(width / sky.width, height / sky.height) })
            paint.shader = shader
        } else {
            val top = mix(mix(0xFF040818.toInt(), 0xFF2566C2.toInt(), p.day), 0xFF576979.toInt(), p.grey * p.cloud)
            val low = mix(mix(0xFF132245.toInt(), 0xFF85BDED.toInt(), p.day), 0xFF97A6B5.toInt(), p.grey * p.cloud)
            paint.shader = LinearGradient(0f, 0f, 0f, height, top, low, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(box, radius, radius, paint)
        // Leichte Abdunklung haelt weisse Schrift auch vor hellen Wolken lesbar
        fill.shader = null
        fill.color = 0x1F000000
        c.drawRoundRect(box, radius, radius, fill)
    }

    private fun mix(a: Int, b: Int, t: Float): Int =
        androidx.compose.ui.graphics.lerp(
            androidx.compose.ui.graphics.Color(a),
            androidx.compose.ui.graphics.Color(b),
            t.coerceIn(0f, 1f),
        ).toArgb()

    private fun label(
        c: Canvas,
        s: String,
        x: Float,
        baseline: Float,
        size: Float,
        face: Typeface,
        maxWidth: Float = 9999f,
        align: Paint.Align = Paint.Align.LEFT,
        alpha: Int = 255,
    ) {
        text.typeface = face
        text.textSize = size
        text.textAlign = align
        text.color = 0xFFFFFF or (alpha shl 24)
        text.setShadowLayer(2.5f, 0f, .6f, 0x40000000)
        val shown = TextUtils.ellipsize(s, text, maxWidth, TextUtils.TruncateAt.END).toString()
        c.drawText(shown, x, baseline, text)
    }

    private fun icon(c: Canvas, code: Int, isDay: Boolean, x: Float, y: Float, size: Float) {
        c.save()
        c.translate(x, y)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, ComposeCanvas(c), Size(size, size)) {
            drawWeather(kindOf(code), isDay, size, .25f)
        }
        c.restore()
    }

    private fun line(c: Canvas, x0: Float, y: Float, x1: Float) {
        fill.shader = null
        fill.color = 0x40FFFFFF
        c.drawRect(x0, y, x1, y + .6f, fill)
    }

    private fun cityRow(c: Canvas, place: Place, x: Float, baseline: Float, size: Float, maxWidth: Float) {
        label(c, place.name, x, baseline, size, semi, maxWidth)
        if (place.isCurrent) {
            val nameWidth = min(text.measureText(place.name), maxWidth)
            val s = size * .62f
            val ax = x + nameWidth + 5f
            val ay = baseline - s - .5f
            val p = Path().apply {
                moveTo(ax + s * .9f, ay + s * .1f)
                lineTo(ax + s * .55f, ay + s * .92f)
                lineTo(ax + s * .45f, ay + s * .55f)
                lineTo(ax + s * .08f, ay + s * .45f)
                close()
            }
            fill.shader = null
            fill.color = -1
            c.drawPath(p, fill)
        }
    }

    private fun small(c: Canvas, place: Place, w: Weather, width: Float, height: Float, pad: Float) {
        val cur = w.current
        cityRow(c, place, pad, pad + 13f, 15f, width - 2 * pad - 16f)
        label(c, cur.temp.deg(), pad - 2f, pad + 63f, 46f, light)
        icon(c, cur.code, cur.isDay, pad - 1f, height - pad - 47f, 21f)
        label(c, conditionText(cur.code, cur.isDay, cur.cloud), pad, height - pad - 15f, 12.5f, medium, width - 2 * pad)
        label(c, "H:${w.today.max.deg()}  T:${w.today.min.deg()}", pad, height - pad, 12.5f, medium, width - 2 * pad)
    }

    private fun header(c: Canvas, place: Place, w: Weather, width: Float, pad: Float) {
        val cur = w.current
        val right = width - pad
        cityRow(c, place, pad, pad + 13f, 15f, width * .55f)
        label(c, cur.temp.deg(), pad - 2f, pad + 60f, 44f, light)
        icon(c, cur.code, cur.isDay, right - 23f, pad - 3f, 24f)
        label(c, conditionText(cur.code, cur.isDay, cur.cloud), right, pad + 37f, 12.5f, medium, width * .42f, Paint.Align.RIGHT)
        label(c, "H:${w.today.max.deg()}  T:${w.today.min.deg()}", right, pad + 53f, 12.5f, medium, width * .42f, Paint.Align.RIGHT)
    }

    private fun compactHeader(c: Canvas, place: Place, w: Weather, width: Float, pad: Float) {
        cityRow(c, place, pad, pad + 11f, 14f, width * .5f)
        val cur = w.current
        label(c, "${cur.temp.deg()}  ${conditionText(cur.code, cur.isDay, cur.cloud)}", width - pad, pad + 11f, 12.5f, medium, width * .46f, Paint.Align.RIGHT)
    }

    /** Sechs Stunden nebeneinander; `bottom` ist die Grundlinie der Temperaturen. */
    private fun hourly(c: Canvas, w: Weather, x: Float, bottom: Float, width: Float) {
        val hours = w.hoursFromNow.take(6)
        if (hours.isEmpty()) return
        val col = width / hours.size
        hours.forEachIndexed { i, h ->
            val cx = x + col * (i + .5f)
            val now = i == 0
            label(c, if (now) "Jetzt" else hourLabel(h.time, w.zone), cx, bottom - 44f, 11f, medium, col, Paint.Align.CENTER, 225)
            icon(c, if (now) w.current.code else h.code, if (now) w.current.isDay else h.isDay, cx - 11f, bottom - 38f, 22f)
            label(c, (if (now) w.current.temp else h.temp).deg(), cx, bottom, 13.5f, medium, col, Paint.Align.CENTER)
        }
    }

    /** Tageszeilen mit denselben Temperaturbalken wie in der App. */
    private fun days(c: Canvas, w: Weather, x: Float, top: Float, width: Float, bottom: Float, rowH: Float, maxRows: Int) {
        val rows = min(floor((bottom - top) / rowH).toInt(), maxRows).coerceAtLeast(1)
        val list = w.upcomingDays.take(rows)
        val low = w.upcomingDays.take(10).minOf { it.min }
        val high = w.upcomingDays.take(10).maxOf { it.max }
        val h = (bottom - top) / rows
        list.forEachIndexed { i, d ->
            val mid = top + h * (i + .5f)
            val base = mid + 4.5f
            label(c, if (i == 0) "Heute" else weekdayShort(d), x, base, 13f, medium, 44f)
            icon(c, d.code, true, x + 46f, mid - 10f, 20f)
            val barX = x + 104f
            val barEnd = x + width - 32f
            label(c, d.min.deg(), barX - 7f, base, 13f, medium, 34f, Paint.Align.RIGHT, 150)
            label(c, d.max.deg(), x + width, base, 13f, medium, 34f, Paint.Align.RIGHT)
            bar(c, d, low, high, if (i == 0) w.current.temp else null, barX, barEnd, mid)
        }
    }

    private fun bar(c: Canvas, d: Day, low: Double, high: Double, current: Double?, x0: Float, x1: Float, mid: Float) {
        val barH = 4f
        val span = (high - low).toFloat().coerceAtLeast(1f)
        val width = x1 - x0
        fun x(t: Double) = x0 + ((t - low).toFloat() / span * width).coerceIn(0f, width)
        val rect = RectF(x0, mid - barH / 2, x1, mid + barH / 2)
        fill.shader = null
        fill.color = 0x33000000
        c.drawRoundRect(rect, barH / 2, barH / 2, fill)

        val colors = IntArray(9) { tempColor(low + (high - low) * it / 8.0).toArgb() }
        // Der Shader erbt die Deckkraft der Paint-Farbe, also vorher auf deckend stellen
        fill.color = -1
        fill.shader = LinearGradient(x0, 0f, x1, 0f, colors, null, Shader.TileMode.CLAMP)
        val left = x(d.min)
        val right = x(d.max).coerceAtLeast(left + barH)
        c.drawRoundRect(RectF(left, rect.top, right, rect.bottom), barH / 2, barH / 2, fill)
        fill.shader = null
        if (current != null && !current.isNaN()) {
            fill.color = 0x73000000
            c.drawCircle(x(current), mid, 4.8f, fill)
            fill.color = -1
            c.drawCircle(x(current), mid, 3f, fill)
        }
    }

    private fun sun(c: Canvas, w: Weather, width: Float, height: Float, pad: Float) {
        val today = w.today
        val now = w.current.time
        val nextSet = now in today.sunrise until today.sunset
        val next = when {
            nextSet -> today.sunset
            now < today.sunrise -> today.sunrise
            else -> w.upcomingDays.getOrNull(1)?.sunrise ?: today.sunrise
        }
        label(c, if (nextSet) "SONNENUNTERGANG" else "SONNENAUFGANG", pad, pad + 10f, 10.5f, medium, width - 2 * pad, alpha = 190)
        label(c, clock(next, w.zone), pad, pad + 38f, 27f, medium)

        val x0 = pad
        val plot = width - 2 * pad
        val top = pad + 50f
        val h = height - top - pad - 24f
        val mid = top + h * .62f
        val amp = h * .42f
        val midnight = today.date.atStartOfDay(w.zone).toEpochSecond()
        fun px(t: Long) = (t - midnight).toFloat() / 86400f * plot
        fun py(x: Float) = mid - sin((x / plot) * 2 * PI.toFloat() - PI.toFloat() / 2) * amp
        val horizon = py(px(today.sunrise))
        val curve = Path().apply {
            moveTo(x0, py(0f))
            var x = 0f
            while (x <= plot) {
                lineTo(x0 + x, py(x))
                x += 2f
            }
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            color = 0x47FFFFFF
        }
        c.drawPath(curve, stroke)
        c.save()
        c.clipRect(x0 - 2f, top - 8f, x0 + plot + 2f, horizon)
        stroke.color = 0xD9FFFFFF.toInt()
        c.drawPath(curve, stroke)
        c.restore()
        line(c, x0, horizon, x0 + plot)
        val dx = px(now).coerceIn(0f, plot)
        val dy = py(dx)
        val up = dy < horizon
        fill.shader = null
        if (up) {
            fill.color = 0x47FFFFFF
            c.drawCircle(x0 + dx, dy, 8.5f, fill)
        }
        fill.color = if (up) -1 else 0xFF8FA3BF.toInt()
        c.drawCircle(x0 + dx, dy, 3.8f, fill)

        val other = if (nextSet) "Aufgang: ${clock(today.sunrise, w.zone)}" else "Untergang: ${clock(today.sunset, w.zone)}"
        label(c, other, pad, height - pad, 12.5f, medium, width - 2 * pad)
    }
}
