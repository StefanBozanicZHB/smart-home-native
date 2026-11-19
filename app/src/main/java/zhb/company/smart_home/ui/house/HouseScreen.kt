package zhb.company.smart_home.ui.house

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Co2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode
import zhb.company.smart_home.ui.components.CollapsingRoomHeader
import zhb.company.smart_home.ui.components.PulsingStatusDot
import zhb.company.smart_home.ui.components.SevenSegmentNumber
import zhb.company.smart_home.ui.components.rememberCompactHeaderHeight
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

private val HOUSE_HEADER_HEIGHT = 300.dp

@Composable
fun HouseScreen(
    viewModel: HouseViewModel = koinViewModel(),
    onOpenHome: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenHeating: () -> Unit,
    onOpenShed: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val compactHeaderHeight = rememberCompactHeaderHeight()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding -> HouseContent(state, viewModel, padding, listState, compactHeaderHeight) }

        CollapsingRoomHeader(
            listState = listState,
            expandedHeight = HOUSE_HEADER_HEIGHT,
            imageRes = R.drawable.img_living_room_up,
            title = stringResource(R.string.menu_house_combined),
            online = state.online,
            stats = emptyList(),
            showStatusPill = false,
            onBack = onOpenHome,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun HouseContent(
    state: HouseState,
    viewModel: HouseViewModel,
    padding: PaddingValues,
    listState: LazyListState,
    compactHeaderHeight: Dp,
) {
    LazyColumn(
        state = listState,
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        modifier = Modifier.fillMaxSize().padding(padding).padding(top = compactHeaderHeight),
    ) {
        item { Spacer(modifier = Modifier.height((HOUSE_HEADER_HEIGHT - compactHeaderHeight).coerceAtLeast(0.dp))) }

        item {
            SectionLabel(stringResource(R.string.menu_first_floor), online = state.groundOnline)
            InlineReadout(temperature = state.groundTemperature, humidity = state.groundHumidity)
        }

        item {
            SectionLabel(stringResource(R.string.menu_second_floor), online = state.upperOnline)
            InlineReadout(temperature = state.upperTemperature, humidity = state.upperHumidity, co2 = state.co2)
            AcCard(state = state, viewModel = viewModel)
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String, online: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(8.dp))
        PulsingStatusDot(online = online)
        Text(
            text = if (online) stringResource(R.string.connections_online) else stringResource(R.string.connections_offline),
            style = MaterialTheme.typography.labelSmall,
            color = if (online) EnableGreen else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun InlineReadout(temperature: String, humidity: String, co2: String? = null) {
    Row(
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReadoutItem(Icons.Filled.Thermostat, "$temperature°C")
        ReadoutItem(Icons.Filled.WaterDrop, "$humidity%")
        if (co2 != null) ReadoutItem(Icons.Filled.Co2, co2)
    }
}

@Composable
private fun ReadoutItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun AcCard(state: HouseState, viewModel: HouseViewModel) {
    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.acOn) PrimaryColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header: AC identity + a single clear power switch (replacing two separate power buttons).
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = CircleShape, color = PrimaryColor.copy(alpha = 0.14f)) {
                    Icon(Icons.Filled.AcUnit, contentDescription = null, tint = PrimaryColor, modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(stringResource(R.string.ac_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (state.acOn) stringResource(R.string.on) else stringResource(R.string.off),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.acOn) EnableGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.acOn,
                    onCheckedChange = { on -> viewModel.onIntent(if (on) HouseIntent.PowerOn else HouseIntent.PowerOff) },
                    colors = SwitchDefaults.colors(checkedTrackColor = PrimaryColor),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Temperature stepper: a soft rounded "dial" with the 7-seg readout, flanked by tonal +/- buttons.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { viewModel.onIntent(HouseIntent.DecreaseTemp) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.ac_temp_decrease))
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        SevenSegmentNumber(value = state.acTemp, color = PrimaryColor)
                    }
                }
                FilledTonalIconButton(onClick = { viewModel.onIntent(HouseIntent.IncreaseTemp) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ac_temp_increase))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            AcOptionCaption(stringResource(R.string.ac_mode_caption))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AcOptionTile(Icons.Filled.AcUnit, stringResource(R.string.ac_mode_cool), state.acMode == AcMode.COOL, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectMode(AcMode.COOL)) }
                AcOptionTile(Icons.Filled.Autorenew, stringResource(R.string.ac_mode_auto), state.acMode == AcMode.AUTO, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectMode(AcMode.AUTO)) }
                AcOptionTile(Icons.Filled.Air, stringResource(R.string.ac_mode_fan), state.acMode == AcMode.FAN, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectMode(AcMode.FAN)) }
                AcOptionTile(Icons.Filled.Whatshot, stringResource(R.string.ac_mode_heat), state.acMode == AcMode.HEAT, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectMode(AcMode.HEAT)) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AcOptionCaption(stringResource(R.string.ac_fan_caption))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AcOptionTile(Icons.Filled.Autorenew, stringResource(R.string.ac_fan_auto), state.acFanSpeed == AcFanSpeed.AUTO, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectFanSpeed(AcFanSpeed.AUTO)) }
                AcOptionTile(Icons.Filled.Air, stringResource(R.string.ac_fan_min), state.acFanSpeed == AcFanSpeed.MIN, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectFanSpeed(AcFanSpeed.MIN)) }
                AcOptionTile(Icons.Filled.Air, stringResource(R.string.ac_fan_med), state.acFanSpeed == AcFanSpeed.MED, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectFanSpeed(AcFanSpeed.MED)) }
                AcOptionTile(Icons.Filled.Air, stringResource(R.string.ac_fan_max), state.acFanSpeed == AcFanSpeed.MAX, Modifier.weight(1f)) { viewModel.onIntent(HouseIntent.SelectFanSpeed(AcFanSpeed.MAX)) }
            }
        }
    }
}

@Composable
private fun AcOptionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AcOptionTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) PrimaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
