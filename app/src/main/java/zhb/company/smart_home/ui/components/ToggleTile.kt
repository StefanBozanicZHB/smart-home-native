package zhb.company.smart_home.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

/**
 * A modern smart-home-style toggle card: circular icon badge, label, on/off caption and a
 * [Switch], with a subtly tinted container when the device is on. Used for compact device grids
 * (e.g. Garden screen) instead of a plain settings-style row.
 */
@Composable
fun ToggleTile(
    label: String,
    icon: ImageVector,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLabel: String = "Uključeno",
    offLabel: String = "Isključeno",
) {
    val containerColor by animateColorAsState(
        targetValue = if (isOn) PrimaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "tileContainer",
    )
    val iconBg by animateColorAsState(
        targetValue = if (isOn) PrimaryColor else MaterialTheme.colorScheme.outlineVariant,
        label = "tileIconBg",
    )
    val iconTint = if (isOn) White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        onClick = { if (enabled) onToggle(!isOn) },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(color = iconBg, shape = CircleShape) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(8.dp).size(20.dp))
                }
                Switch(
                    checked = isOn,
                    onCheckedChange = { if (enabled) onToggle(it) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = EnableGreen, checkedThumbColor = White),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = if (isOn) onLabel else offLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Compact variant of [ToggleTile] for dense grids (e.g. the 6-sprinkler dialog). */
@Composable
fun CompactToggleTile(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (isOn) PrimaryColor else MaterialTheme.colorScheme.surfaceVariant,
        label = "compactTileContainer",
    )
    val contentColor = if (isOn) White else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(14.dp),
        onClick = { onToggle(!isOn) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor, fontWeight = FontWeight.Medium)
        }
    }
}
