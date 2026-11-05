package de.tonyapps.wetter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.R
import de.tonyapps.wetter.data.Changelog
import de.tonyapps.wetter.data.tr
import de.tonyapps.wetter.update.AppRelease
import de.tonyapps.wetter.update.UpdateState
import de.tonyapps.wetter.update.Updater

private val Blue = Color(0xFF0A84FF)
private val Panel = Color(0xFF1C1C1E)

/** App-Version mit Update-Knopf, unten in der Ortsliste. */
@Composable
fun VersionRow() {
    val context = LocalContext.current
    val state = Updater.state
    val status = when (state) {
        UpdateState.Idle -> tr(R.string.u_tap_search)
        UpdateState.Checking -> tr(R.string.u_searching)
        UpdateState.UpToDate -> tr(R.string.u_current)
        is UpdateState.Available -> tr(R.string.u_available_named, state.release.title)
        is UpdateState.Downloading -> tr(R.string.u_loading, (state.progress * 100).toInt())
        is UpdateState.Installing -> tr(R.string.u_installing)
        is UpdateState.Failed -> state.message
    }
    val highlight = state is UpdateState.Available
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable {
                when (state) {
                    is UpdateState.Available -> Updater.sheetHidden = false
                    else -> Updater.check(context)
                }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Changelog.versionLabel(context), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                status,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (highlight) Blue else Color.White.copy(alpha = .6f),
            )
        }
        Spacer(Modifier.width(10.dp))
        val spin by rememberInfiniteTransition(label = "update").animateFloat(
            0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spin",
        )
        val busy = state is UpdateState.Checking
        GlyphIcon(
            Glyph.REFRESH, 24.dp, Color.White.copy(alpha = .85f),
            Modifier.graphicsLayer { rotationZ = if (busy) spin else 0f },
        )
    }
}

/** Hinweis auf ein Update mit Fortschritt; liegt ueber allem anderen. */
@Composable
fun UpdateSheet() {
    val state = Updater.state
    val release: AppRelease? = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Installing -> state.release
        else -> null
    }
    val shown = release != null && !Updater.sheetHidden
    // Beim Ausblenden bleibt der Inhalt noch stehen
    var last by remember { mutableStateOf(release) }
    if (release != null) last = release

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = shown, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .5f))
                    .pointerInput(Unit) { detectTapGestures { } },
            )
        }
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .9f, stiffness = 320f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(),
        ) {
            last?.let { SheetContent(it, state) }
        }
    }
}

@Composable
private fun SheetContent(release: AppRelease, state: UpdateState) {
    val context = LocalContext.current
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(Panel)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = bottom + 16.dp),
    ) {
        Text(tr(R.string.u_available), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(release.title, fontSize = 14.sp, color = Color.White.copy(alpha = .65f))
        Spacer(Modifier.height(10.dp))
        release.notes.take(6).forEach { note ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
                Spacer(Modifier.width(8.dp))
                Text(note, fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = .85f))
            }
        }
        Spacer(Modifier.height(16.dp))
        when (state) {
            is UpdateState.Downloading -> ProgressLine(state.progress, tr(R.string.u_loading, (state.progress * 100).toInt()))
            is UpdateState.Installing -> ProgressLine(1f, tr(R.string.u_confirm))
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(tr(R.string.u_later), Color(0xFF2C2C2E), Modifier.weight(1f)) { Updater.sheetHidden = true }
                SheetButton(tr(R.string.u_update), Blue, Modifier.weight(1f)) { Updater.install(context, release) }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    )
}

@Composable
private fun ProgressLine(progress: Float, label: String) {
    Column {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF3A3A3C))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Blue))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = .75f))
    }
}
