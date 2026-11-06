package de.tonyapps.wetter.ui

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import de.tonyapps.wetter.R
import de.tonyapps.wetter.data.tr
import org.json.JSONObject
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
    // Die Seite ist erst spaeter fertig; bis dahin kennt Compose die echten Raender
    val latestTop by rememberUpdatedState(top)
    val latestBottom by rememberUpdatedState(bottom)

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1C33))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Ohne feste Groesse misst die WebView nach Inhalt: Die Seite haette Hoehe 0.
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(0xFF0B1C33.toInt())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "OpeniWeather/1.9 (+https://github.com/tjeronimo86/Wetter)"
                    // Die Seite kommt aus file:// und holt Daten per fetch von den Wetterdiensten.
                    settings.allowUniversalAccessFromFileURLs = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.i("WetterMap", "${message.message()} @${message.lineNumber()}")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            Log.w("WetterMap", "Fehler ${error.errorCode} ${error.description}: ${request.url}")
                        }

                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            Log.w("WetterMap", "HTTP ${response.statusCode}: ${request.url}")
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            val labels = JSONObject()
                                .put("rain", tr(R.string.t_precip))
                                .put("temp", tr(R.string.d_temp))
                                .put("wind", tr(R.string.t_wind))
                                .put("radarOff", tr(R.string.map_radar_off))
                                .put("loading", tr(R.string.map_loading))
                                .put("modelOff", tr(R.string.map_model_off))
                                .put("locale", Locale.getDefault().toLanguageTag())
                            val js = String.format(
                                Locale.US, "start(%.4f,%.4f,%d,%d,%s)", place.lat, place.lon,
                                latestTop.value.toInt(), latestBottom.value.toInt(), labels.toString(),
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
            tr(R.string.done),
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
