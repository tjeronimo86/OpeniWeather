package de.tonyapps.wetter.data

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.Manifest
import de.tonyapps.wetter.widget.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private const val MAX_AGE_MS = 15 * 60 * 1000L

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    val places = mutableStateListOf<Place>()
    val weather = mutableStateMapOf<String, Weather>()
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    /** Index der Szene aus dem Vorschau-Modus; -1 heisst: echtes Wetter. */
    var demoScene by mutableIntStateOf(-1)

    init {
        val saved = store.places()
        val current = store.lastCurrent()
        if (current != null) places += current
        places += saved
        if (places.isEmpty()) places += DEFAULT_PLACE
        places.forEach { p ->
            store.cached(p.id)?.let { raw ->
                runCatching { Parser.parse(raw) }.onSuccess { weather[p.id] = it }
            }
        }
        refreshAll()
    }

    fun refreshAll(force: Boolean = false) {
        viewModelScope.launch {
            loading = true
            var failed = false
            places.toList().forEach { p ->
                val age = System.currentTimeMillis() - (weather[p.id]?.fetchedAt ?: 0L)
                if (force || age > MAX_AGE_MS) {
                    if (!load(p)) failed = true
                }
            }
            error = if (failed) "Keine Verbindung. Letzte Daten werden angezeigt." else null
            loading = false
            Widgets.updateAll(getApplication())
        }
    }

    private suspend fun load(place: Place): Boolean = runCatching {
        val raw = Api.fetch(place)
        val parsed = withContext(Dispatchers.Default) { Parser.parse(raw) }
        store.cache(place.id, raw)
        weather[place.id] = parsed
    }.isSuccess

    /** Holt Wetter fuer einen Suchtreffer, ohne ihn schon zur Liste hinzuzufuegen. */
    fun preview(place: Place) {
        if (weather.containsKey(place.id)) return
        viewModelScope.launch { load(place) }
    }

    fun add(place: Place) {
        if (places.none { it.id == place.id }) {
            places += place
            store.savePlaces(places)
            if (!weather.containsKey(place.id)) viewModelScope.launch { load(place) }
        }
    }

    fun remove(place: Place) {
        if (place.isCurrent) return
        places.remove(place)
        weather.remove(place.id)
        store.drop(place.id)
        store.savePlaces(places)
        if (places.isEmpty()) places += DEFAULT_PLACE
    }

    /** Wird nach erteilter Standort-Berechtigung und bei jedem Start aufgerufen. */
    fun locate() {
        val ctx = getApplication<Application>()
        if (!hasLocationPermission(ctx)) return
        viewModelScope.launch {
            val loc = currentLocation(ctx) ?: return@launch
            val (name, cc) = withContext(Dispatchers.IO) { reverse(ctx, loc) }
            val place = Place(CURRENT_ID, name, "Mein Standort", cc, loc.latitude, loc.longitude, true)
            store.saveCurrent(place)
            val index = places.indexOfFirst { it.isCurrent }
            if (index >= 0) places[index] = place else places.add(0, place)
            places.remove(DEFAULT_PLACE)
            load(place)
            Widgets.updateAll(ctx)
        }
    }

    private fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        val fresh = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                lm.getCurrentLocation(provider, null, ctx.mainExecutor) { cont.resume(it) }
            }
        }
        return fresh ?: lm.getLastKnownLocation(provider)
    }

    @Suppress("DEPRECATION")
    private fun reverse(ctx: Context, loc: Location): Pair<String, String> {
        val address = runCatching {
            Geocoder(ctx, Locale.GERMANY).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        }.getOrNull()
        val name = address?.locality ?: address?.subAdminArea ?: "Mein Standort"
        return name to (address?.countryCode ?: "DE")
    }

    companion object {
        val DEFAULT_PLACE = Place("default", "Frankfurt am Main", "Hessen, Deutschland", "DE", 50.1109, 8.6821)
    }
}
