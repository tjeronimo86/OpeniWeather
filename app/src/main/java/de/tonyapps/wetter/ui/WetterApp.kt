package de.tonyapps.wetter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import de.tonyapps.wetter.data.Place
import de.tonyapps.wetter.data.WeatherViewModel
import de.tonyapps.wetter.sky.*
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun WetterApp(openPlace: String? = null, vm: WeatherViewModel = viewModel()) {
    WetterTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.locate()
        }
        LaunchedEffect(Unit) {
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                vm.locate()
            } else {
                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshAll() }

        val pager = rememberPagerState(pageCount = { vm.places.size })
        LaunchedEffect(openPlace) {
            val index = vm.places.indexOfFirst { it.id == openPlace }
            if (index >= 0) pager.scrollToPage(index)
        }
        var showList by remember { mutableStateOf(false) }
        var mapPlace by remember { mutableStateOf<Place?>(null) }
        var detail by remember { mutableStateOf<DetailRequest?>(null) }
        var preview by remember { mutableStateOf<Place?>(null) }

        // Himmel je Seite. Zwischen den Seiten wird nicht ueberblendet, sondern gerechnet:
        // Die Parameter beider Orte werden passend zur Wischposition gemischt.
        val skies: List<SkyParams> = vm.places.map { p -> vm.weather[p.id]?.let { skyFor(it) } ?: SkyParams(cloud = .3f) }
        val demo = vm.demoScene
        val settled = pager.settledPage.coerceIn(0, (skies.size - 1).coerceAtLeast(0))
        val base = if (demo >= 0) DEMO_SCENES[demo].second else skies.getOrElse(settled) { SkyParams() }
        val smooth by rememberSmoothSky(base, snapKey = if (demo >= 0) "demo" else settled)
        val latestSkies by rememberUpdatedState(skies)
        val isDemo by rememberUpdatedState(demo >= 0)

        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barHeight = 52.dp

        Box(Modifier.fillMaxSize().background(Color(0xFF123F7D))) {
            SkyBackground({
                val offset = pager.currentPageOffsetFraction
                val list = latestSkies
                if (isDemo || abs(offset) < .001f || list.isEmpty()) {
                    smooth
                } else {
                    val page = pager.currentPage.coerceIn(0, list.lastIndex)
                    val other = (if (offset > 0) page + 1 else page - 1).coerceIn(0, list.lastIndex)
                    lerpSky(list[page], list[other], abs(offset))
                }
            })

            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { vm.places.getOrNull(it)?.id ?: it },
            ) { page ->
                val place = vm.places.getOrNull(page) ?: return@HorizontalPager
                WeatherPage(
                    place = place,
                    weather = vm.weather[place.id],
                    errorText = vm.error,
                    refreshing = vm.loading,
                    onRefresh = { vm.refreshAll(force = true) },
                    demoName = if (demo >= 0) DEMO_SCENES[demo].first else null,
                    onCycleDemo = { vm.demoScene = if (demo + 1 >= DEMO_SCENES.size) -1 else demo + 1 },
                    onDetail = { detail = it },
                    onMap = { mapPlace = place },
                    bottomPadding = barHeight + bottomInset,
                )
            }

            BottomBar(
                pager = pager,
                hasLocation = vm.places.firstOrNull()?.isCurrent == true,
                height = barHeight,
                inset = bottomInset,
                onMap = { mapPlace = vm.places.getOrNull(pager.currentPage) },
                onList = { showList = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            val detailRequest = detail
            val detailWeather = vm.places.getOrNull(pager.currentPage)?.let { vm.weather[it.id] }
            if (detailRequest != null && detailWeather != null) {
                DetailSheet(detailWeather, detailRequest) { detail = null }
            }

            AnimatedVisibility(
                visible = showList,
                enter = fadeIn(tween(220)) + scaleIn(tween(280), initialScale = 1.06f),
                exit = fadeOut(tween(200)) + scaleOut(tween(240), targetScale = 1.06f),
            ) {
                PlacesScreen(
                    places = vm.places,
                    weather = vm.weather,
                    onPick = { index ->
                        scope.launch { pager.scrollToPage(index) }
                        showList = false
                    },
                    onPreview = {
                        vm.preview(it)
                        preview = it
                    },
                    onRemove = { vm.remove(it) },
                    onClose = { showList = false },
                )
            }

            preview?.let { p ->
                PlacePreview(
                    place = p,
                    w = vm.weather[p.id],
                    onCancel = { preview = null },
                    onAdd = {
                        vm.add(p)
                        preview = null
                    },
                )
            }

            AnimatedVisibility(
                visible = mapPlace != null,
                enter = slideInVertically(spring(dampingRatio = .9f, stiffness = 320f)) { it } + fadeIn(),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(),
            ) {
                // Beim Ausblenden bleibt der letzte Ort stehen, deshalb der Merker.
                val shown = remember { mutableStateOf(mapPlace) }
                mapPlace?.let { shown.value = it }
                shown.value?.let { MapScreen(it) { mapPlace = null } }
            }
        }
    }
}

@Composable
private fun BottomBar(
    pager: PagerState,
    hasLocation: Boolean,
    height: Dp,
    inset: Dp,
    onMap: () -> Unit,
    onList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0x33123F7D), Color(0x99123F7D))))
            .padding(bottom = inset)
            .height(height),
    ) {
        Box(Modifier.align(Alignment.CenterStart).clickable(onClick = onMap).padding(horizontal = 18.dp, vertical = 12.dp)) {
            GlyphIcon(Glyph.MAP, 24.dp, Color.White)
        }
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pager.pageCount.coerceAtMost(12)) { i ->
                val pageAlpha = Modifier.graphicsLayer {
                    val distance = abs(pager.currentPage + pager.currentPageOffsetFraction - i).coerceIn(0f, 1f)
                    alpha = 1f - distance * .62f
                }
                if (i == 0 && hasLocation) {
                    ArrowIcon(10.dp, Color.White, pageAlpha)
                } else {
                    Dot(Color.White, 7.dp, pageAlpha)
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).clickable(onClick = onList).padding(horizontal = 18.dp, vertical = 12.dp)) {
            ListIcon(24.dp, Color.White)
        }
    }
}

