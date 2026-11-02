package de.tonyapps.wetter.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import de.tonyapps.wetter.data.Place
import java.util.Locale

/**
 * Die Karte laeuft als lokale Leaflet-Seite aus den Assets. Grundkarte, Radar und Modelldaten
 * kommen per Netz; siehe assets/map/map.html.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(place: Place, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1C33))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0xFF0B1C33.toInt())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Die Seite kommt aus file:// und holt Daten per fetch von den Wetterdiensten.
                    settings.allowUniversalAccessFromFileURLs = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            val js = String.format(
                                Locale.US, "start(%.4f,%.4f,%d,%d)", place.lat, place.lon,
                                top.value.toInt(), bottom.value.toInt(),
                            )
                            view.evaluateJavascript(js, null)
                        }
                    }
                    loadUrl("file:///android_asset/map/map.html")
                }
            },
            onRelease = { it.destroy() },
        )
        Text(
            "Fertig",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = top + 10.dp, start = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xCC1C1C1E))
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}
