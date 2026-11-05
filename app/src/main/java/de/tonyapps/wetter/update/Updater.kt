package de.tonyapps.wetter.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import de.tonyapps.wetter.R
import de.tonyapps.wetter.data.tr
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AppRelease(val build: Int, val title: String, val notes: List<String>, val url: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Sucht auf GitHub nach neuen Versionen. Installiert wird von Hand aus dem Browser;
 * Android prueft dabei die Signatur und lehnt fremd signierte APKs als Update ab.
 */
object Updater {
    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L
    private const val ACTION = "de.tonyapps.wetter.UPDATE_RESULT"

    var apiUrl = "https://api.github.com/repos/tjeronimo86/Wetter/releases/latest"
    var allowedDownload = "https://github.com/tjeronimo86/Wetter/releases/download/"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** "Spaeter" schliesst den Hinweis; der Knopf in der Ortsliste bleibt erreichbar. */
    var sheetHidden by mutableStateOf(false)

    private fun installedBuild(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun autoCheck(context: Context) {
        val prefs = context.getSharedPreferences("wetter", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("update-check", 0L) < CHECK_EVERY_MS) return
        prefs.edit().putLong("update-check", now).apply()
        check(context, silent = true)
    }

    fun check(context: Context, silent: Boolean = false) {
        if (state is UpdateState.Checking) return
        sheetHidden = false
        state = UpdateState.Checking
        scope.launch {
            state = try {
                val latest = fetchLatest()
                if (latest.build > installedBuild(context)) UpdateState.Available(latest) else UpdateState.UpToDate
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Failed(reason(e))
            }
        }
    }

    private fun reason(e: Exception): String = when {
        e is IOException && e.message == "HTTP 404" -> tr(R.string.u_err_404)
        e is IOException && e.message == "HTTP 403" -> tr(R.string.u_err_403)
        else -> tr(R.string.u_err_net)
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Wetter-App")
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $status")
        }
        return connection
    }

    private fun fetchLatest(): AppRelease {
        val connection = open(apiUrl)
        val json = try {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        val build = Regex("(\\d+)$").find(json.getString("tag_name"))?.value?.toInt()
            ?: throw IOException("Unbekannte Version")
        val assets = json.getJSONArray("assets")
        val apk = List(assets.length()) { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?: throw IOException("Keine APK")
        val url = apk.getString("browser_download_url")
        if (!url.startsWith(allowedDownload)) throw IOException("Unerwartete Quelle")
        val notes = notesFor(json.optString("body"))
        return AppRelease(build, json.optString("name", "Build $build"), notes, url, apk.optLong("size", -1L))
    }

    /** Der Release-Text hat je einen Abschnitt Deutsch und English. */
    private fun notesFor(body: String): List<String> {
        val heading = if (Locale.getDefault().language == "de") "### Deutsch" else "### English"
        val section = if (heading in body) body.substringAfter(heading).substringBefore("### ") else body
        return section.lineSequence().filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }.toList()
    }

    /**
     * Oeffnet die APK im Browser. Die App installiert bewusst nicht selbst: Die dafuer noetige
     * Berechtigung laesst Play Protect die App als schaedlich einstufen.
     */
    fun download(context: Context, release: AppRelease) {
        sheetHidden = true
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(release.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(open)
    }
}
