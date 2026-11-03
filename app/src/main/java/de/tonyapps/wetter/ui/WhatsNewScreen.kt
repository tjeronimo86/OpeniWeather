package de.tonyapps.wetter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import de.tonyapps.wetter.data.Changelog

/** Der Update-Verlauf. Erscheint nach jedem Update einmal und liegt in der Ortsliste. */
@Composable
fun WhatsNewScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val releases = remember { Changelog.read(context) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(top = top + 8.dp, start = 16.dp, end = 16.dp),
    ) {
        Row {
            Text("Was ist neu", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Fertig", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0A84FF), modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottom + 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(releases, key = { it.title }) { release ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(14.dp),
                ) {
                    Text(release.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    release.items.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("•", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
                            Spacer(Modifier.width(8.dp))
                            Text(item, fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = .85f))
                        }
                    }
                }
            }
        }
    }
}
