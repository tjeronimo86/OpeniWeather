package de.tonyapps.wetter.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.*
import de.tonyapps.wetter.sky.SkyBackground
import de.tonyapps.wetter.sky.SkyParams
import de.tonyapps.wetter.sky.skyBrush
import de.tonyapps.wetter.sky.skyFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RELEASES_URL = "https://github.com/tjeronimo86/Wetter/releases/latest"

@Composable
fun PlacesScreen(
    places: List<Place>,
    weather: Map<String, Weather>,
    onPick: (Int) -> Unit,
    onPreview: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onClose: () -> Unit,
    onNews: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(320)
        results = runCatching { Api.search(query.trim()) }.getOrDefault(emptyList())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = top + 8.dp, start = 16.dp, end = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Wetter", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Fertig", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0A84FF), modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (query.isEmpty()) Text("Stadt oder Ort suchen", fontSize = 16.sp, color = Color.White.copy(alpha = .5f))
                    inner()
                }
            },
        )
        Spacer(Modifier.height(10.dp))

        if (query.isNotBlank()) {
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp)) {
                items(results, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clickable { onPreview(p) }.padding(vertical = 11.dp)) {
                        Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        if (p.region.isNotBlank()) Text(p.region, fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
                    }
                    Hairline()
                }
                if (results.isEmpty() && query.trim().length >= 2) {
                    item { Text("Keine Treffer", color = Color.White.copy(alpha = .6f), modifier = Modifier.padding(vertical = 14.dp)) }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = bottom + 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(places, key = { _, p -> p.id }) { i, p ->
                    PlaceRow(p, weather[p.id], { onPick(i) }, { onRemove(p) })
                }
                item(key = "news") {
                    Text(
                        "Was ist neu",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0A84FF),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onNews).padding(vertical = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                item(key = "version") { VersionInfo() }
            }
        }
    }
}

/** Softwareversion und Weg zur neuesten APK. Das Repo ist privat: GitHub verlangt im Browser den Login. */
@Composable
private fun VersionInfo() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Changelog.versionLabel(context), fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
        Text(
            "Neueste Version auf GitHub ansehen",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF0A84FF),
            modifier = Modifier
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                }
                .padding(vertical = 12.dp),
        )
    }
}

/** Nach links wischen entfernt den Ort; der eigene Standort bleibt fest. */
@Composable
private fun PlaceRow(place: Place, w: Weather?, onClick: () -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val sky = remember(w) { w?.let { skyFor(it) } ?: SkyParams() }
    Box(Modifier.fillMaxWidth().height(112.dp)) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF453A)).padding(end = 22.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (!place.isCurrent) Text("Löschen", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .pointerInput(place.id) {
                    if (place.isCurrent) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value < -size.width * .38f) {
                                    offset.animateTo(-size.width.toFloat(), tween(180))
                                    onDelete()
                                } else {
                                    offset.animateTo(0f, spring())
                                }
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + drag).coerceAtMost(0f)) }
                    }
                }
                .clip(RoundedCornerShape(16.dp))
                .background(skyBrush(sky))
                .clickable(onClick = onClick)
                .padding(14.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(place.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (place.isCurrent) "Mein Standort" else w?.let { clock(it.current.time, it.zone) } ?: "",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(w?.let { conditionText(it.current.code, it.current.isDay, it.current.cloud) } ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End) {
                Text(w?.current?.temp?.deg() ?: "–", fontSize = 40.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.weight(1f))
                val today = w?.takeIf { it.days.isNotEmpty() }?.today
                if (today != null) Text("H:${today.max.deg()}  T:${today.min.deg()}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Vorschau eines Suchtreffers mit Abbrechen / Hinzufuegen, wie beim Tippen auf einen Ort. */
@Composable
fun PlacePreview(place: Place, w: Weather?, onCancel: () -> Unit, onAdd: () -> Unit) {
    BackHandler(onBack = onCancel)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = .9f, stiffness = 320f)) }
    val params = remember(w) { w?.let { skyFor(it) } ?: SkyParams(cloud = .4f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = rise.value * size.height }
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        SkyBackground({ params })
        WeatherPage(
            place = place, weather = w, errorText = null, refreshing = false, onRefresh = {},
            demoName = null, onCycleDemo = {}, onDetail = {}, onMap = {}, bottomPadding = 20.dp,
        )
        Row(Modifier.fillMaxWidth().padding(top = top + 6.dp, start = 16.dp, end = 16.dp)) {
            Text("Abbrechen", fontSize = 16.sp, modifier = Modifier.clickable(onClick = onCancel))
            Spacer(Modifier.weight(1f))
            Text("Hinzufügen", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onAdd))
        }
    }
}
