package zhb.company.smart_home.ui.heating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.domain.heating.BusSensor
import zhb.company.smart_home.domain.heating.Pipe
import zhb.company.smart_home.domain.heating.PipeCalibration
import zhb.company.smart_home.domain.heating.PipeSensorRules
import zhb.company.smart_home.ui.components.AlertBanner
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.format.Readings

@Composable
fun PipeCalibrationScreen(onDone: () -> Unit, viewModel: PipeCalibrationViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.done) { if (state.done) onDone() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.onIntent(PipeCalibrationIntent.MessageShown)
        if (message is HeatingMessage.Saved) return@LaunchedEffect // the sensors screen shows the new offsets
        snackbar.showSnackbar(
            when (message) {
                is HeatingMessage.Rejected -> context.getString(R.string.heating_msg_rejected, message.error)
                HeatingMessage.Timeout -> context.getString(R.string.heating_msg_timeout)
                else -> context.getString(R.string.heating_msg_not_connected)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PipeCalibrationContent(state, viewModel::onIntent, onBack = onDone)
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PipeCalibrationContent(state: PipeCalibrationState, onIntent: (PipeCalibrationIntent) -> Unit, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back)) }
                Text(stringResource(R.string.calibration_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            DashboardCard {
                CardHeader(Icons.Outlined.Science, stringResource(R.string.calibration_title))
                Text(stringResource(R.string.calibration_intro), style = MaterialTheme.typography.bodyMedium)
                if (state.phase == CalibrationPhase.INTRO) {
                    Button(onClick = { onIntent(PipeCalibrationIntent.Start) }) { Text(stringResource(R.string.calibration_start)) }
                }
            }
        }
        if (state.phase == CalibrationPhase.SAMPLING) {
            item {
                DashboardCard {
                    Text(stringResource(R.string.calibration_sampling, state.sample + 1, state.sampleCount), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { state.sample / state.sampleCount.toFloat() }, modifier = Modifier.fillMaxWidth())
                    state.bus.forEach { BusReading(it) }
                }
            }
        }
        if (state.phase == CalibrationPhase.RESULT) {
            item { ResultCard(state, onIntent) }
        }
    }
}

@Composable
private fun BusReading(sensor: BusSensor) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(sensor.address, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text("${Readings.temperature(sensor.rawTemp)}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ResultCard(state: PipeCalibrationState, onIntent: (PipeCalibrationIntent) -> Unit) {
    DashboardCard {
        when (val result = state.result) {
            is PipeCalibration.Result.Proposal -> {
                Text(stringResource(R.string.calibration_reference, String.format(Locale.US, "%.2f", result.reference)), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.calibration_spread, String.format(Locale.US, "%.2f", result.spread)), style = MaterialTheme.typography.bodyMedium)
                if (result.spreadTooLarge) AlertBanner(Icons.Outlined.WarningAmber, stringResource(R.string.calibration_spread_warning), Tone.WARNING)
                if (result.rows.isEmpty()) {
                    Text(stringResource(R.string.calibration_no_rows), style = MaterialTheme.typography.bodyMedium)
                }
                result.rows.forEach { row ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("${pipeName(row.pipe)} · ${PipeSensorRules.shortAddress(row.address)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                R.string.calibration_row,
                                String.format(Locale.US, "%.2f", row.meanRaw),
                                PipeSensorRules.formatOffset(row.currentOffsetHundredths),
                                PipeSensorRules.formatOffset(row.proposedOffsetHundredths),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (row.clamped) Text(stringResource(R.string.calibration_clamped), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onIntent(PipeCalibrationIntent.Send) }, enabled = !state.sending && result.rows.isNotEmpty()) {
                        Text(stringResource(R.string.calibration_send))
                    }
                    OutlinedButton(onClick = { onIntent(PipeCalibrationIntent.Start) }, enabled = !state.sending) {
                        Text(stringResource(R.string.calibration_again))
                    }
                }
                if (state.sending) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            else -> {
                Text(stringResource(R.string.calibration_too_few), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onIntent(PipeCalibrationIntent.Start) }) { Text(stringResource(R.string.calibration_again)) }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Calibration - proposal", heightDp = 1100)
@androidx.compose.ui.tooling.preview.Preview(name = "Calibration - font 200%", heightDp = 2200, fontScale = 2f)
@Composable
private fun PipeCalibrationPreview() {
    val result = PipeCalibration.Result.Proposal(
        reference = 20.03,
        spread = 0.30,
        rows = listOf(
            PipeCalibration.Row(Pipe.SUPPLY, "28FF4C1D6116038F", 20.20, -50, -17, clamped = false),
            PipeCalibration.Row(Pipe.RETURN, "28AA01020304A169", 19.90, 0, 13, clamped = false),
        ),
    )
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { PipeCalibrationContent(PipeCalibrationState(phase = CalibrationPhase.RESULT, result = result), onIntent = {}, onBack = {}) }
    }
}
