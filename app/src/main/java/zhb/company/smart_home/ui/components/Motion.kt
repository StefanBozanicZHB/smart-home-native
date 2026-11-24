package zhb.company.smart_home.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Motion shared by every screen. Rules:
 * - short (150-400 ms) and only on a change: nothing animates when a screen is first shown,
 *   since animate*AsState and AnimatedContent start at their first value;
 * - the same durations and easing everywhere;
 * - the system setting "Remove animations" (animator duration scale 0) turns them all off by
 *   itself, Compose follows it.
 */
object Motion {
    /** Small things: a color, a short label. */
    const val SHORT_MS = 150

    /** Most state changes: a tile turning on, a value changing, a block opening. */
    const val MEDIUM_MS = 250

    /** Larger movements: a screen sliding in, a chart drawing. */
    const val LONG_MS = 400

    fun <T> standard(durationMs: Int = MEDIUM_MS): FiniteAnimationSpec<T> = tween(durationMs, easing = FastOutSlowInEasing)
}

/** [target] color, cross-faded from the previous one when it changes. */
@Composable
fun animatedColor(target: Color, label: String = "color"): Color =
    animateColorAsState(target, Motion.standard(), label = label).value
