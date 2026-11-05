package de.tonyapps.wetter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Rohantworten, damit sie unveraendert in den Cache wandern koennen. */
data class RawWeather(
    val forecast: String,
    val air: String?,
    val alerts: String?,
    val fetchedAt: Long,
)

object Api {
    private const val HOURLY = "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m," +
        "precipitation_probability,precipitation,snowfall,weather_code,pressure_msl,visibility," +
        "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,cloud_cover,is_day"
    private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
        "uv_index_max,precipitation_sum,precipitation_probability_max,wind_speed_10m_max," +
        "wind_gusts_10m_max,wind_direction_10m_dominant"
    private const val CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
        "precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"

    private fun coord(v: Double) = String.format(Locale.US, "%.4f", v)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Wetter-App/1.7 (+https://github.com/tjeronimo86/Wetter)")
        try {
            val status = c.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun fetch(place: Place): RawWeather = withContext(Dispatchers.IO) {
        coroutineScope {
            val lat = coord(place.lat)
            val lon = coord(place.lon)
            val forecast = async {
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=$CURRENT&hourly=$HOURLY&daily=$DAILY" +
                        "&timezone=auto&timeformat=unixtime&past_days=1&forecast_days=11",
                )
            }
            // Luftqualitaet und Warnungen sind Zusatz: Fehlen sie, zeigen wir den Rest trotzdem.
            val air = async {
                runCatching {
                    get(
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                            "&current=european_aqi,us_aqi,pm10,pm2_5,ozone,nitrogen_dioxide",
                    )
                }.getOrNull()
            }
            val alerts = async {
                if (place.countryCode.equals("DE", ignoreCase = true)) {
                    runCatching { get("https://api.brightsky.dev/alerts?lat=$lat&lon=$lon") }.getOrNull()
                } else {
                    null
                }
            }
            RawWeather(forecast.await(), air.await(), alerts.await(), System.currentTimeMillis())
        }
    }

    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=12&language=de&format=json"))
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        List(results.length()) { i ->
            val r = results.getJSONObject(i)
            Place(
                id = "geo-${r.getLong("id")}",
                name = r.getString("name"),
                region = listOf(r.optString("admin1"), r.optString("country"))
                    .filter { it.isNotBlank() }.joinToString(", "),
                countryCode = r.optString("country_code"),
                lat = r.getDouble("latitude"),
                lon = r.getDouble("longitude"),
            )
        }
    }
}

private fun JSONArray.num(i: Int): Double = if (i >= length() || isNull(i)) Double.NaN else optDouble(i, Double.NaN)
private fun JSONArray.time(i: Int): Long = if (i >= length() || isNull(i)) 0L else optLong(i)
private fun JSONArray.code(i: Int): Int = if (i >= length() || isNull(i)) 0 else optInt(i)
private fun JSONObject.num(k: String): Double = if (isNull(k)) Double.NaN else optDouble(k, Double.NaN)

object Parser {
    fun parse(raw: RawWeather): Weather {
        val root = JSONObject(raw.forecast)
        val offset = root.optInt("utc_offset_seconds", 0)
        val zone: ZoneId = runCatching { ZoneId.of(root.getString("timezone")) }
            .getOrElse { ZoneOffset.ofTotalSeconds(offset) }

        val c = root.getJSONObject("current")
        val current = Current(
            time = maxOf(c.getLong("time"), System.currentTimeMillis() / 1000),
            temp = c.num("temperature_2m"),
            feels = c.num("apparent_temperature"),
            humidity = c.num("relative_humidity_2m"),
            precipitation = c.num("precipitation"),
            code = c.optInt("weather_code"),
            cloud = c.num("cloud_cover"),
            pressure = c.num("pressure_msl"),
            wind = c.num("wind_speed_10m"),
            windDir = c.num("wind_direction_10m"),
            gust = c.num("wind_gusts_10m"),
            isDay = c.optInt("is_day", 1) == 1,
        )

        val h = root.getJSONObject("hourly")
        val hTime = h.getJSONArray("time")
        val hours = List(hTime.length()) { i ->
            Hour(
                time = hTime.time(i),
                temp = h.getJSONArray("temperature_2m").num(i),
                feels = h.getJSONArray("apparent_temperature").num(i),
                humidity = h.getJSONArray("relative_humidity_2m").num(i),
                dew = h.getJSONArray("dew_point_2m").num(i),
                pop = h.getJSONArray("precipitation_probability").num(i),
                precip = h.getJSONArray("precipitation").num(i),
                snow = h.getJSONArray("snowfall").num(i),
                code = h.getJSONArray("weather_code").code(i),
                pressure = h.getJSONArray("pressure_msl").num(i),
                visibility = h.getJSONArray("visibility").num(i),
                wind = h.getJSONArray("wind_speed_10m").num(i),
                windDir = h.getJSONArray("wind_direction_10m").num(i),
                gust = h.getJSONArray("wind_gusts_10m").num(i),
                uv = h.getJSONArray("uv_index").num(i),
                cloud = h.getJSONArray("cloud_cover").num(i),
                isDay = h.getJSONArray("is_day").code(i) == 1,
            )
        }

        val d = root.getJSONObject("daily")
        val dTime = d.getJSONArray("time")
        val days = List(dTime.length()) { i ->
            // Tageszeitstempel zeigen auf lokale Mitternacht. Der Sprung auf den Morgen
            // haelt das Datum stabil, wenn eine Zeitumstellung im Vorhersagezeitraum liegt.
            val date = Instant.ofEpochSecond(dTime.time(i) + 6 * 3600).atZone(zone).toLocalDate()
            Day(
                date = date,
                code = d.getJSONArray("weather_code").code(i),
                max = d.getJSONArray("temperature_2m_max").num(i),
                min = d.getJSONArray("temperature_2m_min").num(i),
                sunrise = d.getJSONArray("sunrise").time(i),
                sunset = d.getJSONArray("sunset").time(i),
                uvMax = d.getJSONArray("uv_index_max").num(i),
                precip = d.getJSONArray("precipitation_sum").num(i),
                pop = d.getJSONArray("precipitation_probability_max").num(i),
                windMax = d.getJSONArray("wind_speed_10m_max").num(i),
                gustMax = d.getJSONArray("wind_gusts_10m_max").num(i),
                windDir = d.getJSONArray("wind_direction_10m_dominant").num(i),
            )
        }.filter { !it.max.isNaN() }

        return Weather(
            zone = zone,
            current = current,
            hours = hours,
            days = days,
            air = raw.air?.let { runCatching { parseAir(it) }.getOrNull() },
            alerts = raw.alerts?.let { runCatching { parseAlerts(it) }.getOrNull() } ?: emptyList(),
            fetchedAt = raw.fetchedAt,
        )
    }

    private fun parseAir(json: String): Air {
        val c = JSONObject(json).getJSONObject("current")
        return Air(
            aqi = c.num("european_aqi"),
            usAqi = c.num("us_aqi"),
            pm10 = c.num("pm10"),
            pm25 = c.num("pm2_5"),
            ozone = c.num("ozone"),
            no2 = c.num("nitrogen_dioxide"),
        )
    }

    private fun parseAlerts(json: String): List<Alert> {
        val list = JSONObject(json).optJSONArray("alerts") ?: return emptyList()
        val now = System.currentTimeMillis() / 1000
        fun epoch(v: String): Long = runCatching { OffsetDateTime.parse(v).toEpochSecond() }.getOrDefault(0L)
        return List(list.length()) { list.getJSONObject(it) }
            .map {
                Alert(
                    event = it.optString("event_de"),
                    headline = it.optString("headline_de"),
                    description = it.optString("description_de"),
                    instruction = it.optString("instruction_de").takeUnless { s -> s == "null" }.orEmpty(),
                    severity = it.optString("severity"),
                    onset = epoch(it.optString("onset")),
                    expires = epoch(it.optString("expires")),
                )
            }
            .filter { it.expires == 0L || it.expires > now }
            .sortedByDescending { severityRank(it.severity) }
    }

    fun severityRank(s: String): Int = when (s.lowercase()) {
        "extreme" -> 3
        "severe" -> 2
        "moderate" -> 1
        else -> 0
    }
}
