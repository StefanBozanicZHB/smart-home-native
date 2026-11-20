package zhb.company.smart_home.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.ui.theme.AppTheme

/*
 * Shared dashboard building blocks for every screen of the redesign. Colors come only from
 * MaterialTheme.colorScheme and AppTheme.status, so light and dark themes stay consistent.
 */

val CardShape = RoundedCornerShape(16.dp)
val TileShape = RoundedCornerShape(12.dp)

/** Screen title row used under the status bar on every main tab. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    horizontalPadding: Dp = 16.dp,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        trailing()
    }
}

/** A flat card on the container color. Clickable when [onClick] is set. */
@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shapeModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = shapeModifier) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    } else {
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = shapeModifier) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Small icon + label at the top of a card, with an optional element on the right (chip, switch). */
@Composable
fun CardHeader(icon: ImageVector, title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp).weight(1f),
        )
        trailing()
    }
}

enum class Tone { ACTIVE, NEUTRAL, WARNING, DANGER }

@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> {
    val s = AppTheme.status
    return when (tone) {
        Tone.ACTIVE -> s.activeContainer to s.onActiveContainer
        Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        Tone.WARNING -> s.warningContainer to s.onWarningContainer
        Tone.DANGER -> s.dangerContainer to s.onDangerContainer
    }
}

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(tone)
    Surface(color = bg, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** A big number with a muted label above and an optional line below, e.g. "Napolju / 3.5° / 81 %". */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null, valueColor: Color = Color.Unspecified) {
    Surface(shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Value in the large display style used inside cards ("21.5°"). */
@Composable
fun BigValue(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = color, modifier = modifier)
}

/**
 * A switchable device. The whole tile toggles; it is highlighted while on. When [enabled] is false
 * (its node is offline) it shows [disabledReason] instead of [detail] and does not toggle.
 */
@Composable
fun DeviceTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    val s = AppTheme.status
    val bg = if (on && enabled) s.activeContainer else MaterialTheme.colorScheme.surfaceContainer
    val fg = if (on && enabled) s.onActiveContainer else MaterialTheme.colorScheme.onSurface
    val stateText = if (on) "uključeno" else "isključeno"
    Surface(
        shape = TileShape,
        color = bg,
        modifier = modifier
            .semantics { stateDescription = stateText }
            .clickable(enabled = enabled, role = Role.Switch) { onToggle(!on) },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = on, onCheckedChange = null, enabled = enabled, modifier = Modifier.scale(0.8f))
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val second = if (!enabled) disabledReason else detail
            if (second != null) {
                Text(
                    second,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!enabled) s.danger else fg.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A device or place that opens a screen instead of toggling (e.g. "Ulazna vrata", "Prskalice"). */
@Composable
fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Surface(onClick = onClick, shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Full-width warning/error strip shown only while the problem exists. */
@Composable
fun AlertBanner(icon: ImageVector, text: String, tone: Tone, modifier: Modifier = Modifier, detail: String? = null) {
    val (bg, fg) = toneColors(tone)
    Surface(shape = TileShape, color = bg, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = fg)
                if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

/** One-line summary of something that is idle right now (e.g. heating in summer), with an action. */
@Composable
fun CompactRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(onClick = onClick, shape = TileShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp).padding(vertical = 4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp).weight(1f).padding(vertical = 8.dp))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

/** Muted section label between groups of cards ("Uključeno (2)"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing()
    }
}
