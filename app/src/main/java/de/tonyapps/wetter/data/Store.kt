package de.tonyapps.wetter.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val CURRENT_ID = "current"

/** Orte und Cache liegen in SharedPreferences; Android-Backup nimmt sie aufs neue Handy mit. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("wetter", Context.MODE_PRIVATE)

    fun places(): List<Place> {
        val json = prefs.getString("places", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(json)
            List(a.length()) {
                val o = a.getJSONObject(it)
                Place(
                    o.getString("id"), o.getString("name"), o.optString("region"), o.optString("cc"),
                    o.getDouble("lat"), o.getDouble("lon"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePlaces(list: List<Place>) {
        val a = JSONArray()
        list.filter { !it.isCurrent }.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("region", it.region)
                    .put("cc", it.countryCode).put("lat", it.lat).put("lon", it.lon),
            )
        }
        prefs.edit().putString("places", a.toString()).apply()
    }

    fun cached(id: String): RawWeather? {
        val forecast = prefs.getString("f-$id", null) ?: return null
        return RawWeather(forecast, prefs.getString("a-$id", null), prefs.getString("w-$id", null), prefs.getLong("t-$id", 0))
    }

    fun cache(id: String, raw: RawWeather) {
        prefs.edit().putString("f-$id", raw.forecast).putString("a-$id", raw.air)
            .putString("w-$id", raw.alerts).putLong("t-$id", raw.fetchedAt).apply()
    }

    fun drop(id: String) {
        prefs.edit().remove("f-$id").remove("a-$id").remove("w-$id").remove("t-$id").apply()
    }

    fun lastCurrent(): Place? {
        val json = prefs.getString("current-place", null) ?: return null
        return runCatching {
            val o = JSONObject(json)
            Place(CURRENT_ID, o.getString("name"), "Mein Standort", o.optString("cc"), o.getDouble("lat"), o.getDouble("lon"), true)
        }.getOrNull()
    }

    fun saveCurrent(p: Place) {
        prefs.edit().putString(
            "current-place",
            JSONObject().put("name", p.name).put("cc", p.countryCode).put("lat", p.lat).put("lon", p.lon).toString(),
        ).apply()
    }

    /** Alle Orte in Anzeigereihenfolge, wie sie auch die App zeigt. */
    fun allPlaces(): List<Place> {
        val list = listOfNotNull(lastCurrent()) + places()
        return list.ifEmpty { listOf(WeatherViewModel.DEFAULT_PLACE) }
    }

    fun widgetPlaceId(widgetId: Int): String? = prefs.getString("widget-$widgetId", null)

    fun setWidgetPlace(widgetId: Int, placeId: String) {
        prefs.edit().putString("widget-$widgetId", placeId).apply()
    }

    fun dropWidget(widgetId: Int) {
        prefs.edit().remove("widget-$widgetId").apply()
    }
}
