package zhb.company.smart_home.ui.wall

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import zhb.company.smart_home.domain.wall.HourForecast
import zhb.company.smart_home.domain.wall.PumpRun
import zhb.company.smart_home.domain.wall.WeatherKind

/** The wall's own palette (from the approved prototype): calm panels, color only where it means something. */
@Immutable
data class WallColors(
    val bg: Color,
    val panel: Color,
    val line: Color,
    val fg: Color,
    val muted: Color,
    /** Sun, solar. */
    val accent: Color,
    /** Cold, water, rain. */
    val cool: Color,
    /** Heating. */
    val warm: Color,
    val ok: Color,
) {
    companion object {
        val Dark = WallColors(
            bg = Color(0xFF0F1418), panel = Color(0xFF171E23), line = Color(0xFF26313A), fg = Color(0xFFE8EEF2),
            muted = Color(0xFF8B9AA6), accent = Color(0xFFF2B84B), cool = Color(0xFF6FB7E0), warm = Color(0xFFEF8A5C), ok = Color(0xFF69C08B),
        )
        val Light = WallColors(
            bg = Color(0xFFEEF2F4), panel = Color(0xFFFFFFFF), line = Color(0xFFD8E0E5), fg = Color(0xFF172026),
            muted = Color(0xFF5D6B76), accent = Color(0xFFC98A12), cool = Color(0xFF2B7FB0), warm = Color(0xFFC8582A), ok = Color(0xFF2F8F57),
        )
    }
}

val LocalWallColors = staticCompositionLocalOf { WallColors.Dark }

/** Roboto Condensed, light and regular: the big numbers (system fonts, nothing to download). */
val CondensedLight = FontFamily(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL))
val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

/** Simple weather icons drawn in the wall's colors. */
@Composable
fun WeatherIcon(kind: WeatherKind, modifier: Modifier) {
    val c = LocalWallColors.current
    Canvas(modifier) { drawWeather(kind, c) }
}

private fun DrawScope.drawWeather(kind: WeatherKind, c: WallColors) {
    val s = size.minDimension
    val u = s / 48f
    fun cloud(dy: Float, color: Color) {
        val p = Path().apply {
            moveTo(13 * u, (36 + dy) * u)
            lineTo(35 * u, (36 + dy) * u)
            cubicTo(46 * u, (36 + dy) * u, 46 * u, (20 + dy) * u, 35 * u, (20 + dy) * u)
            cubicTo(32 * u, (10 + dy) * u, 16 * u, (10 + dy) * u, 14 * u, (23 + dy) * u)
            cubicTo(5 * u, (23 + dy) * u, 5 * u, (36 + dy) * u, 13 * u, (36 + dy) * u)
            close()
        }
        drawPath(p, color)
    }
    fun drops(color: Color, flakes: Boolean = false) {
        listOf(17f, 25f, 33f).forEach { x ->
            if (flakes) drawCircle(color, 2.2f * u, Offset(x * u, 39 * u))
            else drawLine(color, Offset(x * u, 35 * u), Offset((x - 2) * u, 41 * u), 3 * u, StrokeCap.Round)
        }
    }
    when (kind) {
        WeatherKind.CLEAR -> {
            drawCircle(c.accent, 9 * u, Offset(24 * u, 24 * u))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val dx = kotlin.math.cos(a).toFloat()
                val dy = kotlin.math.sin(a).toFloat()
                drawLine(c.accent, Offset((24 + dx * 14) * u, (24 + dy * 14) * u), Offset((24 + dx * 19) * u, (24 + dy * 19) * u), 3 * u, StrokeCap.Round)
            }
        }
        WeatherKind.PARTLY -> {
            drawCircle(c.accent, 8 * u, Offset(18 * u, 18 * u))
            cloud(2f, c.muted)
        }
        WeatherKind.CLOUDY -> cloud(0f, c.muted)
        WeatherKind.FOG -> listOf(18f to 9f, 25f to 6f, 32f to 11f).forEach { (y, x) ->
            drawLine(c.muted, Offset(x * u, y * u), Offset((48 - x) * u, y * u), 3.5f * u, StrokeCap.Round)
        }
        WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.SHOWERS -> {
            if (kind == WeatherKind.SHOWERS) drawCircle(c.accent, 7 * u, Offset(16 * u, 12 * u))
            cloud(-6f, c.muted)
            drops(c.cool)
        }
        WeatherKind.SNOW -> {
            cloud(-6f, c.muted)
            drops(c.fg, flakes = true)
        }
        WeatherKind.THUNDER -> {
            cloud(-6f, c.muted)
            val bolt = Path().apply {
                moveTo(25 * u, 31 * u); lineTo(19 * u, 40 * u); lineTo(24 * u, 40 * u); lineTo(21 * u, 47 * u); lineTo(30 * u, 36 * u); lineTo(25 * u, 36 * u); close()
            }
            drawPath(bolt, c.accent)
        }
    }
}

/**
 * The next hours: temperature line with labels, rain bars (mm) under it, hours on the axis,
 * the first one labelled [nowLabel]. Drawn once per forecast (all work in the draw cache).
 */
@Composable
fun HourlyChart(hours: List<HourForecast>, nowLabel: String, modifier: Modifier) {
    val c = LocalWallColors.current
    val measurer = rememberTextMeasurer()
    val valueStyle = remember(c) { TextStyle(color = c.fg, fontSize = 13.sp, fontFamily = Condensed) }
    val axisStyle = remember(c) { TextStyle(color = c.muted, fontSize = 11.sp) }
    Spacer(
        modifier.drawWithCache {
            val n = hours.size
            val padX = 18.dp2px(density)
            val axisH = 16.dp2px(density)
            val top = 20.dp2px(density)
            val bottom = size.height - axisH - 22.dp2px(density)
            val lo = (hours.minOfOrNull { it.temperature } ?: 0.0) - 1
            val hi = (hours.maxOfOrNull { it.temperature } ?: 1.0) + 1
            fun x(i: Int) = if (n <= 1) size.width / 2 else padX + i * (size.width - 2 * padX) / (n - 1)
            fun y(t: Double) = (bottom - (t - lo) / (hi - lo) * (bottom - top)).toFloat()
            val line = Path().apply { hours.forEachIndexed { i, h -> if (i == 0) moveTo(x(i), y(h.temperature)) else lineTo(x(i), y(h.temperature)) } }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(n - 1), bottom)
                lineTo(x(0), bottom)
                close()
            }
            val values = hours.map { measurer.measure("${it.temperature.roundToInt()}°", valueStyle) }
            val labels = hours.mapIndexed { i, h ->
                val text = if (i == 0) nowLabel else java.time.Instant.ofEpochMilli(h.atMs).atZone(java.time.ZoneId.systemDefault()).hour.toString()
                measurer.measure(text, axisStyle)
            }
            val barW = 12.dp2px(density)
            val rainMax = 22.dp2px(density)
            onDrawBehind {
                hours.forEachIndexed { i, h ->
                    if (h.precipitationMm >= 0.1) {
                        val bh = (h.precipitationMm * 10 * density).toFloat().coerceIn(3f, rainMax)
                        drawRoundRect(c.cool.copy(alpha = 0.5f), Offset(x(i) - barW / 2, size.height - axisH - 2 - bh), Size(barW, bh), CornerRadius(3f, 3f))
                    }
                }
                drawPath(fill, c.accent.copy(alpha = 0.10f))
                drawPath(line, c.accent, style = Stroke(2.5f * density, join = StrokeJoin.Round))
                hours.forEachIndexed { i, h ->
                    drawCircle(c.accent, (if (i == 0) 4f else 2.5f) * density, Offset(x(i), y(h.temperature)))
                    val v = values[i]
                    drawText(v, topLeft = Offset(x(i) - v.size.width / 2, y(h.temperature) - v.size.height - 4))
                    val l = labels[i]
                    drawText(l, topLeft = Offset(x(i) - l.size.width / 2, size.height - l.size.height))
                }
            }
        },
    )
}

/** Today's heat pump runs on a 0-24 h strip; the part after now is faded, now is a thin mark. */
@Composable
fun RunStrip(runs: List<PumpRun>, dayStartMs: Long, nowMs: Long, modifier: Modifier) {
    val c = LocalWallColors.current
    Spacer(
        modifier.drawWithCache {
            val dayMs = 24 * 3_600_000f
            fun x(ms: Long) = ((ms - dayStartMs) / dayMs).coerceIn(0f, 1f) * size.width
            val radius = CornerRadius(5 * density, 5 * density)
            onDrawBehind {
                drawRoundRect(c.line, cornerRadius = radius)
                runs.forEach { r ->
                    val x0 = x(r.startMs)
                    val w = (x(r.endMs) - x0).coerceAtLeast(1.5f * density)
                    drawRect(c.warm, Offset(x0, 0f), Size(w, size.height))
                }
                val nowX = x(nowMs)
                drawRect(c.panel.copy(alpha = 0.55f), Offset(nowX, 0f), Size(size.width - nowX, size.height))
                drawRect(c.fg, Offset(nowX - density, -2 * density), Size(2 * density, size.height + 4 * density))
            }
        },
    )
}

private fun Int.dp2px(density: Float) = this * density
