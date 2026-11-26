package zhb.company.smart_home.domain.wall

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset of one local day. */
data class SunDay(val sunrise: ZonedDateTime, val sunset: ZonedDateTime) {
    val dayLength: Duration get() = Duration.between(sunrise, sunset)
}

/**
 * Sunrise and sunset on the device, no network: the NOAA approximation used by the firmware
 * (`smart-home-mcu/lib/zhb_schedule`, `sunTimesUtc`), with the same default coordinates, so the
 * wall shows the times the yard lights switch by. Accuracy about a minute at these latitudes.
 */
object SunTimes {
    /** House location, as `kDefaultLatitude` / `kDefaultLongitude` in the firmware. */
    const val LATITUDE = 44.8125
    const val LONGITUDE = 20.4612

    /** Null on a day the sun does not rise or set (polar latitudes only). */
    fun of(date: LocalDate, zone: ZoneId, lat: Double = LATITUDE, lon: Double = LONGITUDE): SunDay? {
        val (riseUtc, setUtc) = utcMinutes(date, lat, lon) ?: return null
        val midnightUtc = date.atStartOfDay(ZoneId.of("UTC"))
        return SunDay(
            sunrise = midnightUtc.plusMinutes(riseUtc).withZoneSameInstant(zone),
            sunset = midnightUtc.plusMinutes(setUtc).withZoneSameInstant(zone),
        )
    }

    /** Sunrise and sunset in UTC minutes after midnight, as the firmware computes them. */
    fun utcMinutes(date: LocalDate, lat: Double, lon: Double): Pair<Long, Long>? {
        val g = 2.0 * Math.PI / 365.0 * (date.dayOfYear - 1) // fractional year at noon
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latRad = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)
        if (cosHa < -1.0 || cosHa > 1.0) return null
        val ha = Math.toDegrees(acos(cosHa))
        return (720.0 - 4.0 * (lon + ha) - eqTime).roundToLong() to (720.0 - 4.0 * (lon - ha) - eqTime).roundToLong()
    }
}
