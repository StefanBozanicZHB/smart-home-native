package zhb.company.smart_home.domain.wall

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import zhb.company.smart_home.domain.node.parseJsonObject

/** Weather in a few kinds the wall can draw an icon for (from the WMO code). */
enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER }

data class HourForecast(val atMs: Long, val temperature: Double, val precipitationMm: Double, val kind: WeatherKind)

data class DayForecast(val date: LocalDate, val kind: WeatherKind, val max: Double, val min: Double, val precipitationMm: Double)

/** One Open-Meteo answer: now, the next hours and the coming days. */
data class Forecast(
    val fetchedMs: Long,
    val currentTemperature: Double?,
    val currentKind: WeatherKind?,
    val windKmh: Double?,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
) {
    /** The [count] hours starting with the one that contains [nowMs]. */
    fun nextHours(nowMs: Long, count: Int = 12): List<HourForecast> =
        hours.dropWhile { it.atMs + HOUR_MS <= nowMs }.take(count)

    /** Today and the following days, [count] in all. */
    fun daysFrom(today: LocalDate, count: Int = 5): List<DayForecast> = days.dropWhile { it.date < today }.take(count)

    companion object {
        const val HOUR_MS = 3_600_000L
    }
}

/**
 * Open-Meteo forecast API (`api.open-meteo.com/v1/forecast`, no key; free for non-commercial use,
 * data under CC BY 4.0, so the wall names the source). Requested with `timeformat=unixtime`.
 */
object OpenMeteo {
    /** Two decimals (about 1 km) are enough for a forecast and do not pinpoint the house. */
    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(java.util.Locale.US, lat, lon) +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&hourly=temperature_2m,precipitation,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum" +
            "&timezone=auto&timeformat=unixtime&forecast_days=6"

    fun parse(text: String, fetchedMs: Long, zone: ZoneId = ZoneId.systemDefault()): Forecast? {
        val o = parseJsonObject(text) ?: return null
        val current = o["current"] as? JsonObject
        val hourly = o["hourly"] as? JsonObject ?: return null
        val daily = o["daily"] as? JsonObject ?: return null

        val hTimes = hourly.longs("time")
        val hTemp = hourly.doubles("temperature_2m")
        val hRain = hourly.doubles("precipitation")
        val hCode = hourly.ints("weather_code")
        val hours = hTimes.indices.mapNotNull { i ->
            val t = hTimes[i] ?: return@mapNotNull null
            val temp = hTemp.getOrNull(i) ?: return@mapNotNull null
            HourForecast(t * 1000, temp, hRain.getOrNull(i) ?: 0.0, kind(hCode.getOrNull(i)))
        }

        val dTimes = daily.longs("time")
        val dMax = daily.doubles("temperature_2m_max")
        val dMin = daily.doubles("temperature_2m_min")
        val dRain = daily.doubles("precipitation_sum")
        val dCode = daily.ints("weather_code")
        val days = dTimes.indices.mapNotNull { i ->
            val t = dTimes[i] ?: return@mapNotNull null
            DayForecast(
                // Local midnight of that day (`timezone=auto`); noon keeps it on the right date.
                date = Instant.ofEpochSecond(t + 12 * 3600).atZone(zone).toLocalDate(),
                kind = kind(dCode.getOrNull(i)),
                max = dMax.getOrNull(i) ?: return@mapNotNull null,
                min = dMin.getOrNull(i) ?: return@mapNotNull null,
                precipitationMm = dRain.getOrNull(i) ?: 0.0,
            )
        }
        if (hours.isEmpty() || days.isEmpty()) return null
        return Forecast(
            fetchedMs = fetchedMs,
            currentTemperature = (current?.get("temperature_2m") as? JsonPrimitive)?.doubleOrNull,
            currentKind = (current?.get("weather_code") as? JsonPrimitive)?.intOrNull?.let(::kind),
            windKmh = (current?.get("wind_speed_10m") as? JsonPrimitive)?.doubleOrNull,
            hours = hours,
            days = days,
        )
    }

    /** WMO weather interpretation codes, as listed in the Open-Meteo documentation. */
    fun kind(code: Int?): WeatherKind = when (code) {
        0 -> WeatherKind.CLEAR
        1, 2 -> WeatherKind.PARTLY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67 -> WeatherKind.RAIN
        80, 81, 82 -> WeatherKind.SHOWERS
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.THUNDER
        else -> WeatherKind.CLOUDY // 3 and anything unknown
    }

    private fun JsonObject.array(key: String): List<JsonPrimitive?> = (this[key] as? JsonArray).orEmpty().map { it as? JsonPrimitive }
    private fun JsonObject.longs(key: String) = array(key).map { it?.longOrNull }
    private fun JsonObject.doubles(key: String) = array(key).map { it?.doubleOrNull }
    private fun JsonObject.ints(key: String) = array(key).map { it?.intOrNull }
}
