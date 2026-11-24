package zhb.company.smart_home.ui.heating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.heating.Pipe
import zhb.company.smart_home.domain.heating.PipeSensorRules
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.SectionLabel
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.animatedItem

/** While the screen is visible the node is asked for fresh readings this often. */
private const val AUTO_REFRESH_MS = 10_000L

@Composable
fun PipeSensorsScreen(onBack: () -> Unit, onOpenCalibration: () -> Unit, viewModel: PipeSensorsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // config/get republishes node5/sensors with fresh raw readings; only while the screen is shown.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.onIntent(PipeSensorsIntent.Refresh)
                delay(AUTO_REFRESH_MS)
            }
        }
    }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.onIntent(PipeSensorsIntent.MessageShown)
        snackbar.showSnackbar(
            when (message) {
                HeatingMessage.Saved -> resources.getString(R.string.heating_msg_saved)
                is HeatingMessage.Rejected -> resources.getString(R.string.heating_msg_rejected, message.error)
                HeatingMessage.Timeout -> resources.getString(R.string.heating_msg_timeout)
                HeatingMessage.NotConnected -> resources.getString(R.string.heating_msg_not_connected)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PipeSensorsContent(state, viewModel::onIntent, onBack, onOpenCalibration)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
    PipeSensorsDialogs(state, viewModel::onIntent)
}

@Composable
private fun PipeSensorsContent(state: PipeSensorsState, onIntent: (PipeSensorsIntent) -> Unit, onBack: () -> Unit, onOpenCalibration: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        animatedItem("sensors-1") {
            ScreenHeader(title = stringResource(R.string.sensors_title), horizontalPadding = 0.dp, onBack = onBack) {
                if (state.slow) {
                    // In place of the refresh button, so nothing on the screen moves.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                } else {
                    IconButton(onClick = { onIntent(PipeSensorsIntent.Refresh) }, enabled = state.connected) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.sensors_refresh))
                    }
                }
            }
        }
        if (!state.connected) animatedItem("sensors-2") { AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.heating_not_connected), Tone.DANGER) }
        if (state.loading) {
            animatedItem("sensors-3") { AlertBanner(Icons.Outlined.HourglassEmpty, stringResource(R.string.sensors_waiting, state.nodeId), Tone.NEUTRAL) }
            return@LazyColumn
        }
        if (state.fallback) animatedItem("sensors-4") { AlertBanner(Icons.Outlined.Info, stringResource(R.string.sensors_fallback), Tone.WARNING) }
        animatedItem("sensors-5") {
            Text(stringResource(R.string.sensors_identify_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        animatedItem("sensors-6") { SectionLabel(stringResource(R.string.sensors_pipes)) }
        state.pipes.forEach { row -> animatedItem(row.pipe.key) { PipeCard(row, state, onIntent) } }
        animatedItem("sensors-7") {
            SectionLabel(stringResource(R.string.sensors_bus)) {
                TextButton(onClick = onOpenCalibration, enabled = state.connected && state.pipes.any { it.address != null }) {
                    Text(stringResource(R.string.sensors_calibrate))
                }
            }
        }
        animatedItem("sensors-8") { BusCard(state, onIntent) }
    }
}

@Composable
internal fun pipeName(pipe: Pipe): String = stringResource(
    when (pipe) {
        Pipe.SUPPLY -> R.string.sensors_pipe_supply
        Pipe.RETURN -> R.string.sensors_pipe_return
        Pipe.WELL_IN -> R.string.sensors_pipe_well_in
        Pipe.WELL_OUT -> R.string.sensors_pipe_well_out
    },
)

@Composable
private fun PipeCard(row: PipeRowUi, state: PipeSensorsState, onIntent: (PipeSensorsIntent) -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.Thermostat, pipeName(row.pipe)) {
            Text(row.temperature?.let { "$it°" } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        if (row.address == null) {
            Text(stringResource(R.string.sensors_no_sensor), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DashboardCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onIntent(PipeSensorsIntent.ShowAddress(row.address)) }) {
                Text(PipeSensorRules.shortAddress(row.address), fontFamily = FontFamily.Monospace)
            }
            Text(
                listOfNotNull(
                    stringResource(R.string.sensors_offset, PipeSensorRules.formatOffset(row.offsetHundredths)),
                    row.rawTemperature?.let { stringResource(R.string.sensors_raw, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (row.missingOnBus) AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.sensors_missing), Tone.DANGER)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onIntent(PipeSensorsIntent.EditOffset(row.pipe)) }, enabled = state.connected) {
                Text(stringResource(R.string.sensors_edit_offset))
            }
            TextButton(onClick = { onIntent(PipeSensorsIntent.RequestRemove(row.pipe)) }, enabled = state.connected) {
                Text(stringResource(R.string.sensors_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Every sensor on the bus, warmest first; unassigned ones are highlighted. Tap to assign. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BusCard(state: PipeSensorsState, onIntent: (PipeSensorsIntent) -> Unit) {
    DashboardCard {
        if (state.bus.isEmpty()) {
            Text(stringResource(R.string.sensors_bus_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.bus.forEachIndexed { i, sensor ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(
                onClick = { onIntent(PipeSensorsIntent.PickSensor(sensor.address)) },
                enabled = state.connected,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sensor.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        // Wraps with a large font instead of cutting the chips.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            if (sensor.pipe != null) StatusChip(pipeName(sensor.pipe), Tone.ACTIVE) else StatusChip(stringResource(R.string.sensors_unassigned), Tone.WARNING)
                            if (sensor.warmest) StatusChip(stringResource(R.string.sensors_warmest), Tone.NEUTRAL)
                        }
                    }
                    Text("${sensor.rawTemperature}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PipeSensorsDialogs(state: PipeSensorsState, onIntent: (PipeSensorsIntent) -> Unit) {
    state.pickingFor?.let { address ->
        val currentPipe = state.bus.firstOrNull { it.address == address }?.pipe
        AlertDialog(
            onDismissRequest = { onIntent(PipeSensorsIntent.DismissPicker) },
            title = { Text(stringResource(R.string.sensors_assign_title, PipeSensorRules.shortAddress(address))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (currentPipe != null) {
                        Text(stringResource(R.string.sensors_assign_moves, pipeName(currentPipe)), style = MaterialTheme.typography.bodySmall)
                    }
                    Pipe.entries.forEach { pipe ->
                        val holder = state.pipes.first { it.pipe == pipe }.address
                        TextButton(onClick = { onIntent(PipeSensorsIntent.AssignTo(pipe)) }, enabled = pipe != currentPipe, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                listOfNotNull(pipeName(pipe), holder?.takeIf { it != address }?.let(PipeSensorRules::shortAddress)).joinToString(" · "),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { onIntent(PipeSensorsIntent.DismissPicker) }) { Text(stringResource(R.string.sensors_cancel)) } },
        )
    }
    state.editingOffset?.let { pipe ->
        val row = state.pipes.first { it.pipe == pipe }
        var text by remember(pipe) { mutableStateOf(PipeSensorRules.formatOffset(row.offsetHundredths)) }
        AlertDialog(
            onDismissRequest = { onIntent(PipeSensorsIntent.DismissOffset) },
            title = { Text(stringResource(R.string.sensors_offset_title, pipeName(pipe))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sensors_offset_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        suffix = { Text("°C") },
                        isError = state.offsetError,
                        supportingText = if (state.offsetError) ({ Text(stringResource(R.string.sensors_offset_error)) }) else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onIntent(PipeSensorsIntent.SaveOffset(text)) }) { Text(stringResource(R.string.sensors_save)) } },
            dismissButton = { TextButton(onClick = { onIntent(PipeSensorsIntent.DismissOffset) }) { Text(stringResource(R.string.sensors_cancel)) } },
        )
    }
    state.confirmRemove?.let { pipe ->
        AlertDialog(
            onDismissRequest = { onIntent(PipeSensorsIntent.DismissRemove) },
            title = { Text(stringResource(R.string.sensors_remove_title, pipeName(pipe))) },
            text = { Text(stringResource(R.string.sensors_remove_text)) },
            confirmButton = {
                TextButton(onClick = { onIntent(PipeSensorsIntent.ConfirmRemove) }) {
                    Text(stringResource(R.string.sensors_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { onIntent(PipeSensorsIntent.DismissRemove) }) { Text(stringResource(R.string.sensors_cancel)) } },
        )
    }
    state.addressDetail?.let { address ->
        AlertDialog(
            onDismissRequest = { onIntent(PipeSensorsIntent.DismissAddress) },
            title = { Text(stringResource(R.string.sensors_address_title)) },
            text = { SelectionContainer { Text(address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) } },
            confirmButton = { TextButton(onClick = { onIntent(PipeSensorsIntent.DismissAddress) }) { Text(stringResource(R.string.sensors_close)) } },
        )
    }
}

// ---- previews ------------------------------------------------------------------------------------

private val PreviewSensors = PipeSensorsState(
    nodeId = "node5", connected = true, loading = false,
    pipes = listOf(
        PipeRowUi(Pipe.SUPPLY, "28FF4C1D6116038F", -12, temperature = "34.3", rawTemperature = "34.4"),
        PipeRowUi(Pipe.RETURN, "28AA01020304A169", 0, temperature = null, missingOnBus = true),
        PipeRowUi(Pipe.WELL_IN),
        PipeRowUi(Pipe.WELL_OUT),
    ),
    bus = listOf(
        BusRowUi("28FF4C1D6116038F", "34.4", Pipe.SUPPLY, warmest = true),
        BusRowUi("28B1C2D3E4F50617", "21.1", null),
    ),
)

@androidx.compose.ui.tooling.preview.Preview(name = "Pipe sensors", heightDp = 1500)
@androidx.compose.ui.tooling.preview.Preview(name = "Pipe sensors - font 200%", heightDp = 3000, fontScale = 2f)
@Composable
private fun PipeSensorsPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { PipeSensorsContent(PreviewSensors, onIntent = {}, onBack = {}, onOpenCalibration = {}) }
    }
}
