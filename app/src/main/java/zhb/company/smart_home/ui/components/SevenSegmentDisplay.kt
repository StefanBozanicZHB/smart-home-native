package zhb.company.smart_home.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.ui.theme.PrimaryColor

// Segment layout, 7 segments (a top, b upper-right, c lower-right, d bottom, e lower-left, f upper-left, g middle).
private val digitSegments: Map<Int, Set<Char>> = mapOf(
    0 to setOf('a', 'b', 'c', 'd', 'e', 'f'),
    1 to setOf('b', 'c'),
    2 to setOf('a', 'b', 'g', 'e', 'd'),
    3 to setOf('a', 'b', 'g', 'c', 'd'),
    4 to setOf('f', 'g', 'b', 'c'),
    5 to setOf('a', 'f', 'g', 'c', 'd'),
    6 to setOf('a', 'f', 'g', 'e', 'c', 'd'),
    7 to setOf('a', 'b', 'c'),
    8 to setOf('a', 'b', 'c', 'd', 'e', 'f', 'g'),
    9 to setOf('a', 'b', 'c', 'd', 'f', 'g'),
)

/** Retro 7-segment digit readout, replacing the `segment_display` Flutter package. */
@Composable
fun SevenSegmentDigit(digit: Int, modifier: Modifier = Modifier, color: Color = PrimaryColor, offColor: Color = color.copy(alpha = 0.12f)) {
    Canvas(modifier = modifier.size(36.dp, 56.dp)) {
        val lit = digitSegments[digit].orEmpty()
        drawSegments(lit, color, offColor)
    }
}

@Composable
fun SevenSegmentNumber(value: Int, modifier: Modifier = Modifier, color: Color = PrimaryColor) {
    val digits = value.coerceIn(0, 99).toString().padStart(2, '0').map { it - '0' }
    Row(modifier = modifier.padding(4.dp)) {
        digits.forEach { d -> SevenSegmentDigit(digit = d, modifier = Modifier.padding(2.dp), color = color) }
    }
}

private fun DrawScope.drawSegments(lit: Set<Char>, onColor: Color, offColor: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.18f
    val cap = StrokeCap.Round

    fun colorFor(seg: Char) = if (seg in lit) onColor else offColor

    // a: top
    drawLine(colorFor('a'), Offset(w * 0.2f, h * 0.05f), Offset(w * 0.8f, h * 0.05f), stroke, cap)
    // b: upper-right
    drawLine(colorFor('b'), Offset(w * 0.85f, h * 0.08f), Offset(w * 0.85f, h * 0.48f), stroke, cap)
    // c: lower-right
    drawLine(colorFor('c'), Offset(w * 0.85f, h * 0.52f), Offset(w * 0.85f, h * 0.92f), stroke, cap)
    // d: bottom
    drawLine(colorFor('d'), Offset(w * 0.2f, h * 0.95f), Offset(w * 0.8f, h * 0.95f), stroke, cap)
    // e: lower-left
    drawLine(colorFor('e'), Offset(w * 0.15f, h * 0.52f), Offset(w * 0.15f, h * 0.92f), stroke, cap)
    // f: upper-left
    drawLine(colorFor('f'), Offset(w * 0.15f, h * 0.08f), Offset(w * 0.15f, h * 0.48f), stroke, cap)
    // g: middle
    drawLine(colorFor('g'), Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), stroke, cap)
}
