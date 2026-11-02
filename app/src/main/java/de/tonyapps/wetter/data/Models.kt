package de.tonyapps.wetter.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Ein Ort aus der Liste. `isCurrent` markiert den GPS-Standort. */
data class Place(
    val id: String,
    val name: String,
    val region: String,
    val countryCode: String,
    val lat: Double,
    val lon: Double,
    val isCurrent: Boolean = false,
)

data class Current(
    val time: Long,
    val temp: Double,
    val feels: Double,
    val humidity: Double,
    val precipitation: Double,
    val code: Int,
    val cloud: Double,
    val pressure: Double,
    val wind: Double,
    val windDir: Double,
    val gust: Double,
    val isDay: Boolean,
)

data class Hour(
    val time: Long,
    val temp: Double,
    val feels: Double,
    val humidity: Double,
    val dew: Double,
    val pop: Double,
    val precip: Double,
    val snow: Double,
    val code: Int,
    val pressure: Double,
    val visibility: Double,
    val wind: Double,
    val windDir: Double,
    val gust: Double,
    val uv: Double,
    val cloud: Double,
    val isDay: Boolean,
)

data class Day(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: Long,
    val sunset: Long,
    val uvMax: Double,
    val precip: Double,
    val pop: Double,
    val windMax: Double,
    val gustMax: Double,
    val windDir: Double,
)

data class Air(
    val aqi: Double,
    val usAqi: Double,
    val pm10: Double,
    val pm25: Double,
    val ozone: Double,
    val no2: Double,
)

data class Alert(
    val event: String,
    val headline: String,
    val description: String,
    val instruction: String,
    val severity: String,
    val onset: Long,
    val expires: Long,
)

data class Weather(
    val zone: ZoneId,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val air: Air?,
    val alerts: List<Alert>,
    val fetchedAt: Long,
) {
    /** Heutiger Tag; Open-Meteo liefert wegen past_days=1 auch gestern mit. */
    val today: Day get() = upcomingDays.first()

    val upcomingDays: List<Day>
        get() {
            val date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(current.time), zone).toLocalDate()
            return days.filter { !it.date.isBefore(date) }.ifEmpty { days }
        }

    /** Stunde, in der wir gerade sind, und alles danach. */
    val hoursFromNow: List<Hour>
        get() = hours.filter { it.time + 3600 > current.time }

    fun hoursOf(date: LocalDate): List<Hour> = hours.filter {
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(it.time), zone).toLocalDate() == date
    }

    /** Sonnenauf- und -untergang des Tages, zu dem ein Zeitpunkt gehoert. */
    fun dayOf(time: Long): Day? {
        val date = ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), zone).toLocalDate()
        return days.firstOrNull { it.date == date }
    }
}

fun Double.deg(): String = if (isNaN()) "–" else "${roundToInt()}°"
fun Double.int(): String = if (isNaN()) "–" else roundToInt().toString()
