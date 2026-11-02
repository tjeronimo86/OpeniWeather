package de.tonyapps.wetter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.*
import kotlinx.coroutines.launch
import kotlin.math.*

enum class Metric(val title: String, val unit: String, val color: Color, val bars: Boolean = false) {
    TEMP("Temperatur", "°", Color(0xFFFFB340)),
    FEELS("Gefühlte Temperatur", "°", Color(0xFFFF9F68)),
    RAIN("Niederschlag", " %", Color(0xFF58C4FF), bars = true),
    AMOUNT("Regenmenge", " mm", Color(0xFF0A84FF), bars = true),
    SNOW("Schnee", " cm", Color(0xFFBFE3FF), bars = true),
    WIND("Wind", " km/h", Color(0xFF5EE0C8)),
    GUST("Böen", " km/h", Color(0xFF7DE3A0)),
    HUMIDITY("Luftfeuchtigkeit", " %", Color(0xFF7DA7FF)),
    DEW("Taupunkt", "°", Color(0xFF9FD0FF)),
    PRESSURE("Luftdruck", " hPa", Color(0xFFD0B3FF)),
    UV("UV-Index", "", Color(0xFFFFD60A), bars = true),
    VISIBILITY("Sichtweite", " km", Color(0xFFE0E6EE));

    fun of(h: Hour): Double = when (this) {
        TEMP -> h.temp
        FEELS -> h.feels
        RAIN -> h.pop
        AMOUNT -> h.precip
        SNOW -> h.snow
        WIND -> h.wind
        GUST -> h.gust
        HUMIDITY -> h.humidity
        DEW -> h.dew
        PRESSURE -> h.pressure
        UV -> h.uv
        VISIBILITY -> h.visibility / 1000
    }

    /** Achse bei null beginnen, wo null eine echte Aussage ist. */
    val zeroBased: Boolean get() = this in setOf(RAIN, AMOUNT, SNOW, WIND, GUST, UV, VISIBILITY, HUMIDITY)
}

private val SheetBg = Color(0xFF1C1C1E)
private val ChipBg = Color(0xFF2C2C2E)

/** Vollbild-Blatt fuer Detailkurven: Tag waehlen, Messgroesse waehlen, auf der Kurve entlangfahren. */
@Composable
fun DetailSheet(w: Weather, request: DetailRequest, onClose: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val screen = with(density) { 1200.dp.toPx() }
    val slide = remember { Animatable(screen) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        slide.animateTo(0f, spring(dampingRatio = .9f, stiffness = 320f))
    }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            slide.animateTo(screen, tween(260, easing = FastOutLinearInEasing))
            onClose()
        }
    }
    BackHandler { close() }

    val days = w.upcomingDays.take(10)
    var date by remember { mutableStateOf(request.date ?: days.first().date) }
    var metric by remember { mutableStateOf(request.metric) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = (1f - slide.value / screen).coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = .45f)),
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = top + 10.dp)
            .graphicsLayer { translationY = slide.value }
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            .background(SheetBg)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // Griff: nach unten ziehen schliesst das Blatt
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (slide.value > 120.dp.toPx()) {
                                close()
                            } else {
                                scope.launch { slide.animateTo(0f, spring(dampingRatio = .9f)) }
                            }
                        },
                        onVerticalDrag = { _, drag ->
                            scope.launch { slide.snapTo((slide.value + drag).coerceAtLeast(0f)) }
                        },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(7.dp))
            Box(Modifier.size(38.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF55555A)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Wetterlage", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Fertig", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0A84FF), modifier = Modifier.clickable { close() })
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(days) { i, d ->
                val selected = d.date == date
                Column(
                    Modifier.width(44.dp).clickable { date = d.date },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (i == 0) "Heu" else weekdayShort(d), fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(34.dp).clip(RectangleShapeCircle).background(if (selected) Color(0xFF0A84FF) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.date.dayOfMonth.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Metric.entries.forEach { m ->
                Text(
                    m.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (m == metric) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (m == metric) Color.White else ChipBg)
                        .clickable { metric = m }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = bottom + 16.dp)) {
            Spacer(Modifier.height(14.dp))
            val hours = remember(w, date) { w.hoursOf(date) }
            val day = days.firstOrNull { it.date == date }
            key(date, metric) {
                Chart(w, hours, metric, day)
            }
            Spacer(Modifier.height(14.dp))
            if (day != null) DayFacts(w, day)
        }
    }
}

private fun fmt(v: Double, m: Metric): String = when {
    v.isNaN() -> "–"
    m == Metric.AMOUNT || m == Metric.SNOW -> String.format(java.util.Locale.GERMAN, "%.1f%s", v, m.unit)
    else -> "${v.roundToInt()}${m.unit}"
}

@Composable
private fun Chart(w: Weather, hours: List<Hour>, metric: Metric, day: Day?) {
    if (hours.isEmpty()) {
        Text("Keine Stundenwerte für diesen Tag.", color = Color.White.copy(alpha = .6f))
        return
    }
    val values = remember(hours, metric) { hours.map { metric.of(it) } }
    val valid = values.filter { !it.isNaN() }
    val nowIndex = hours.indexOfLast { it.time <= w.current.time }
    var picked by remember { mutableIntStateOf(-1) }
    val draw by rememberReveal(60, 850)

    val low = valid.minOrNull() ?: 0.0
    val high = valid.maxOrNull() ?: 1.0
    val shownIndex = when {
        picked >= 0 -> picked
        nowIndex >= 0 && nowIndex < hours.size - 1 -> nowIndex
        else -> -1
    }
    Text(metric.title, fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
    Text(
        if (shownIndex >= 0) fmt(values[shownIndex], metric) else "${fmt(high, metric)}  /  ${fmt(low, metric)}",
        fontSize = 34.sp, fontWeight = FontWeight.Medium,
    )
    Text(
        when {
            shownIndex >= 0 -> "${clock(hours[shownIndex].time, w.zone)} · ${conditionText(hours[shownIndex].code, hours[shownIndex].isDay, hours[shownIndex].cloud)}"
            day != null -> "${weekdayLong(day)}: Höchst- und Tiefstwert"
            else -> ""
        },
        fontSize = 14.sp, color = Color.White.copy(alpha = .7f),
    )
    Spacer(Modifier.height(16.dp))

    val minY = if (metric.zeroBased) 0.0 else floor(low - 1.5)
    val maxY = run {
        val top = if (high - minY < 1.0) minY + 1.0 else high
        top + (top - minY) * .12
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .pointerInput(hours) {
                detectHorizontalDragGestures(
                    onDragEnd = { picked = -1 },
                    onDragCancel = { picked = -1 },
                ) { change, _ ->
                    change.consume()
                    picked = ((change.position.x / (size.width * .9f)) * (hours.size - 1)).roundToInt().coerceIn(0, hours.size - 1)
                }
            }
            .pointerInput(hours) {
                detectTapGestures(
                    onPress = { o ->
                        picked = ((o.x / (size.width * .9f)) * (hours.size - 1)).roundToInt().coerceIn(0, hours.size - 1)
                        tryAwaitRelease()
                        picked = -1
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val plotW = size.width * .9f
            val plotH = size.height - 22.dp.toPx()
            fun x(i: Int) = i.toFloat() / (hours.size - 1).coerceAtLeast(1) * plotW
            fun y(v: Double) = (plotH - ((v - minY) / (maxY - minY)).toFloat() * plotH)

            // Raster mit y-Beschriftung
            val text = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(150, 255, 255, 255)
                textSize = 11.sp.toPx()
                isAntiAlias = true
            }
            for (i in 0..4) {
                val v = minY + (maxY - minY) * i / 4
                val yy = y(v)
                drawLine(Color.White.copy(alpha = .1f), Offset(0f, yy), Offset(plotW, yy), 1f)
                drawContext.canvas.nativeCanvas.drawText(
                    if (metric == Metric.AMOUNT || metric == Metric.SNOW) String.format(java.util.Locale.GERMAN, "%.1f", v) else v.roundToInt().toString(),
                    plotW + 6.dp.toPx(), yy + 4.dp.toPx(), text,
                )
            }
            hours.forEachIndexed { i, h ->
                if (i % 6 == 0) {
                    drawLine(Color.White.copy(alpha = .08f), Offset(x(i), 0f), Offset(x(i), plotH), 1f)
                    drawContext.canvas.nativeCanvas.drawText(hourLabel(h.time, w.zone).take(2), x(i), plotH + 16.dp.toPx(), text)
                }
            }

            val line: Brush = if (metric == Metric.TEMP || metric == Metric.FEELS) {
                val stops = (0..6).map { tempColor(minY + (maxY - minY) * (6 - it) / 6.0) }
                Brush.verticalGradient(stops, 0f, plotH)
            } else {
                SolidColor(metric.color)
            }

            clipRect(right = plotW * draw + 2f) {
                if (metric.bars) {
                    val bw = plotW / hours.size * .62f
                    values.forEachIndexed { i, v ->
                        if (!v.isNaN()) {
                            val yy = y(v)
                            drawRoundRect(metric.color.copy(alpha = if (i == shownIndex) 1f else .72f), Offset(x(i) - bw / 2, yy), Size(bw, plotH - yy), CornerRadius(2f))
                        }
                    }
                } else {
                    val path = Path()
                    var started = false
                    values.forEachIndexed { i, v ->
                        if (!v.isNaN()) {
                            if (!started) {
                                path.moveTo(x(i), y(v))
                                started = true
                            } else {
                                path.lineTo(x(i), y(v))
                            }
                        }
                    }
                    val area = Path().apply {
                        addPath(path)
                        lineTo(x(values.lastIndex), plotH)
                        lineTo(x(0), plotH)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(metric.color.copy(alpha = .30f), Color.Transparent), 0f, plotH))
                    // Vergangenes gedimmt, Zukunft kraeftig
                    val split = if (nowIndex >= 0) x(nowIndex) else 0f
                    clipRect(right = split) { drawPath(path, line, alpha = .45f, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                    clipRect(left = split) { drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }
            if (shownIndex >= 0 && !values[shownIndex].isNaN()) {
                val p = Offset(x(shownIndex), y(values[shownIndex]))
                drawLine(Color.White.copy(alpha = .4f), Offset(p.x, 0f), Offset(p.x, plotH), 1.dp.toPx())
                drawCircle(SheetBg, 7.dp.toPx() * draw, p)
                drawCircle(Color.White, 5.dp.toPx() * draw, p)
            }
        }
    }
}

@Composable
private fun DayFacts(w: Weather, d: Day) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ChipBg).padding(horizontal = 14.dp, vertical = 6.dp)) {
        Fact("Wetter", conditionText(d.code, true))
        Fact("Höchstwert / Tiefstwert", "${d.max.deg()} / ${d.min.deg()}")
        Fact("Niederschlag", "${d.precip.int()} mm · ${d.pop.int()} %")
        Fact("Wind maximal", "${d.windMax.int()} km/h ${windName(d.windDir)}")
        Fact("Böen maximal", "${d.gustMax.int()} km/h")
        Fact("UV-Index maximal", "${d.uvMax.int()} · ${uvLabel(d.uvMax)}")
        Fact("Sonne", "${clock(d.sunrise, w.zone)} – ${clock(d.sunset, w.zone)}")
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = .65f), modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp)
    }
}

