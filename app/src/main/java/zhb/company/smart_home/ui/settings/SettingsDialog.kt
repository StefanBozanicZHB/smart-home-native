package zhb.company.smart_home.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R

@Composable
fun SettingsDialog(onDismiss: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        confirmButton = {
            TextButton(onClick = { viewModel.onIntent(SettingsIntent.Save); onDismiss() }) { Text(stringResource(R.string.settings_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zatvori") } },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_dark_theme), modifier = Modifier.weight(1f))
                    Switch(checked = state.darkTheme, onCheckedChange = { viewModel.onIntent(SettingsIntent.ToggleDarkTheme(it)) })
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_sprinkler_auto_enable), modifier = Modifier.weight(1f))
                    Switch(checked = state.sprinklerAuto, onCheckedChange = { viewModel.onIntent(SettingsIntent.ToggleSprinklerAuto(it)) })
                }
                OutlinedTextField(
                    value = state.sprinklerStartTime,
                    onValueChange = { viewModel.onIntent(SettingsIntent.SetStartTime(it)) },
                    label = { Text(stringResource(R.string.settings_sprinkler_start_time)) },
                    singleLine = true,
                )
                Text(text = "${stringResource(R.string.settings_sprinkler_on_duration)}: ${state.sprinklerOnMinutes}")
                Slider(value = state.sprinklerOnMinutes.toFloat(), onValueChange = { viewModel.onIntent(SettingsIntent.SetOnMinutes(it.toInt())) }, valueRange = 1f..15f)
                Text(text = "${stringResource(R.string.settings_sprinkler_off_duration)}: ${state.sprinklerOffSeconds}")
                Slider(value = state.sprinklerOffSeconds.toFloat(), onValueChange = { viewModel.onIntent(SettingsIntent.SetOffSeconds(it.toInt())) }, valueRange = 0f..120f)
                Text(text = "${stringResource(R.string.settings_sprinkler_sets)}: ${state.sprinklerSets}")
                Slider(value = state.sprinklerSets.toFloat(), onValueChange = { viewModel.onIntent(SettingsIntent.SetSets(it.toInt())) }, valueRange = 1f..10f)
            }
        },
    )
}
