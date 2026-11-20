package zhb.company.smart_home.ui.garden

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.R

/** − value + for whole minutes within [range]; [zeroLabel] replaces "0 min" (e.g. "–" for a skipped zone). */
@Composable
internal fun MinuteStepper(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    zeroLabel: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        FilledTonalIconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = enabled && value > range.first) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.irrigation_less))
        }
        Text(
            if (value == 0 && zeroLabel != null) zeroLabel else stringResource(R.string.garden_minutes_short, value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        FilledTonalIconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = enabled && value < range.last) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.irrigation_more))
        }
    }
}
