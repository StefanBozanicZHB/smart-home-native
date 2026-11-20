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
import zhb.company.smart_home.ui.components.ChoiceGroup
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatTile
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.TileGrid
import zhb.company.smart_home.ui.components.adaptiveColumns
import zhb.company.smart_home.ui.format.NO_VALUE
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.ui.history.HistoryAutoLoad
import zhb.company.smart_home.ui.history.HistoryState
import zhb.company.smart_home.ui.history.HistoryViewModel
import zhb.company.smart_home.ui.history.ClimateHistoryCard

@Composable
fun HouseScreen(viewModel: HouseViewModel = koinViewModel(), historyViewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by historyViewModel.state.collectAsStateWithLifecycle()
    HistoryAutoLoad(historyViewModel, HistoryPeriod.entries.toSet())
    HouseContent(state, viewModel::onIntent, history)
}

@Composable
private fun HouseContent(state: HouseState, onIntent: (HouseIntent) -> Unit, history: HistoryState = HistoryState()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.menu_house_combined), horizontalPadding = 0.dp) }
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
        item { ClimateHistoryCard(history) }
    }
}

@Composable
private fun FloorCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, online: Boolean, temperature: String, humidity: String, air: String?) {
    DashboardCard {
        CardHeader(icon, title) {
            if (!online) StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
        }
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            add { m -> StatTile(stringResource(R.string.house_temperature), "$temperature°", m) }
            add { m -> StatTile(stringResource(R.string.house_humidity), stringResource(R.string.home_humidity, humidity), m) }
            if (air != null) add { m -> StatTile(stringResource(R.string.house_air_short), air, m) }
        }
        TileGrid(columns = adaptiveColumns(tiles.size), tiles = tiles)
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
        ChoiceGroup(
            options = modes.map { (mode, label) -> mode to stringResource(label) },
            selected = state.acMode,
            onSelect = { onIntent(HouseIntent.SelectMode(it)) },
            enabled = state.upperOnline,
        )
        Text(stringResource(R.string.ac_fan_caption), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val fans = listOf(AcFanSpeed.AUTO to R.string.ac_fan_auto, AcFanSpeed.MIN to R.string.ac_fan_min, AcFanSpeed.MED to R.string.ac_fan_med, AcFanSpeed.MAX to R.string.ac_fan_max)
        ChoiceGroup(
            options = fans.map { (fan, label) -> fan to stringResource(label) },
            selected = state.acFanSpeed,
            onSelect = { onIntent(HouseIntent.SelectFanSpeed(it)) },
            enabled = state.upperOnline,
        )
        Text(stringResource(R.string.house_ac_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- previews ------------------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "House", heightDp = 1100)
@androidx.compose.ui.tooling.preview.Preview(name = "House - font 200%", heightDp = 2300, fontScale = 2f)
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
