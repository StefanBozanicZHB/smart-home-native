package zhb.company.smart_home.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import zhb.company.smart_home.R
import zhb.company.smart_home.data.notifications.PushNotifications

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
                NotificationsSetting(
                    enabled = state.notifications,
                    onToggle = { viewModel.onIntent(SettingsIntent.ToggleNotifications(it)) },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_node5_test_board), modifier = Modifier.weight(1f))
                    Switch(checked = state.node5TestBoard, onCheckedChange = { viewModel.onIntent(SettingsIntent.ToggleNode5TestBoard(it)) })
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

/** On/off switch for push notifications; asks for the Android 13+ permission when turned on. */
@Composable
private fun NotificationsSetting(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    push: PushNotifications = koinInject(),
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(push.hasPermission()) }
    // Re-check after returning from the system settings screen.
    LifecycleResumeEffect(Unit) {
        granted = push.hasPermission()
        onPauseOrDispose {}
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_notifications), modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = {
                onToggle(it)
                if (it && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
    if (enabled && !granted) {
        Text(
            stringResource(R.string.settings_notifications_no_permission),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }) { Text(stringResource(R.string.settings_notifications_open_system)) }
    }
}
