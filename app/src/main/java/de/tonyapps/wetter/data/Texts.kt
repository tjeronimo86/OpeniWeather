package de.tonyapps.wetter.data

import android.content.Context
import de.tonyapps.wetter.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val H = DateTimeFormatter.ofPattern("HH", Locale.ROOT)

/** Texte fuer Stellen ohne Compose-Kontext, etwa Widgets; folgt der Sprache des Geraets. */
object AppContext {
    lateinit var app: Context
}

fun tr(id: Int, vararg args: Any): String = AppContext.app.getString(id, *args)
fun trPlural(id: Int, count: Int): String = AppContext.app.resources.getQuantityString(id, count, count)

fun clock(epoch: Long, zone: ZoneId): String = HM.format(Instant.ofEpochSecond(epoch).atZone(zone))
fun hourLabel(epoch: Long, zone: ZoneId): String = tr(R.string.hour_label, H.format(Instant.ofEpochSecond(epoch).atZone(zone)))

fun weekdayShort(d: Day): String = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
    .trimEnd('.').take(3).replaceFirstChar { it.titlecase(Locale.getDefault()) }
fun weekdayLong(d: Day): String = d.date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
    .replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** WMO-Wettercodes, so wie Open-Meteo sie liefert. */
fun conditionText(code: Int, isDay: Boolean, cloud: Double = Double.NaN): String = when (code) {
    0 -> tr(if (isDay) R.string.c_sunny else R.string.c_clear)
    1 -> tr(if (isDay) R.string.c_mostly_sunny else R.string.c_mostly_clear)
    2 -> tr(R.string.c_partly)
    3 -> tr(if (!cloud.isNaN() && cloud < 92) R.string.c_mostly_cloudy else R.string.c_overcast)
    45, 48 -> tr(R.string.c_fog)
    51 -> tr(R.string.c_drizzle_light)
    53 -> tr(R.string.c_drizzle)
    55 -> tr(R.string.c_drizzle_heavy)
    56, 57 -> tr(R.string.c_drizzle_freezing)
    61 -> tr(R.string.c_rain_light)
    63 -> tr(R.string.c_rain)
    65 -> tr(R.string.c_rain_heavy)
    66, 67 -> tr(R.string.c_rain_freezing)
    71 -> tr(R.string.c_snow_light)
    73 -> tr(R.string.c_snow)
    75 -> tr(R.string.c_snow_heavy)
    77 -> tr(R.string.c_snow_grains)
    80 -> tr(R.string.c_showers_light)
    81 -> tr(R.string.c_showers)
    82 -> tr(R.string.c_showers_heavy)
    85 -> tr(R.string.c_snow_showers)
    86 -> tr(R.string.c_snow_showers_heavy)
    95 -> tr(R.string.c_thunder)
    96, 99 -> tr(R.string.c_thunder_hail)
    else -> "–"
}

enum class Kind { CLEAR, MOSTLY_CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, HEAVY_RAIN, SLEET, SNOW, THUNDER }

fun kindOf(code: Int): Kind = when (code) {
    0 -> Kind.CLEAR
    1 -> Kind.MOSTLY_CLEAR
    2 -> Kind.PARTLY
    3 -> Kind.CLOUDY
    45, 48 -> Kind.FOG
    51, 53, 55 -> Kind.DRIZZLE
    61, 63, 80, 81 -> Kind.RAIN
    65, 82 -> Kind.HEAVY_RAIN
    56, 57, 66, 67 -> Kind.SLEET
    71, 73, 75, 77, 85, 86 -> Kind.SNOW
    95, 96, 99 -> Kind.THUNDER
    else -> Kind.CLOUDY
}

private fun isWet(k: Kind) = k in setOf(Kind.DRIZZLE, Kind.RAIN, Kind.HEAVY_RAIN, Kind.SLEET)

/**
 * Der Kurztext ueber der Stundenleiste, z. B. "Gewitter erwartet gegen 21:00."
 * Reihenfolge: Gewitter > laufender Niederschlag > kommender Niederschlag > Allgemeines.
 */
fun summary(w: Weather): String {
    val next = w.hoursFromNow.take(13)
    if (next.isEmpty()) return conditionText(w.current.code, w.current.isDay)
    val gust = next.take(12).map { it.gust }.filter { !it.isNaN() }.maxOrNull()?.roundToInt()
    val gustText = if (gust != null && gust >= 12) " " + tr(R.string.sum_gusts, gust) else ""
    val nowKind = kindOf(w.current.code)

    if (nowKind != Kind.THUNDER) {
        next.firstOrNull { kindOf(it.code) == Kind.THUNDER }?.let {
            return tr(R.string.sum_thunder_at, clock(it.time, w.zone)) + gustText
        }
    }
    if (nowKind == Kind.THUNDER) return tr(R.string.sum_thunder_now) + gustText
    if (isWet(nowKind) || nowKind == Kind.SNOW) {
        val snow = nowKind == Kind.SNOW
        val dry = next.firstOrNull { val k = kindOf(it.code); !isWet(k) && k != Kind.SNOW }
        return if (dry != null) {
            tr(if (snow) R.string.sum_snow_ends else R.string.sum_rain_ends, clock(dry.time, w.zone)) + gustText
        } else {
            tr(if (snow) R.string.sum_snow_goes_on else R.string.sum_rain_goes_on) + gustText
        }
    }
    next.firstOrNull { val k = kindOf(it.code); isWet(k) || k == Kind.SNOW }?.let {
        val snow = kindOf(it.code) == Kind.SNOW
        return tr(if (snow) R.string.sum_snow_at else R.string.sum_rain_at, clock(it.time, w.zone)) + gustText
    }
    val cloud = next.take(10).map { it.cloud }.filter { !it.isNaN() }.average()
    val base = when {
        nowKind == Kind.FOG -> tr(R.string.sum_fog)
        cloud > 70 -> tr(R.string.sum_clouds)
        cloud < 25 && w.current.isDay -> tr(R.string.sum_sun)
        cloud < 25 -> tr(R.string.sum_clear_night)
        else -> tr(R.string.sum_mixed)
    }
    return base + gustText
}

fun uvLabel(uv: Double): String = when {
    uv.isNaN() -> "–"
    uv < 3 -> tr(R.string.uv_low)
    uv < 6 -> tr(R.string.uv_moderate)
    uv < 8 -> tr(R.string.uv_high)
    uv < 11 -> tr(R.string.uv_very_high)
    else -> tr(R.string.uv_extreme)
}

fun uvAdvice(w: Weather): String {
    val day = w.today
    val high = w.hoursOf(day.date).filter { it.uv >= 3 }
    val until = high.lastOrNull()?.takeIf { it.time + 3600 > w.current.time }
    return if (until != null) {
        tr(R.string.uv_protect_until, clock(until.time + 3600, w.zone))
    } else {
        tr(R.string.uv_low_rest)
    }
}

fun aqiLabel(aqi: Double): String = when {
    aqi.isNaN() -> "–"
    aqi <= 20 -> tr(R.string.aqi_very_good)
    aqi <= 40 -> tr(R.string.aqi_good)
    aqi <= 60 -> tr(R.string.aqi_moderate)
    aqi <= 80 -> tr(R.string.aqi_poor)
    aqi <= 100 -> tr(R.string.aqi_very_poor)
    else -> tr(R.string.aqi_extreme)
}

fun windName(deg: Double): String {
    if (deg.isNaN()) return "–"
    val names = listOf(R.string.dir_n, R.string.dir_ne, R.string.dir_e, R.string.dir_se, R.string.dir_s, R.string.dir_sw, R.string.dir_w, R.string.dir_nw)
    return tr(names[((deg / 45.0).roundToInt() + 8) % 8])
}

data class MoonInfo(val phase: Double, val illumination: Double, val name: String, val daysToFull: Int)

/** Mondphase aus der mittleren Lunation; fuer die Anzeige genau genug (±0,5 Tage). */
fun moonInfo(epoch: Long): MoonInfo {
    val synodic = 29.530588853
    val julian = epoch / 86400.0 + 2440587.5
    val cycles = (julian - 2451550.1) / synodic
    val phase = cycles - floor(cycles)
    val illumination = (1 - cos(2 * PI * phase)) / 2
    val name = when {
        phase < 0.03 || phase >= 0.97 -> tr(R.string.m_new)
        phase < 0.22 -> tr(R.string.m_waxing_crescent)
        phase < 0.28 -> tr(R.string.m_first_quarter)
        phase < 0.47 -> tr(R.string.m_waxing_gibbous)
        phase < 0.53 -> tr(R.string.m_full)
        phase < 0.72 -> tr(R.string.m_waning_gibbous)
        phase < 0.78 -> tr(R.string.m_last_quarter)
        else -> tr(R.string.m_waning_crescent)
    }
    val toFull = ((0.5 - phase + 1) % 1) * synodic
    return MoonInfo(phase, illumination, name, toFull.roundToInt())
}
