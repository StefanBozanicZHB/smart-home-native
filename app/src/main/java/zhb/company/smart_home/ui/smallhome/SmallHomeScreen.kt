package zhb.company.smart_home.ui.smallhome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.SensorDoor
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.DeviceTile
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.TileGrid
import zhb.company.smart_home.ui.components.adaptiveColumns

/** Shed (node7). The node is not in use (smart-home-mcu docs/nodes.md); the screen stays for when it returns. */
@Composable
fun SmallHomeScreen(onBack: () -> Unit, viewModel: SmallHomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SmallHomeContent(state, viewModel::onIntent, onBack)
}

@Composable
private fun SmallHomeContent(state: SmallHomeState, onIntent: (SmallHomeIntent) -> Unit, onBack: () -> Unit = {}) {
    val offline = stringResource(R.string.home_node_offline_tile)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.menu_house), horizontalPadding = 0.dp, onBack = onBack) }
        if (!state.online) item { AlertBanner(Icons.Outlined.Info, stringResource(R.string.small_home_not_in_use), Tone.NEUTRAL) }
        item {
            TileGrid(
                columns = adaptiveColumns(2),
                tiles = listOf(
                    { m -> DeviceTile(Icons.Outlined.SensorDoor, stringResource(R.string.small_home_door), state.doorOn, { onIntent(SmallHomeIntent.ToggleDoor(it)) }, m, enabled = state.online, disabledReason = offline) },
                    { m -> DeviceTile(Icons.Outlined.Lightbulb, stringResource(R.string.small_home_bulb_1), state.bulb1, { onIntent(SmallHomeIntent.ToggleBulb1(it)) }, m, enabled = state.online, disabledReason = offline) },
                    { m -> DeviceTile(Icons.Outlined.Lightbulb, stringResource(R.string.small_home_bulb_2), state.bulb2, { onIntent(SmallHomeIntent.ToggleBulb2(it)) }, m, enabled = state.online, disabledReason = offline) },
                    { m -> DeviceTile(Icons.Outlined.Lightbulb, stringResource(R.string.small_home_bulb_3), state.bulb3, { onIntent(SmallHomeIntent.ToggleBulb3(it)) }, m, enabled = state.online, disabledReason = offline) },
                ),
            )
        }
        item {
            DashboardCard {
                CardHeader(Icons.Outlined.Fingerprint, stringResource(R.string.small_home_fingerprint_section))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.addIdInput,
                        onValueChange = { onIntent(SmallHomeIntent.AddIdInputChanged(it)) },
                        label = { Text(stringResource(R.string.small_home_add_id)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = { onIntent(SmallHomeIntent.AddFingerprintId) }, enabled = state.online) {
                        Text(stringResource(R.string.small_home_add))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.deleteIdInput,
                        onValueChange = { onIntent(SmallHomeIntent.DeleteIdInputChanged(it)) },
                        label = { Text(stringResource(R.string.small_home_delete_id)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { onIntent(SmallHomeIntent.DeleteFingerprintId) }, enabled = state.online) {
                        Text(stringResource(R.string.small_home_delete))
                    }
                }
            }
        }
        item {
            DashboardCard {
                CardHeader(Icons.Outlined.History, stringResource(R.string.small_home_log))
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = state.fingerLog.ifBlank { stringResource(R.string.small_home_log_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = if (state.fingerLog.isBlank()) null else FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Shed", heightDp = 900)
@Composable
private fun SmallHomePreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { SmallHomeContent(SmallHomeState(online = false), onIntent = {}) }
    }
}
