package zhb.company.smart_home.ui.house

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.AcCommandBuilder
import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatTile
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.format.NO_VALUE

@Composable
fun HouseScreen(viewModel: HouseViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HouseContent(state, viewModel::onIntent)
}

@Composable
private fun HouseContent(state: HouseState, onIntent: (HouseIntent) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.menu_house_combined), horizontalPadding = 0.dp) }
        item {
            FloorCard(
                icon = Icons.Outlined.Chair,
                title = stringResource(R.string.home_room_upper),
                online = state.upperOnline,
                temperature = state.upperTemperature,
                humidity = state.upperHumidity,
                air = state.co2.takeUnless { it == NO_VALUE || it.isBlank() },
            )
        }
        item { AcCard(state, onIntent) }
        item {
            FloorCard(
                icon = Icons.Outlined.Weekend,
                title = stringResource(R.string.home_room_ground),
                online = state.groundOnline,
                temperature = state.groundTemperature,
                humidity = state.groundHumidity,
                air = null,
            )
        }
    }
}

@Composable
private fun FloorCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, online: Boolean, temperature: String, humidity: String, air: String?) {
    DashboardCard {
        CardHeader(icon, title) {
            if (!online) StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.house_temperature), "$temperature°", Modifier.weight(1f))
            StatTile(stringResource(R.string.house_humidity), stringResource(R.string.home_humidity, humidity), Modifier.weight(1f))
            if (air != null) StatTile(stringResource(R.string.house_air_short), air, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AcCard(state: HouseState, onIntent: (HouseIntent) -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.AcUnit, stringResource(R.string.ac_title)) {
            Switch(
                checked = state.acOn,
                onCheckedChange = { onIntent(if (it) HouseIntent.PowerOn else HouseIntent.PowerOff) },
                enabled = state.upperOnline,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            FilledTonalIconButton(onClick = { onIntent(HouseIntent.DecreaseTemp) }, enabled = state.upperOnline && state.acTemp > AcCommandBuilder.MIN_TEMP) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.ac_temp_decrease))
            }
            Text(
                "${state.acTemp}°",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (state.acOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            FilledTonalIconButton(onClick = { onIntent(HouseIntent.IncreaseTemp) }, enabled = state.upperOnline && state.acTemp < AcCommandBuilder.MAX_TEMP) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ac_temp_increase))
            }
        }
        Text(stringResource(R.string.ac_mode_caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val modes = listOf(AcMode.AUTO to R.string.ac_fan_auto, AcMode.COOL to R.string.ac_mode_cool, AcMode.HEAT to R.string.ac_mode_heat, AcMode.FAN to R.string.ac_mode_fan)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = state.acMode == mode,
                    onClick = { onIntent(HouseIntent.SelectMode(mode)) },
                    enabled = state.upperOnline,
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    icon = {},
                    label = { Text(stringResource(label), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Text(stringResource(R.string.ac_fan_caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val fans = listOf(AcFanSpeed.AUTO to R.string.ac_fan_auto, AcFanSpeed.MIN to R.string.ac_fan_min, AcFanSpeed.MED to R.string.ac_fan_med, AcFanSpeed.MAX to R.string.ac_fan_max)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            fans.forEachIndexed { i, (fan, label) ->
                SegmentedButton(
                    selected = state.acFanSpeed == fan,
                    onClick = { onIntent(HouseIntent.SelectFanSpeed(fan)) },
                    enabled = state.upperOnline,
                    shape = SegmentedButtonDefaults.itemShape(i, fans.size),
                    icon = {},
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
        Text(stringResource(R.string.house_ac_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- previews ------------------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "House", heightDp = 1100)
@Composable
private fun HousePreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            HouseContent(
                HouseState(
                    groundOnline = true, groundTemperature = "21.5", groundHumidity = "45",
                    upperOnline = true, upperTemperature = "22.4", upperHumidity = "40", co2 = "312",
                    acOn = true, acMode = AcMode.COOL, acFanSpeed = AcFanSpeed.AUTO, acTemp = 23,
                ),
                onIntent = {},
            )
        }
    }
}
