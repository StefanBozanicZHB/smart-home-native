package zhb.company.smart_home.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import zhb.company.smart_home.ui.theme.AppTheme

/**
 * Small charts drawn on a Canvas (no chart library). Points are spaced evenly by index; a null
 * value is a gap. Charts that take [selected]/[onSelect] show a guide at the touched point and
 * let the caller print its values ([ChartReadout]).
 */

enum class ChartAxis { LEFT, RIGHT }

data class ChartLine(
    val values: List<Float?>,
    val color: Color,
    val dashed: Boolean = false,
    val axis: ChartAxis = ChartAxis.LEFT,
    /** Optional min/max band drawn behind the line. */
    val low: List<Float?>? = null,
    val high: List<Float?>? = null,
)

/** A label under the x axis at point [index] (centered on it). */
data class XTick(val index: Int, val label: String)

private val AxisLabelWidth = 30.dp
private val XLabelHeight = 16.dp

/**
 * Compact line without axes (home card): optional shading per point (0..1, e.g. the share of
 * the time the pump ran) and a dashed reference line (target temperature).
 */
@Composable
fun Sparkline(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    shade: List<Float?>? = null,
    shadeColor: Color = AppTheme.status.active,
    reference: List<Float?>? = null,
    height: Dp = 40.dp,
    description: String? = null,
) {
    val refColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier.fillMaxWidth().height(height).semantics { description?.let { contentDescription = it } },
    ) {
        val n = values.size
        if (n == 0) return@Canvas
        val w = size.width / n
        shade?.forEachIndexed { i, f ->
            if (f != null && f > 0f) {
                drawRect(shadeColor.copy(alpha = 0.35f * f.coerceAtMost(1f)), Offset(i * w, 0f), Size(w + 0.5f, size.height))
            }
        }
        val all = values.filterNotNull() + reference.orEmpty().filterNotNull()
        if (all.isEmpty()) return@Canvas
        val range = paddedRange(all.min(), all.max(), minSpan = 1f)
        val y = { v: Float -> size.height * (1f - (v - range.first) / (range.second - range.first)) }
        val x = { i: Int -> (i + 0.5f) * w }
        reference?.let { drawSeries(it, x, y, refColor.copy(alpha = 0.7f), 1.dp.toPx(), dashed = true) }
        drawSeries(values, x, y, color, 2.dp.toPx(), dashed = false)
    }
}

/** Lines with a left axis (and a right one if any line uses it), x labels and touch readout. */
@Composable
fun LineChart(
    lines: List<ChartLine>,
    xTicks: List<XTick>,
    modifier: Modifier = Modifier,
    leftFormat: (Float) -> String = { formatAxis(it) },
    rightFormat: (Float) -> String = { formatAxis(it) },
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    height: Dp = 150.dp,
    description: String? = null,
    /** Hard limits for the left axis, e.g. 0..100 for humidity. */
    leftBounds: ClosedFloatingPointRange<Float>? = null,
) {
    val n = lines.maxOfOrNull { it.values.size } ?: 0
    val hasRight = lines.any { it.axis == ChartAxis.RIGHT && it.values.any { v -> v != null } }
    ChartFrame(n, xTicks, hasRight, selected, onSelect, height, modifier, description) { plot, labels ->
        val left = lines.filter { it.axis == ChartAxis.LEFT }.flatMap { l -> l.values.filterNotNull() + l.low.orEmpty().filterNotNull() + l.high.orEmpty().filterNotNull() }
        val right = lines.filter { it.axis == ChartAxis.RIGHT }.flatMap { l -> l.values.filterNotNull() + l.low.orEmpty().filterNotNull() + l.high.orEmpty().filterNotNull() }
        val leftRange = if (left.isEmpty()) null else paddedRange(left.min(), left.max(), minSpan = 2f).let { r ->
            if (leftBounds == null) r else r.first.coerceIn(leftBounds) to r.second.coerceIn(leftBounds)
        }
        val rightRange = if (right.isEmpty()) null else paddedRange(right.min(), right.max(), minSpan = 10f)
        leftRange?.let { labels.axis(it, ChartAxis.LEFT, leftFormat) }
        if (hasRight) rightRange?.let { labels.axis(it, ChartAxis.RIGHT, rightFormat) }
        lines.forEach { line ->
            val range = (if (line.axis == ChartAxis.LEFT) leftRange else rightRange) ?: return@forEach
            val y = plot.y(range)
            if (line.low != null && line.high != null) drawBand(line.low, line.high, plot.x, y, line.color.copy(alpha = 0.15f))
            drawSeries(line.values, plot.x, y, line.color, 2.dp.toPx(), line.dashed)
        }
    }
}

/** Bars on the left axis with a line on the right axis (pump run time and outdoor temperature). */
@Composable
fun BarLineChart(
    bars: List<Float?>,
    line: List<Float?>,
    barColor: Color,
    lineColor: Color,
    xTicks: List<XTick>,
    modifier: Modifier = Modifier,
    barFormat: (Float) -> String = { formatAxis(it) },
    lineFormat: (Float) -> String = { formatAxis(it) },
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    height: Dp = 150.dp,
    description: String? = null,
) {
    val hasLine = line.any { it != null }
    ChartFrame(bars.size, xTicks, hasLine, selected, onSelect, height, modifier, description) { plot, labels ->
        val maxBar = (bars.filterNotNull().maxOrNull() ?: 0f).coerceAtLeast(1f)
        val barRange = 0f to niceCeil(maxBar)
        labels.axis(barRange, ChartAxis.LEFT, barFormat)
        val yBar = plot.y(barRange)
        val slot = plot.width / bars.size.coerceAtLeast(1)
        val barW = (slot * 0.62f).coerceAtLeast(1f)
        bars.forEachIndexed { i, v ->
            if (v == null || v <= 0f) return@forEachIndexed
            val top = yBar(v)
            drawRoundRect(
                barColor,
                topLeft = Offset(plot.x(i) - barW / 2, top),
                size = Size(barW, plot.bottom - top),
                cornerRadius = CornerRadius((barW / 4).coerceAtMost(3.dp.toPx())),
            )
        }
        if (hasLine) {
            val v = line.filterNotNull()
            val range = paddedRange(v.min(), v.max(), minSpan = 4f)
            labels.axis(range, ChartAxis.RIGHT, lineFormat)
            drawSeries(line, plot.x, plot.y(range), lineColor, 2.dp.toPx(), dashed = false)
        }
    }
}

/** One cell per day: full color = online all day, warning = with interruptions, gray = no data. */
@Composable
fun AvailabilityStrip(values: List<Float?>, modifier: Modifier = Modifier, labels: List<String> = emptyList()) {
    val s = AppTheme.status
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            values.forEach { v ->
                val color = when {
                    v == null -> empty
                    v >= 0.999f -> s.active
                    v >= 0.5f -> s.warning
                    else -> s.danger
                }
                Box(modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(3.dp)).background(color))
            }
        }
        if (labels.size == values.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                labels.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 24h / 7d / 30d (or any short options), small enough for a card header. */
@Composable
fun <T> PeriodTabs(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Row(modifier = Modifier.padding(2.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) AppTheme.status.onActiveContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) AppTheme.status.activeContainer else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(value) })
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The muted line under a chart: legend, or the values of the touched point. */
@Composable
fun ChartReadout(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A small colored legend mark (solid or dashed line) for use in front of a label. */
@Composable
fun LegendMark(color: Color, dashed: Boolean = false, bar: Boolean = false) {
    Canvas(modifier = Modifier.width(14.dp).height(10.dp)) {
        if (bar) {
            drawRoundRect(color, topLeft = Offset(size.width * 0.2f, 0f), size = Size(size.width * 0.6f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
        } else {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
    }
}

@Composable
fun Legend(items: List<Triple<Color, String, LegendStyle>>, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        items.forEach { (color, label, style) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LegendMark(color, dashed = style == LegendStyle.DASHED, bar = style == LegendStyle.BAR)
                ChartReadout(label)
            }
        }
    }
}

enum class LegendStyle { SOLID, DASHED, BAR }

/** One series in a [ToggleLegend]. */
data class LegendToggle(val color: Color, val label: String, val visible: Boolean, val onToggle: () -> Unit)

/**
 * Legend whose entries turn their line on and off. A hidden entry keeps its place, with a gray
 * mark and a crossed-out label, so it can be turned back on.
 */
@Composable
fun ToggleLegend(items: List<LegendToggle>, modifier: Modifier = Modifier) {
    val off = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .toggleable(value = item.visible, role = Role.Checkbox, onValueChange = { item.onToggle() })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                LegendMark(if (item.visible) item.color else off)
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        textDecoration = if (item.visible) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
                    ),
                    color = if (item.visible) MaterialTheme.colorScheme.onSurfaceVariant else off,
                )
            }
        }
    }
}

// --- frame, scales, drawing ---------------------------------------------------------------

/** Plot area geometry handed to a chart's drawing code. */
class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float, private val n: Int) {
    val width: Float get() = right - left
    val x: (Int) -> Float = { i -> left + (i + 0.5f) * width / n.coerceAtLeast(1) }
    fun y(range: Pair<Float, Float>): (Float) -> Float = { v ->
        bottom - (v - range.first) / (range.second - range.first) * (bottom - top)
    }
    fun indexAt(px: Float): Int = floor((px - left) / width * n).toInt().coerceIn(0, n - 1)
}

/** Draws axis labels; the frame's grid lines follow the left axis. */
class AxisLabels(private val scope: DrawScope, private val plot: PlotArea, private val draw: DrawScope.(String, Offset, Boolean) -> Unit, private val grid: Color) {
    fun axis(range: Pair<Float, Float>, side: ChartAxis, format: (Float) -> String) {
        val y = plot.y(range)
        listOf(range.first, (range.first + range.second) / 2, range.second).forEach { v ->
            if (side == ChartAxis.LEFT) {
                scope.drawLine(grid, Offset(plot.left, y(v)), Offset(plot.right, y(v)), strokeWidth = 1f)
                scope.draw(format(v), Offset(plot.left - 4f, y(v)), true)
            } else {
                scope.draw(format(v), Offset(plot.right + 4f, y(v)), false)
            }
        }
    }
}

@Composable
private fun ChartFrame(
    n: Int,
    xTicks: List<XTick>,
    rightAxis: Boolean,
    selected: Int?,
    onSelect: ((Int?) -> Unit)?,
    height: Dp,
    modifier: Modifier,
    description: String?,
    content: DrawScope.(PlotArea, AxisLabels) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val guide = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    // Written while drawing, read by the touch handler (which outlives a single composition).
    val plotRef = androidx.compose.runtime.remember { arrayOfNulls<PlotArea>(1) }
    val touch = if (onSelect == null || n == 0) Modifier else Modifier.pointerInput(n) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            plotRef[0]?.let { onSelect(it.indexAt(down.position.x)) }
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                plotRef[0]?.let { onSelect(it.indexAt(change.position.x)) }
                // Mostly horizontal movement scrubs the chart; vertical goes to the list scroll.
                val d = change.positionChange()
                if (kotlin.math.abs(d.x) > kotlin.math.abs(d.y)) change.consume()
            } while (change.pressed)
        }
    }
    Canvas(
        modifier = modifier.fillMaxWidth().height(height).then(touch)
            .semantics { description?.let { contentDescription = it } },
    ) {
        val labelW = AxisLabelWidth.toPx()
        val plot = PlotArea(
            left = labelW,
            top = 6.dp.toPx(),
            right = size.width - if (rightAxis) labelW else 6.dp.toPx(),
            bottom = size.height - XLabelHeight.toPx(),
            n = n,
        )
        plotRef[0] = plot
        if (n == 0) return@Canvas
        val drawLabel: DrawScope.(String, Offset, Boolean) -> Unit = { text, at, alignRight ->
            val layout = measurer.measure(text, labelStyle)
            val x = if (alignRight) at.x - layout.size.width else at.x
            drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
        }
        content(plot, AxisLabels(this, plot, drawLabel, grid))
        xTicks.forEach { t ->
            if (t.index !in 0 until n) return@forEach
            val layout = measurer.measure(t.label, labelStyle)
            val x = (plot.x(t.index) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, plot.bottom + 2.dp.toPx()))
        }
        selected?.takeIf { it in 0 until n }?.let { i ->
            drawLine(guide, Offset(plot.x(i), plot.top), Offset(plot.x(i), plot.bottom), strokeWidth = 1.dp.toPx())
        }
    }
}

private fun DrawScope.drawSeries(values: List<Float?>, x: (Int) -> Float, y: (Float) -> Float, color: Color, width: Float, dashed: Boolean) {
    var path: Path? = null
    var runLength = 0
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(width * 2.5f, width * 2f)) else null
    fun flush(lastIndex: Int) {
        val p = path ?: return
        if (runLength == 1) {
            drawCircle(color, radius = width, center = Offset(x(lastIndex), y(values[lastIndex]!!)))
        } else {
            drawPath(p, color, style = Stroke(width = width, cap = StrokeCap.Round, pathEffect = effect))
        }
        path = null
        runLength = 0
    }
    values.forEachIndexed { i, v ->
        if (v == null) {
            flush(i - 1)
        } else {
            val p = path ?: Path().also { path = it; it.moveTo(x(i), y(v)) }
            if (runLength > 0) p.lineTo(x(i), y(v))
            runLength++
        }
    }
    flush(values.lastIndex)
}

private fun DrawScope.drawBand(low: List<Float?>, high: List<Float?>, x: (Int) -> Float, y: (Float) -> Float, color: Color) {
    var start = -1
    fun fill(from: Int, to: Int) {
        if (to <= from) return
        val p = Path()
        p.moveTo(x(from), y(high[from]!!))
        for (i in from + 1..to) p.lineTo(x(i), y(high[i]!!))
        for (i in to downTo from) p.lineTo(x(i), y(low[i]!!))
        p.close()
        drawPath(p, color)
    }
    for (i in 0..low.size) {
        val ok = i < low.size && low[i] != null && high.getOrNull(i) != null
        if (ok && start < 0) start = i
        if (!ok && start >= 0) {
            fill(start, i - 1)
            start = -1
        }
    }
}

/** Range with some headroom, at least [minSpan] wide, rounded to whole units. */
fun paddedRange(min: Float, max: Float, minSpan: Float): Pair<Float, Float> {
    var lo = min
    var hi = max
    if (hi - lo < minSpan) {
        val mid = (hi + lo) / 2
        lo = mid - minSpan / 2
        hi = mid + minSpan / 2
    }
    val pad = (hi - lo) * 0.08f
    return floor(lo - pad) to ceil(hi + pad)
}

/** 1, 2, 5, 10, 20, 50 ... at or above [v]. */
fun niceCeil(v: Float): Float {
    var step = 1f
    while (true) {
        for (m in floatArrayOf(1f, 2f, 5f)) if (m * step >= v) return m * step
        step *= 10f
    }
}

fun formatAxis(v: Float): String = if (v == floor(v)) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
