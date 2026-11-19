package zhb.company.smart_home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen

/** Port of TextWithButton - a label + ON/OFF button bound to a single device toggle. */
@Composable
fun DeviceToggleRow(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label)
        Button(
            onClick = { onToggle(!isOn) },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = if (isOn) EnableGreen else DisableRed),
        ) {
            Text(text = if (isOn) stringResource(R.string.on) else stringResource(R.string.off))
        }
    }
}
