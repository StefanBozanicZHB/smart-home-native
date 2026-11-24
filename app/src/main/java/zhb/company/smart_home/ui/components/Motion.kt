package zhb.company.smart_home.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/**
 * A LazyColumn item that fades in when it appears, fades out when it goes, and slides to its new
 * place when items above it come or go. [key] must be unique and stable within the list.
 */
fun LazyListScope.animatedItem(key: Any, content: @Composable () -> Unit) {
    item(key = key) {
        Box(
            modifier = Modifier.fillMaxWidth().animateItem(
                fadeInSpec = tween(Motion.MEDIUM_MS),
                placementSpec = Motion.standard(),
                fadeOutSpec = tween(Motion.SHORT_MS),
            ),
        ) { content() }
    }
}

/** Shows [content] for [value] while it is not null; it opens and closes instead of jumping in. */
@Composable
fun <T : Any> Appearing(value: T?, content: @Composable (T) -> Unit) {
    // The last value stays drawn while the block closes.
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    AnimatedVisibility(
        visible = value != null,
        enter = expandVertically(Motion.standard()) + fadeIn(Motion.standard()),
        exit = shrinkVertically(Motion.standard()) + fadeOut(Motion.standard(Motion.SHORT_MS)),
    ) {
        last?.let { content(it) }
    }
}

/** [content] while [visible]; it opens and closes instead of jumping in. */
@Composable
fun Appearing(visible: Boolean, content: @Composable () -> Unit) {
    Appearing(if (visible) Unit else null) { content() }
}
