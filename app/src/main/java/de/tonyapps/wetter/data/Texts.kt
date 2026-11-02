package de.tonyapps.wetter.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

private val HM = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
private val H = DateTimeFormatter.ofPattern("HH", Locale.GERMAN)

fun clock(epoch: Long, zone: ZoneId): String = HM.format(Instant.ofEpochSecond(epoch).atZone(zone))
fun hourLabel(epoch: Long, zone: ZoneId): String = H.format(Instant.ofEpochSecond(epoch).atZone(zone)) + " Uhr"

fun weekdayShort(d: Day): String = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.GERMAN).take(2)
fun weekdayLong(d: Day): String = d.date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)

/** WMO-Wettercodes, so wie Open-Meteo sie liefert. */
fun conditionText(code: Int, isDay: Boolean, cloud: Double = Double.NaN): String = when (code) {
    0 -> if (isDay) "Sonnig" else "Klar"
    1 -> if (isDay) "Meist sonnig" else "Meist klar"
    2 -> "Teils bewölkt"
    3 -> if (!cloud.isNaN() && cloud < 92) "Meist bewölkt" else "Bedeckt"
    45, 48 -> "Nebel"
    51 -> "Leichter Nieselregen"
    53 -> "Nieselregen"
    55 -> "Starker Nieselregen"
    56, 57 -> "Gefrierender Nieselregen"
    61 -> "Leichter Regen"
    63 -> "Regen"
    65 -> "Starker Regen"
    66, 67 -> "Gefrierender Regen"
    71 -> "Leichter Schneefall"
    73 -> "Schneefall"
    75 -> "Starker Schneefall"
    77 -> "Schneegriesel"
    80 -> "Leichte Schauer"
    81 -> "Regenschauer"
    82 -> "Heftige Schauer"
    85 -> "Schneeschauer"
    86 -> "Starke Schneeschauer"
    95 -> "Gewitter"
    96, 99 -> "Gewitter mit Hagel"
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
    val gustText = if (gust != null && gust >= 12) " Windböen mit bis zu $gust km/h." else ""
    val nowKind = kindOf(w.current.code)

    if (nowKind != Kind.THUNDER) {
        next.firstOrNull { kindOf(it.code) == Kind.THUNDER }?.let {
            return "Gewitter erwartet gegen ${clock(it.time, w.zone)}.$gustText"
        }
    }
    if (nowKind == Kind.THUNDER) return "Gewitter in der Umgebung.$gustText"
    if (isWet(nowKind) || nowKind == Kind.SNOW) {
        val word = if (nowKind == Kind.SNOW) "Schneefall" else "Regen"
        val dry = next.firstOrNull { val k = kindOf(it.code); !isWet(k) && k != Kind.SNOW }
        return if (dry != null) {
            "$word endet voraussichtlich gegen ${clock(dry.time, w.zone)}.$gustText"
        } else {
            "$word hält weiter an.$gustText"
        }
    }
    next.firstOrNull { val k = kindOf(it.code); isWet(k) || k == Kind.SNOW }?.let {
        val word = if (kindOf(it.code) == Kind.SNOW) "Schneefall" else "Regen"
        return "$word erwartet gegen ${clock(it.time, w.zone)}.$gustText"
    }
    val cloud = next.take(10).map { it.cloud }.filter { !it.isNaN() }.average()
    val base = when {
        nowKind == Kind.FOG -> "Nebel in den nächsten Stunden."
        cloud > 70 -> "Wolken für den Rest des Tages."
        cloud < 25 && w.current.isDay -> "Sonne den ganzen Tag."
        cloud < 25 -> "Klar für den Rest der Nacht."
        else -> "Wechselnd bewölkt in den nächsten Stunden."
    }
    return base + gustText
}

fun uvLabel(uv: Double): String = when {
    uv.isNaN() -> "–"
    uv < 3 -> "Niedrig"
    uv < 6 -> "Mittel"
    uv < 8 -> "Hoch"
    uv < 11 -> "Sehr hoch"
    else -> "Extrem"
}

fun uvAdvice(w: Weather): String {
    val day = w.today
    val high = w.hoursOf(day.date).filter { it.uv >= 3 }
    val until = high.lastOrNull()?.takeIf { it.time + 3600 > w.current.time }
    return if (until != null) {
        "Sonnenschutz verwenden bis ${clock(until.time + 3600, w.zone)}."
    } else {
        "Für den Rest des Tages niedrig."
    }
}

fun aqiLabel(aqi: Double): String = when {
    aqi.isNaN() -> "–"
    aqi <= 20 -> "Sehr gut"
    aqi <= 40 -> "Gut"
    aqi <= 60 -> "Mäßig"
    aqi <= 80 -> "Schlecht"
    aqi <= 100 -> "Sehr schlecht"
    else -> "Extrem schlecht"
}

fun windName(deg: Double): String {
    if (deg.isNaN()) return "–"
    val names = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
    return names[((deg / 45.0).roundToInt() + 8) % 8]
}

fun beaufort(kmh: Double): String = when {
    kmh < 2 -> "Windstille"
    kmh < 12 -> "Leiser Zug"
    kmh < 20 -> "Leichte Brise"
    kmh < 29 -> "Schwache Brise"
    kmh < 39 -> "Mäßige Brise"
    kmh < 50 -> "Frische Brise"
    kmh < 62 -> "Starker Wind"
    kmh < 75 -> "Steifer Wind"
    kmh < 89 -> "Stürmischer Wind"
    kmh < 103 -> "Sturm"
    else -> "Orkanartig"
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
        phase < 0.03 || phase >= 0.97 -> "Neumond"
        phase < 0.22 -> "Zunehmende Sichel"
        phase < 0.28 -> "Erstes Viertel"
        phase < 0.47 -> "Zunehmender Mond"
        phase < 0.53 -> "Vollmond"
        phase < 0.72 -> "Abnehmender Mond"
        phase < 0.78 -> "Letztes Viertel"
        else -> "Abnehmende Sichel"
    }
    val toFull = ((0.5 - phase + 1) % 1) * synodic
    return MoonInfo(phase, illumination, name, toFull.roundToInt())
}
