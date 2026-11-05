package de.tonyapps.wetter.ui

import de.tonyapps.wetter.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.*
import de.tonyapps.wetter.sky.skyFor
import java.time.LocalDate
import kotlin.math.roundToInt

private val HeaderTop = 34.dp
private val HeaderHeight = 268.dp
private val CollapseRange = 170.dp
private val PinnedBelowInset = 80.dp

data class DetailRequest(val metric: Metric, val date: LocalDate? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherPage(
    place: Place,
    weather: Weather?,
    errorText: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    demoName: String?,
    onCycleDemo: () -> Unit,
    onDetail: (DetailRequest) -> Unit,
    onMap: () -> Unit,
    bottomPadding: Dp,
    startItem: Int = 0,
) {
    val insets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startItem)
    val density = LocalDensity.current
    val sticky = remember(list, insets) { StickyContext(list, with(density) { (insets + PinnedBelowInset).toPx() }) }
    val scroll: () -> Float = {
        if (list.firstVisibleItemIndex > 0) 100000f else list.firstVisibleItemScrollOffset.toFloat()
    }
    val tint = remember(weather) { weather?.let { cardTint(skyFor(it)) } ?: Color(0x55123F7D) }

    CompositionLocalProvider(LocalCardTint provides tint) {
        Box(Modifier.fillMaxSize()) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                if (weather != null && weather.days.isNotEmpty()) {
                    PageList(list, sticky, weather, insets, bottomPadding, onDetail, onMap)
                } else {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                errorText ?: tr(R.string.loading_weather),
                                fontSize = 15.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = insets + HeaderHeight, start = 30.dp, end = 30.dp),
                            )
                        }
                    }
                }
            }
            Header(place, weather, insets, scroll, demoName, onCycleDemo)
        }
    }
}

@Composable
private fun PageList(
    list: LazyListState,
    sticky: StickyContext,
    w: Weather,
    insets: Dp,
    bottomPadding: Dp,
    onDetail: (DetailRequest) -> Unit,
    onMap: () -> Unit,
) {
    val gap = 10.dp
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding + 20.dp),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        item(key = "top") { Spacer(Modifier.height(insets + HeaderHeight)) }
        w.alerts.take(3).forEachIndexed { i, alert ->
            item(key = "alert$i") { AlertCard(alert, w.zone, "alert$i", sticky) }
        }
        item(key = "hourly") { HourlyCard(w, "hourly", sticky) { onDetail(DetailRequest(Metric.TEMP)) } }
        item(key = "days") { TenDayCard(w, "days", sticky) { onDetail(DetailRequest(Metric.TEMP, it.date)) } }
        item(key = "map") { MapCard("map", sticky, onMap) }
        item(key = "wind") { WindCard(w, "wind", sticky) { onDetail(DetailRequest(Metric.WIND)) } }
        item(key = "r1") {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                UvTile(w, "r1", sticky, Modifier.weight(1f)) { onDetail(DetailRequest(Metric.UV)) }
                SunTile(w, "r1", sticky, Modifier.weight(1f))
            }
        }
        item(key = "r2") {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PrecipTile(w, "r2", sticky, Modifier.weight(1f)) { onDetail(DetailRequest(Metric.RAIN)) }
                FeelsTile(w, "r2", sticky, Modifier.weight(1f)) { onDetail(DetailRequest(Metric.FEELS)) }
            }
        }
        item(key = "r3") {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                HumidityTile(w, "r3", sticky, Modifier.weight(1f)) { onDetail(DetailRequest(Metric.HUMIDITY)) }
                VisibilityTile(w, "r3", sticky, Modifier.weight(1f)) { onDetail(DetailRequest(Metric.VISIBILITY)) }
            }
        }
        item(key = "r4") {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                PressureTile(w, "r4", sticky, Modifier.weight(1f)) { onDetail(DetailRequest(Metric.PRESSURE)) }
                MoonTile(w, "r4", sticky, Modifier.weight(1f))
            }
        }
        w.air?.let { air -> item(key = "air") { AirCard(air, "air", sticky) } }
        item(key = "credits") {
            Text(
                tr(R.string.data_credits),
                fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = .6f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}

/** Grosser Kopf, der beim Scrollen zu "Ort / 22° | Bewoelkt" schrumpft. Rein per graphicsLayer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(
    place: Place,
    weather: Weather?,
    insets: Dp,
    scroll: () -> Float,
    demoName: String?,
    onCycleDemo: () -> Unit,
) {
    val density = LocalDensity.current
    val range = with(density) { CollapseRange.toPx() }
    val c = weather?.current
    val condition = c?.let { conditionText(it.code, it.isDay, it.cloud) } ?: ""
    val today = weather?.takeIf { it.days.isNotEmpty() }?.today

    Column(
        Modifier.fillMaxWidth().padding(top = insets + HeaderTop),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            place.name,
            fontSize = 34.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    translationY = -(scroll().coerceAtMost(range)) * .2f
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = onCycleDemo,
                ),
        )
        if (place.isCurrent) {
            Text(
                tr(R.string.my_location),
                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - scroll() / (range * .3f)).coerceIn(0f, 1f)
                },
            )
        }
        Box(contentAlignment = Alignment.TopCenter) {
            // Kompakte Zeile, die erst eingeklappt sichtbar wird
            if (c != null) {
                Text(
                    "${c.temp.roundToInt()}°  |  $condition",
                    fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        val p = (scroll() / range).coerceIn(0f, 1f)
                        alpha = ((p - .62f) / .3f).coerceIn(0f, 1f)
                        translationY = -(scroll().coerceAtMost(range)) * .2f - with(density) { if (place.isCurrent) 16.dp.toPx() else 0f }
                    },
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val p = (scroll() / range).coerceIn(0f, 1f)
                    alpha = (1f - p * 1.9f).coerceIn(0f, 1f)
                    translationY = -scroll().coerceAtMost(range) * .55f
                },
            ) {
                if (c != null) {
                    // Unsichtbares ° links haelt die Ziffern optisch mittig
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.Transparent)) { append("°") }
                            append("${c.temp.roundToInt()}°")
                        },
                        fontSize = 98.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 108.sp,
                    )
                    Text(condition, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    if (today != null) {
                        Spacer(Modifier.height(3.dp))
                        Text(tr(R.string.hi_lo, today.max.deg(), today.min.deg()), fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (demoName != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(tr(R.string.preview_scene, demoName), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .85f))
                }
            }
        }
    }
}
