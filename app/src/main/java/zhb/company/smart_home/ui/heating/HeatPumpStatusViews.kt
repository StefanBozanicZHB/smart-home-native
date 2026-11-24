package zhb.company.smart_home.ui.heating

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.heating.HeatPumpStatus
import zhb.company.smart_home.ui.components.AnimatedText
import zhb.company.smart_home.ui.components.Appearing
import zhb.company.smart_home.ui.components.CardShape
import zhb.company.smart_home.ui.components.Motion
import zhb.company.smart_home.ui.components.animatedColor
import zhb.company.smart_home.ui.theme.AppTheme

/** Headline and explanation for a heat pump status, shared by the heating and home screens. */
@Composable
fun heatPumpStatusText(status: HeatPumpStatus): Pair<String, String?> = when (status) {
    is HeatPumpStatus.Running -> {
        val title = status.minutes?.let { stringResource(R.string.pump_status_running_for, it) } ?: stringResource(R.string.pump_status_running)
        val detail = when {
            status.demand -> stringResource(R.string.pump_status_running_demand)
            status.minRunLeftMinutes != null -> stringResource(R.string.pump_status_running_min_run, status.minRunLeftMinutes)
            else -> stringResource(R.string.pump_status_running_stopping)
        }
        title to detail
    }
    is HeatPumpStatus.Waiting ->
        (status.startsInMinutes?.let { stringResource(R.string.pump_status_waiting_in, it) } ?: stringResource(R.string.pump_status_waiting_soon)) to
            stringResource(R.string.pump_status_waiting_detail)
    is HeatPumpStatus.Idle ->
        (status.sinceTime?.let { stringResource(R.string.pump_status_idle_since, it) } ?: stringResource(R.string.pump_status_idle)) to
            stringResource(R.string.pump_status_idle_detail)
    HeatPumpStatus.Off -> stringResource(R.string.pump_status_off) to stringResource(R.string.pump_status_off_detail)
    is HeatPumpStatus.SafeMode -> stringResource(R.string.pump_status_safe) to safeModeText(status.reason)
    HeatPumpStatus.Unknown -> stringResource(R.string.pump_status_unknown) to stringResource(R.string.pump_status_unknown_detail)
}

private data class StatusLook(val icon: ImageVector, val container: Color, val content: Color, val spin: Boolean = false)

@Composable
private fun look(status: HeatPumpStatus): StatusLook {
    val s = AppTheme.status
    val c = MaterialTheme.colorScheme
    return when (status) {
        is HeatPumpStatus.Running -> StatusLook(Icons.Outlined.Sync, s.activeContainer, s.onActiveContainer, spin = true)
        is HeatPumpStatus.Waiting -> StatusLook(Icons.Outlined.HourglassTop, s.warningContainer, s.onWarningContainer)
        is HeatPumpStatus.Idle -> StatusLook(Icons.Outlined.PauseCircle, c.surfaceContainerHigh, c.onSurface)
        HeatPumpStatus.Off -> StatusLook(Icons.Outlined.PowerSettingsNew, c.surfaceContainerHighest, c.onSurfaceVariant)
        is HeatPumpStatus.SafeMode -> StatusLook(Icons.Outlined.WarningAmber, s.dangerContainer, s.onDangerContainer)
        HeatPumpStatus.Unknown -> StatusLook(Icons.AutoMirrored.Outlined.HelpOutline, c.surfaceContainerHighest, c.onSurfaceVariant)
    }
}

@Composable
private fun StatusIcon(look: StatusLook, size: Int) {
    val angle by rememberInfiniteTransition(label = "pumpSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart),
        label = "pumpSpinAngle",
    )
    Icon(look.icon, contentDescription = null, tint = look.content, modifier = Modifier.size(size.dp).rotate(if (look.spin) angle else 0f))
}

/**
 * The first thing on the heating screen: is the heat pump running, and why (not). A big colored
 * card; with the main switch off it offers to switch heating on.
 */
@Composable
fun HeatPumpStatusCard(status: HeatPumpStatus, modifier: Modifier = Modifier, onTurnOn: (() -> Unit)? = null) {
    val look = look(status)
    val (title, detail) = heatPumpStatusText(status)
    // A new state (running → waiting → idle …) cross-fades its color, icon and text.
    val container = animatedColor(look.container, "statusBg")
    val content = animatedColor(look.content, "statusFg")
    Surface(shape = CardShape, color = container, modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Surface(shape = CircleShape, color = content.copy(alpha = 0.12f), modifier = Modifier.size(52.dp)) {
                Crossfade(look.copy(container = Color.Unspecified, content = Color.Unspecified), animationSpec = Motion.standard(), label = "statusIcon") { shown ->
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { StatusIcon(shown.copy(content = content), 30) }
                }
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                AnimatedText(title) { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = content) }
                Appearing(detail) { d -> AnimatedText(d) { Text(it, style = MaterialTheme.typography.bodyMedium, color = content) } }
                Appearing(status == HeatPumpStatus.Off && onTurnOn != null) {
                    TextButton(onClick = { onTurnOn?.invoke() }, modifier = Modifier.padding(top = 2.dp)) { Text(stringResource(R.string.home_heating_turn_on)) }
                }
            }
        }
    }
}

/** One-line version for the home screen's heating card. */
@Composable
fun HeatPumpStatusLine(status: HeatPumpStatus, modifier: Modifier = Modifier) {
    val look = look(status)
    val (title, _) = heatPumpStatusText(status)
    val content = animatedColor(look.content, "statusLineFg")
    Surface(shape = CardShape, color = animatedColor(look.container, "statusLineBg"), modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Crossfade(look.copy(container = Color.Unspecified, content = Color.Unspecified), animationSpec = Motion.standard(), label = "statusLineIcon") { shown ->
                StatusIcon(shown.copy(content = content), 20)
            }
            Box(modifier = Modifier.padding(start = 8.dp)) {
                AnimatedText(title) { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = content) }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Heat pump status - all states", heightDp = 1100)
@Composable
private fun HeatPumpStatusPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                HeatPumpStatusCard(HeatPumpStatus.Running(42, demand = true, minRunLeftMinutes = null))
                HeatPumpStatusCard(HeatPumpStatus.Running(7, demand = false, minRunLeftMinutes = 3))
                HeatPumpStatusCard(HeatPumpStatus.Waiting(4))
                HeatPumpStatusCard(HeatPumpStatus.Idle("21:10"))
                HeatPumpStatusCard(HeatPumpStatus.Off, onTurnOn = {})
                HeatPumpStatusCard(HeatPumpStatus.SafeMode("room_temp_stale"))
                HeatPumpStatusCard(HeatPumpStatus.Unknown)
            }
        }
    }
}
