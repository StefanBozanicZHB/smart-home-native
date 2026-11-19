package zhb.company.smart_home.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerpFloat
import kotlin.math.roundToInt
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

/** A single compact stat (icon + value), e.g. temperature or humidity, shown in [CollapsingRoomHeader]. */
data class RoomStat(val icon: ImageVector, val text: String)

/**
 * How "collapsed" a [LazyListState] header is: 0f while the header (item index 0, height
 * [headerHeight]) is still fully visible, sliding to 1f once it has been scrolled fully away.
 */
@Composable
fun rememberHeaderCollapseFraction(listState: LazyListState, headerHeight: Dp): State<Float> {
    val headerPx = with(LocalDensity.current) { headerHeight.toPx() }
    return remember(listState, headerPx) {
        derivedStateOf {
            val offsetPx = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                headerPx
            }
            if (headerPx <= 0f) 0f else (offsetPx / headerPx).coerceIn(0f, 1f)
        }
    }
}

val CompactBarHeight = 64.dp
private val EdgeMargin = 16.dp

/**
 * The header's fully-collapsed height (compact bar + status bar inset). The paired scrollable
 * list must reserve exactly this much *permanent* top space (via a fixed [Modifier.padding], not
 * scrollable `contentPadding`) so its content never scrolls out from underneath the pinned,
 * fully-collapsed header - see [CollapsingRoomHeader]'s doc for the full pairing recipe.
 */
@Composable
fun rememberCompactHeaderHeight(): Dp {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    return CompactBarHeight + with(density) { statusBarPx.toDp() }
}

/**
 * Native-feeling collapsing toolbar for room screens: the room photo itself lives inside this
 * overlay and visibly shrinks away as the paired [LazyListState] scrolls, ending as a compact
 * solid [PrimaryColor] app bar - a real Android `CollapsingToolbarLayout` feel, not a color bar
 * floating over a separately-scrolling image. The room title + online/offline status stay pinned
 * top-start throughout; the [stats] (temperature/humidity/...) continuously slide from
 * bottom-start (fully expanded) to top-end (fully collapsed) as the header collapses.
 *
 * Pairing recipe for the scrollable list: give the list itself a fixed
 * `Modifier.padding(top = rememberCompactHeaderHeight())` (so content can never scroll fully
 * underneath the pinned collapsed bar), then use
 * `Spacer(Modifier.height(expandedHeight - rememberCompactHeaderHeight()))` as its first item -
 * only that *extra* expanded portion is collapsible/scrollable. Drive
 * [rememberHeaderCollapseFraction] off that same reduced distance.
 */
@Composable
fun CollapsingRoomHeader(
    listState: LazyListState,
    expandedHeight: Dp,
    @DrawableRes imageRes: Int,
    title: String,
    online: Boolean,
    stats: List<RoomStat>,
    modifier: Modifier = Modifier,
    // Screens backed by a single node/controller show a live online/offline pill next to the
    // title. Screens that combine several controllers (e.g. the House screen's two floors) pass
    // false here and show each controller's own online status inline in their own section
    // instead, since one shared pill can't honestly represent more than one controller.
    showStatusPill: Boolean = true,
    // Every room screen except Home shows a back button next to the title, returning there.
    onBack: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val statusBarDp = with(density) { statusBarPx.toDp() }
    val compactTotalHeight = CompactBarHeight + statusBarDp
    val collapsibleDistance = (expandedHeight - compactTotalHeight).coerceAtLeast(0.dp)
    val collapseFraction by rememberHeaderCollapseFraction(listState, collapsibleDistance)
    val currentHeight = lerpDp(expandedHeight, compactTotalHeight, collapseFraction)

    var statsSize by remember { mutableStateOf(IntSize.Zero) }
    var titleSize by remember { mutableStateOf(IntSize.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight),
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val expandedHeightPx = with(density) { expandedHeight.toPx() }
        val compactHeightPx = with(density) { CompactBarHeight.toPx() }
        val edgeMarginPx = with(density) { EdgeMargin.toPx() }

        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Bottom gradient scrim for badge legibility while expanded, fading out as it collapses.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f * (1f - collapseFraction)),
                            Color.Black.copy(alpha = 0.55f * (1f - collapseFraction)),
                        ),
                    ),
                ),
        )

        // Solid primary-color wash that fades in as the header collapses into a normal toolbar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PrimaryColor.copy(alpha = collapseFraction)),
        )

        // Title + online status: pinned at the left, sliding down slightly to end up vertically
        // centered in the compact bar (matching the stats row's collapsed vertical center below).
        val titleExpandedY = statusBarPx + with(density) { 10.dp.toPx() }
        val titleCollapsedY = statusBarPx + (compactHeightPx - titleSize.height) / 2f
        val titleOffsetY = lerpFloat(titleExpandedY, titleCollapsedY, collapseFraction)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .onSizeChanged { titleSize = it }
                .offset { IntOffset(edgeMarginPx.roundToInt(), titleOffsetY.roundToInt()) },
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.nav_back),
                        tint = White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = title,
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showStatusPill) {
                Spacer(modifier = Modifier.width(10.dp))
                PulsingStatusDot(online = online)
                Text(
                    text = if (online) stringResource(R.string.connections_online) else stringResource(R.string.connections_offline),
                    color = White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        // Stats (temperature/humidity): slide continuously bottom-start -> top-end as it collapses.
        val expandedX = edgeMarginPx
        val expandedY = expandedHeightPx - statsSize.height - edgeMarginPx
        val collapsedX = containerWidthPx - statsSize.width - edgeMarginPx
        val collapsedY = statusBarPx + (compactHeightPx - statsSize.height) / 2f
        val offsetX = lerpFloat(expandedX, collapsedX, collapseFraction)
        val offsetY = lerpFloat(expandedY, collapsedY, collapseFraction)

        Row(
            modifier = Modifier
                .onSizeChanged { statsSize = it }
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
        ) {
            stats.forEachIndexed { index, stat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = if (index == 0) 0.dp else 12.dp),
                ) {
                    Icon(imageVector = stat.icon, contentDescription = null, tint = White, modifier = Modifier.size(15.dp))
                    Text(
                        text = stat.text,
                        color = White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * A live/online-style status dot: a solid core with a ring that repeatedly expands and fades
 * out around it, so the online/offline state reads as an obviously "live" signal rather than a
 * static colored dot.
 */
@Composable
fun PulsingStatusDot(online: Boolean, modifier: Modifier = Modifier) {
    val color = if (online) EnableGreen else DisableRed
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "statusPulseScale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "statusPulseAlpha",
    )
    Box(modifier = modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .background(color, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
    }
}
