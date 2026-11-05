package de.tonyapps.wetter.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val build: Int, val title: String, val notes: List<String>, val url: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Installing(val release: AppRelease) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Sucht auf GitHub nach neuen Versionen und installiert sie ueber den PackageInstaller.
 * Android prueft dabei selbst die Signatur: Fremd signierte APKs lehnt es als Update ab.
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
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
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
        e is IOException && e.message == "HTTP 404" -> "Keine Version gefunden. Ist das Repo öffentlich?"
        e is IOException && e.message == "HTTP 403" -> "GitHub lässt gerade keine Abfrage zu. Versuche es später."
        else -> "Keine Verbindung zu GitHub."
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
        val notes = json.optString("body").lineSequence()
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .toList()
        return AppRelease(build, json.optString("name", "Build $build"), notes, url, apk.optLong("size", -1L))
    }

    fun install(context: Context, release: AppRelease) {
        if (state is UpdateState.Downloading) return
        sheetHidden = false
        state = UpdateState.Downloading(release, 0f)
        val app = context.applicationContext
        scope.launch {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(app.packageName)
            if (release.size > 0) params.setSize(release.size)
            var sessionId = -1
            try {
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val connection = open(release.url)
                    try {
                        val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
                        session.openWrite("wetter.apk", 0, total).use { out ->
                            connection.inputStream.use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var done = 0L
                                var shown = 0f
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    out.write(buffer, 0, read)
                                    done += read
                                    val progress = if (total > 0) done.toFloat() / total else 0f
                                    if (progress - shown >= .01f) {
                                        shown = progress
                                        state = UpdateState.Downloading(release, progress)
                                    }
                                }
                            }
                            session.fsync(out)
                        }
                    } finally {
                        connection.disconnect()
                    }
                    state = UpdateState.Installing(release)
                    val result = Intent(app, UpdateReceiver::class.java).setAction(ACTION).setPackage(app.packageName)
                    val pending = PendingIntent.getBroadcast(
                        app, sessionId, result,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
            } catch (e: Exception) {
                if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
                state = UpdateState.Failed("Download abgebrochen. Prüfe die Verbindung.")
            }
        }
    }

    fun finished(success: Boolean, message: String?) {
        // Bei Erfolg beendet Android die App ohnehin, um sie zu ersetzen
        if (!success) state = UpdateState.Failed(message ?: "Installation abgebrochen.")
    }

    fun cancelled() {
        state = UpdateState.Idle
    }
}

/** Antwort des Systems auf die Installation. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.finished(true, null)
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.cancelled()
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            -> Updater.finished(false, "Das Update passt nicht zur installierten Version.")
            PackageInstaller.STATUS_FAILURE_BLOCKED -> Updater.finished(false, "Android hat die Installation blockiert.")
            else -> Updater.finished(false, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
