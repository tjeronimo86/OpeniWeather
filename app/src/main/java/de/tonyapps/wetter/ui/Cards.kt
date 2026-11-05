package de.tonyapps.wetter.ui

import de.tonyapps.wetter.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.*
import kotlinx.coroutines.delay
import kotlin.math.*

private val TileHeight = 168.dp

@Composable
fun CardTitle(glyph: Glyph, title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, 13.dp, Color.White.copy(alpha = .6f))
        Spacer(Modifier.width(6.dp))
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .6f), letterSpacing = .3.sp)
    }
}

/**
 * Startwert 0, laeuft nach `delayMs` auf 1. Fuer die Einblend-Animationen der Karten.
 * Jede laeuft nur einmal: Kommt eine Karte beim Wischen zurueck, ist sie sofort fertig.
 */
@Composable
fun rememberReveal(delayMs: Long = 0, durationMs: Int = 900): State<Float> {
    var played by rememberSaveable { mutableStateOf(false) }
    val a = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        played = true
        delay(delayMs)
        a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    return a.asState()
}

// ---------- Warnung ----------

@Composable
fun AlertCard(alert: Alert, zone: java.time.ZoneId, key: Any, sticky: StickyContext) {
    var expanded by remember { mutableStateOf(false) }
    GlassCard(
        key, sticky, Modifier.fillMaxWidth(), onClick = { expanded = !expanded },
        header = { CardTitle(Glyph.WARN, alert.event.ifBlank { tr(R.string.alert_default) }) },
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            Text(alert.headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (alert.expires > 0) {
                Text(tr(R.string.alert_until, clock(alert.expires, zone)), fontSize = 12.sp, color = Color.White.copy(alpha = .65f))
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(alert.description, fontSize = 14.sp, lineHeight = 19.sp)
                if (alert.instruction.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(alert.instruction, fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = .8f))
                }
            }
        }
    }
}

// ---------- Stundenleiste ----------

private sealed interface Slot {
    data class Forecast(val hour: Hour, val now: Boolean) : Slot
    data class SunEvent(val time: Long, val rise: Boolean) : Slot
}

private fun slots(w: Weather): List<Slot> {
    val hours = w.hoursFromNow.take(26)
    if (hours.isEmpty()) return emptyList()
    val from = w.current.time
    val to = hours.last().time
    val events = w.days.flatMap { listOf(Slot.SunEvent(it.sunrise, true), Slot.SunEvent(it.sunset, false)) }
        .filter { it.time in from..to }
    val out = ArrayList<Pair<Long, Slot>>()
    hours.forEachIndexed { i, h -> out += h.time to Slot.Forecast(h, i == 0) }
    events.forEach { out += it.time to it }
    return out.sortedBy { it.first }.map { it.second }
}

@Composable
fun HourlyCard(w: Weather, key: Any, sticky: StickyContext, onOpen: () -> Unit) {
    val list = remember(w) { slots(w) }
    GlassCard(
        key, sticky, Modifier.fillMaxWidth(),
        header = {
            Column {
                Text(summary(w), fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 11.dp).fillMaxWidth())
                Hairline(Modifier.padding(start = 14.dp))
            }
        },
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(list) { slot ->
                Column(
                    Modifier
                        .width(62.dp)
                        .clickable(onClick = onOpen),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (slot) {
                        is Slot.Forecast -> {
                            val h = slot.hour
                            Text(if (slot.now) tr(R.string.now) else hourLabel(h.time, w.zone), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Spacer(Modifier.height(7.dp))
                            IconWithPop(if (slot.now) w.current.code else h.code, if (slot.now) w.current.isDay else h.isDay, h.pop)
                            Spacer(Modifier.height(5.dp))
                            Text((if (slot.now) w.current.temp else h.temp).deg(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                        is Slot.SunEvent -> {
                            Text(clock(slot.time, w.zone), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Spacer(Modifier.height(7.dp))
                            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                                GlyphIcon(Glyph.SUNSET, 26.dp, Color(0xFFFFD33D))
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(tr(if (slot.rise) R.string.rise_short else R.string.set_short), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconWithPop(code: Int, isDay: Boolean, pop: Double) {
    Column(Modifier.height(44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        WeatherIcon(code, isDay, 30.dp)
        if (!pop.isNaN() && pop >= 20) {
            Text("${(pop / 10).roundToInt() * 10} %", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF7DD0FF))
        }
    }
}

// ---------- 10-Tage-Vorhersage mit Temperaturbalken ----------

@Composable
fun TenDayCard(w: Weather, key: Any, sticky: StickyContext, onDay: (Day) -> Unit) {
    val days = w.upcomingDays.take(10)
    val lowest = days.minOf { it.min }
    val highest = days.maxOf { it.max }
    GlassCard(
        key, sticky, Modifier.fillMaxWidth(),
        header = {
            Column {
                CardTitle(Glyph.CALENDAR, tr(R.string.t_ten_days))
                Hairline(Modifier.padding(start = 14.dp))
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            days.forEachIndexed { i, d ->
                Row(
                    Modifier.fillMaxWidth().clickable { onDay(d) }.heightIn(min = 52.dp).padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (i == 0) tr(R.string.today) else weekdayShort(d), fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(60.dp))
                    Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) { IconWithPop(d.code, true, d.pop) }
                    Spacer(Modifier.width(10.dp))
                    Text(d.min.deg(), fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .58f), textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                    Spacer(Modifier.width(9.dp))
                    TempBar(
                        low = d.min, high = d.max,
                        rangeLow = lowest, rangeHigh = highest,
                        current = if (i == 0) w.current.temp else null,
                        delayMs = 120L + i * 55L,
                        modifier = Modifier.weight(1f).height(14.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(d.max.deg(), fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                }
                if (i < days.lastIndex) Hairline()
            }
        }
    }
}

/**
 * Der Temperaturbalken der Tageszeile:
 * - die dunkle Spur steht fuer den Bereich aller 10 Tage,
 * - das farbige Stueck liegt dort, wo dieser Tag innerhalb dieses Bereichs liegt,
 * - sein Verlauf nutzt die feste Temperatur-Farbskala,
 * - heute markiert ein weisser Punkt die aktuelle Temperatur.
 * Beim Einblenden waechst das Stueck aus seiner Mitte; neue Daten gleiten an die neue Position.
 */
@Composable
fun TempBar(
    low: Double,
    high: Double,
    rangeLow: Double,
    rangeHigh: Double,
    current: Double?,
    delayMs: Long,
    modifier: Modifier = Modifier,
) {
    val grow by rememberReveal(delayMs, 780)
    val lo by animateFloatAsState(low.toFloat(), tween(900), label = "lo")
    val hi by animateFloatAsState(high.toFloat(), tween(900), label = "hi")
    val colors = remember(rangeLow, rangeHigh) {
        List(9) { tempColor(rangeLow + (rangeHigh - rangeLow) * it / 8.0) }
    }
    Canvas(modifier) {
        val barH = 4.5.dp.toPx()
        val top = (size.height - barH) / 2
        val w = size.width
        val span = (rangeHigh - rangeLow).toFloat().coerceAtLeast(1f)
        fun x(t: Float) = ((t - rangeLow.toFloat()) / span * w).coerceIn(0f, w)
        val corner = CornerRadius(barH / 2)

        drawRoundRect(Color.Black.copy(alpha = .20f), Offset(0f, top), Size(w, barH), corner)

        val left = x(lo)
        val right = x(hi).coerceAtLeast(left + barH)
        val mid = (left + right) / 2
        val half = (right - left) / 2 * grow
        drawRoundRect(
            Brush.horizontalGradient(colors, 0f, w),
            Offset(mid - half, top),
            Size(half * 2, barH),
            corner,
            alpha = grow.coerceIn(0f, 1f),
        )
        if (current != null && !current.isNaN()) {
            val dot = Offset(x(current.toFloat()), size.height / 2)
            drawCircle(Color.Black.copy(alpha = .45f), (barH / 2 + 2.5.dp.toPx()) * grow, dot)
            drawCircle(Color.White, (barH / 2 + 1.dp.toPx()) * grow, dot)
        }
    }
}

// ---------- Kachel-Grundform ----------

@Composable
fun Tile(
    glyph: Glyph,
    title: String,
    key: Any,
    sticky: StickyContext,
    modifier: Modifier = Modifier,
    height: Dp = TileHeight,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassCard(
        key, sticky, modifier.height(height), onClick = onClick,
        header = { CardTitle(glyph, title) },
    ) {
        Box(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), content = content)
    }
}

@Composable
private fun BigValue(text: String, unit: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun BoxScope.Note(text: String) {
    Text(text, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart))
}

// ---------- UV ----------

private val UV_COLORS = listOf(Color(0xFF4CD964), Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFFBF5AF2))

/** Regenbogenleiste mit Punkt; auch fuer die Luftqualitaet. */
@Composable
fun ScaleBar(fraction: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val move by rememberReveal(200, 1100)
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val h = 4.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(Brush.horizontalGradient(colors), Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
        val p = Offset(size.width * fraction.coerceIn(0f, 1f) * move, size.height / 2)
        drawCircle(Color.Black.copy(alpha = .5f), 5.5.dp.toPx(), p)
        drawCircle(Color.White, 3.5.dp.toPx(), p)
    }
}

@Composable
fun UvTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier, onClick: () -> Unit) {
    val uv = w.hoursFromNow.firstOrNull()?.uv ?: Double.NaN
    Tile(Glyph.SUN, tr(R.string.t_uv), key, sticky, modifier, onClick = onClick) {
        Column {
            BigValue(uv.int())
            Text(uvLabel(uv), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            ScaleBar(if (uv.isNaN()) 0f else (uv / 11.0).toFloat(), UV_COLORS)
        }
        Note(uvAdvice(w))
    }
}

// ---------- Sonne ----------

@Composable
fun SunTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier) {
    val today = w.today
    val now = w.current.time
    val tomorrow = w.upcomingDays.getOrNull(1)
    val nextIsSet = now in today.sunrise until today.sunset
    val nextTime = when {
        nextIsSet -> today.sunset
        now < today.sunrise -> today.sunrise
        else -> tomorrow?.sunrise ?: today.sunrise
    }
    val otherLabel = if (nextIsSet) tr(R.string.sunrise_at, clock(today.sunrise, w.zone)) else tr(R.string.sunset_at, clock(today.sunset, w.zone))
    val move by rememberReveal(150, 1400)
    Tile(if (nextIsSet) Glyph.SUNSET else Glyph.SUN, tr(if (nextIsSet) R.string.t_sunset else R.string.t_sunrise), key, sticky, modifier) {
        BigValue(clock(nextTime, w.zone))
        Canvas(Modifier.fillMaxWidth().height(58.dp).align(Alignment.Center).offset(y = 16.dp)) {
            val width = size.width
            val mid = size.height * .62f
            val amp = size.height * .36f
            // Tageslaenge bestimmt die Hoehe des Horizonts auf der Kurve
            val midnight = today.date.atStartOfDay(w.zone).toEpochSecond()
            fun xOf(t: Long) = (t - midnight).toFloat() / 86400f * width
            fun yOf(x: Float) = mid - sin((x / width) * 2 * PI.toFloat() - PI.toFloat() / 2) * amp
            val horizon = yOf(xOf(today.sunrise))
            val curve = Path().apply {
                moveTo(0f, yOf(0f))
                var x = 0f
                while (x <= width) {
                    lineTo(x, yOf(x))
                    x += 3f
                }
            }
            clipRect(bottom = horizon) { drawPath(curve, Color.White.copy(alpha = .75f), style = Stroke(2.dp.toPx())) }
            clipRect(top = horizon) { drawPath(curve, Color.White.copy(alpha = .28f), style = Stroke(2.dp.toPx())) }
            drawLine(Color.White.copy(alpha = .4f), Offset(0f, horizon), Offset(width, horizon), 1.dp.toPx())
            val x = xOf(now).coerceIn(0f, width) * move
            val dot = Offset(x, yOf(x))
            val up = dot.y < horizon
            if (up) drawCircle(Color.White.copy(alpha = .28f), 11.dp.toPx(), dot)
            drawCircle(if (up) Color.White else Color(0xFF8FA3BF), 4.5.dp.toPx(), dot)
        }
        Note(otherLabel)
    }
}

// ---------- Wind ----------

@Composable
fun WindCard(w: Weather, key: Any, sticky: StickyContext, onClick: () -> Unit) {
    val c = w.current
    GlassCard(key, sticky, Modifier.fillMaxWidth(), onClick = onClick, header = { CardTitle(Glyph.WIND, tr(R.string.t_wind)) }) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WindRow(tr(R.string.t_wind), "${c.wind.int()} km/h")
                Hairline()
                WindRow(tr(R.string.t_gusts), "${c.gust.int()} km/h")
                Hairline()
                WindRow(tr(R.string.t_direction), "${c.windDir.int()}° ${windName(c.windDir)}")
            }
            Spacer(Modifier.width(16.dp))
            Compass(c.windDir, c.wind)
        }
    }
}

@Composable
private fun WindRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = Color.White.copy(alpha = .62f))
    }
}

/** Die Nadel schwingt beim Oeffnen ein und zittert danach minimal, je staerker der Wind, desto mehr. */
@Composable
private fun Compass(direction: Double, speed: Double) {
    val target = if (direction.isNaN()) 0f else direction.toFloat()
    val angle = remember { Animatable(target - 150f) }
    LaunchedEffect(target) {
        angle.animateTo(target, spring(dampingRatio = .42f, stiffness = 26f))
    }
    val wobble by rememberInfiniteTransition(label = "wind").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "w",
    )
    val shake = (if (speed.isNaN()) 0f else speed.toFloat() / 12f).coerceIn(.5f, 5f)
    Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            for (i in 0 until 72) {
                val a = i * 5f * PI.toFloat() / 180f
                val d = Offset(sin(a), -cos(a))
                val major = i % 18 == 0
                drawLine(
                    Color.White.copy(alpha = if (major) .85f else .3f),
                    center + d * (r - if (major) 9.dp.toPx() else 6.dp.toPx()),
                    center + d * (r - 1.dp.toPx()),
                    if (major) 1.6.dp.toPx() else 1.dp.toPx(),
                )
            }
            rotate(angle.value + 180f + wobble * shake) {
                val tip = Offset(center.x, center.y - r * .72f)
                val tail = Offset(center.x, center.y + r * .72f)
                val gap = r * .32f
                drawLine(Color.White, Offset(center.x, center.y - gap), tip, 2.2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(center.x, center.y + gap), tail, 2.2.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, 4.dp.toPx(), tail, style = Stroke(1.8.dp.toPx()))
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 7.dp.toPx())
                    lineTo(tip.x - 6.dp.toPx(), tip.y + 5.dp.toPx())
                    lineTo(tip.x + 6.dp.toPx(), tip.y + 5.dp.toPx())
                    close()
                }
                drawPath(head, Color.White)
            }
        }
        for ((label, alignment) in listOf(
            tr(R.string.dir_n) to Alignment.TopCenter, tr(R.string.dir_e) to Alignment.CenterEnd,
            tr(R.string.dir_s) to Alignment.BottomCenter, tr(R.string.dir_w) to Alignment.CenterStart,
        )) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .7f), modifier = Modifier.align(alignment).padding(10.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(speed.int(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("km/h", fontSize = 10.sp)
        }
    }
}

// ---------- Einfache Kacheln ----------

@Composable
fun PrecipTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier, onClick: () -> Unit) {
    val now = w.current.time
    val past = w.hours.filter { it.time in (now - 86400)..now }.sumOf { if (it.precip.isNaN()) 0.0 else it.precip }
    val next = w.hours.filter { it.time in now..(now + 86400) }.sumOf { if (it.precip.isNaN()) 0.0 else it.precip }
    Tile(Glyph.DROP, tr(R.string.t_precip), key, sticky, modifier, onClick = onClick) {
        Column {
            BigValue(past.int(), "mm")
            Text(tr(R.string.precip_last24), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp)
        }
        Note(if (next >= .5) tr(R.string.precip_next, next.int()) else tr(R.string.precip_none))
    }
}

@Composable
fun FeelsTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier, onClick: () -> Unit) {
    val c = w.current
    val diff = c.feels - c.temp
    val note = when {
        diff.isNaN() -> ""
        abs(diff) < 1.5 -> tr(R.string.feels_same)
        diff < 0 && c.wind > 15 -> tr(R.string.feels_wind)
        diff < 0 -> tr(R.string.feels_colder)
        c.humidity > 65 -> tr(R.string.feels_humid)
        else -> tr(R.string.feels_warmer)
    }
    Tile(Glyph.THERMO, tr(R.string.t_feels), key, sticky, modifier, onClick = onClick) {
        BigValue(c.feels.deg())
        Note(note)
    }
}

@Composable
fun HumidityTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier, onClick: () -> Unit) {
    val dew = w.hoursFromNow.firstOrNull()?.dew ?: Double.NaN
    Tile(Glyph.HUMIDITY, tr(R.string.t_humidity), key, sticky, modifier, onClick = onClick) {
        BigValue("${w.current.humidity.int()} %")
        Note(tr(R.string.dew_note, dew.deg()))
    }
}

@Composable
fun VisibilityTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier, onClick: () -> Unit) {
    val metres = w.hoursFromNow.firstOrNull()?.visibility ?: Double.NaN
    val km = metres / 1000
    val note = when {
        km.isNaN() -> ""
        km >= 20 -> tr(R.string.vis_perfect)
        km >= 10 -> tr(R.string.vis_good)
        km >= 4 -> tr(R.string.vis_hazy)
        else -> tr(R.string.vis_poor)
    }
    Tile(Glyph.EYE, tr(R.string.t_visibility), key, sticky, modifier, onClick = onClick) {
        BigValue(km.int(), "km")
        Note(note)
    }
}

@Composable
fun PressureTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier, onClick: () -> Unit) {
    val p = w.current.pressure
    val soon = w.hoursFromNow.getOrNull(3)?.pressure ?: p
    val delta = soon - p
    val trend = when {
        delta.isNaN() -> ""
        delta > 0.7 -> tr(R.string.p_rising)
        delta < -0.7 -> tr(R.string.p_falling)
        else -> tr(R.string.p_steady)
    }
    val move by rememberReveal(150, 1200)
    Tile(Glyph.GAUGE, tr(R.string.t_pressure), key, sticky, modifier, onClick = onClick) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(116.dp)) {
                val r = size.minDimension / 2
                val start = 135f
                val sweep = 270f
                for (i in 0..54) {
                    val a = (start + sweep * i / 54f) * PI.toFloat() / 180f
                    val d = Offset(cos(a), sin(a))
                    drawLine(Color.White.copy(alpha = .35f), center + d * (r * .82f), center + d * r, 1.5.dp.toPx())
                }
                val fraction = if (p.isNaN()) .5f else (((p - 960) / 100).toFloat()).coerceIn(0f, 1f)
                val a = (start + sweep * fraction * move) * PI.toFloat() / 180f
                val d = Offset(cos(a), sin(a))
                drawLine(Color.White, center + d * (r * .74f), center + d * (r * 1.02f), 3.5.dp.toPx(), StrokeCap.Round)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.int(), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("hPa", fontSize = 11.sp)
                Text(trend, fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
            }
        }
    }
}

// ---------- Mond ----------

@Composable
fun MoonTile(w: Weather, key: Any, sticky: StickyContext, modifier: Modifier) {
    val m = remember(w.current.time) { moonInfo(w.current.time) }
    Tile(Glyph.MOON, tr(R.string.t_moon), key, sticky, modifier) {
        Column {
            Text(m.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            MoonDisc(m.phase, Modifier.size(58.dp))
        }
        Note(tr(R.string.m_lit, (m.illumination * 100).roundToInt()) + " · " + trPlural(R.plurals.m_full_in, m.daysToFull))
    }
}

@Composable
private fun MoonDisc(phase: Double, modifier: Modifier) {
    val reveal by rememberReveal(120, 1000)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(Color.Black.copy(alpha = .38f), r, c)
        val mirrored = phase > .5
        val p = if (mirrored) 1 - phase else phase
        val rx = r * abs(cos(2 * PI * p)).toFloat()
        val bounds = Rect(c, r)
        val ellipse = Rect(c.x - rx, c.y - r, c.x + rx, c.y + r)
        val lit = Path().apply {
            arcTo(bounds, -90f, 180f, true)
            // Sichel: Trennlinie biegt zum Rand; Dreiviertelmond: zur Gegenseite
            arcTo(ellipse, 90f, if (p < .25) -180f else 180f, false)
            close()
        }
        scale(if (mirrored) -1f else 1f, 1f, c) {
            drawPath(lit, Color(0xFFEFECE0).copy(alpha = reveal))
        }
    }
}

// ---------- Luftqualitaet ----------

private val AQI_COLORS = listOf(Color(0xFF4CD964), Color(0xFFFFD60A), Color(0xFFFF9F0A), Color(0xFFFF453A), Color(0xFF9B59D0))

@Composable
fun AirCard(air: Air, key: Any, sticky: StickyContext) {
    GlassCard(key, sticky, Modifier.fillMaxWidth(), header = { CardTitle(Glyph.AIR, tr(R.string.t_air)) }) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BigValue(air.aqi.int())
                Spacer(Modifier.width(10.dp))
                Text(aqiLabel(air.aqi), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.height(8.dp))
            ScaleBar(if (air.aqi.isNaN()) 0f else (air.aqi / 100).toFloat(), AQI_COLORS)
            Spacer(Modifier.height(10.dp))
            Text(
                tr(R.string.aqi_details, air.pm25.int(), air.pm10.int(), air.ozone.int(), air.no2.int()),
                fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = .8f),
            )
        }
    }
}

// ---------- Kartenteaser ----------

@Composable
fun MapCard(key: Any, sticky: StickyContext, onClick: () -> Unit) {
    val sweep by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "s",
    )
    Tile(Glyph.MAP, tr(R.string.t_map), key, sticky, Modifier.fillMaxWidth(), height = 170.dp, onClick = onClick) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .22f))) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.maxDimension * .62f
                for (i in 1..3) drawCircle(Color.White.copy(alpha = .1f), r * i / 3f, style = Stroke(1.dp.toPx()))
                rotate(sweep) {
                    drawArc(
                        Brush.sweepGradient(0f to Color.Transparent, .86f to Color.Transparent, 1f to Color(0x8858C4FF), center = center),
                        -60f, 60f, true,
                        Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                    )
                }
                drawCircle(Color.White, 5.dp.toPx())
                drawCircle(Color(0xFF0A84FF), 3.5.dp.toPx())
            }
            Text(tr(R.string.map_open), fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
    }
}
