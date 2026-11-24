package zhb.company.smart_home.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import zhb.company.smart_home.data.notifications.QuietHours

/** System settings (opened from the system tab): theme, notifications, quiet hours. Applied right away. */
@Composable
fun SystemSettingsDialog(onDismiss: () -> Unit, onSignedOut: () -> Unit = {}, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val quiet by viewModel.quietHours.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut(onDone = onSignedOut)
                }) { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.settings_sign_out_cancel)) } },
        )
        return
    }

    AlertDialog(
        dismissButton = { TextButton(onClick = { confirmSignOut = true }) { Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error) } },
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(stringResource(R.string.settings_dark_theme), state.darkTheme) {
                    viewModel.onIntent(SettingsIntent.ToggleDarkTheme(it))
                }
                NotificationsSetting(
                    enabled = state.notifications,
                    onToggle = { viewModel.onIntent(SettingsIntent.ToggleNotifications(it)) },
                )
                if (state.notifications) quiet?.let { q -> QuietHoursSetting(q) { viewModel.onIntent(it) } }
            }
        },
    )
}

/** A label and a switch on one line, the label vertically centered on the switch. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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

    SwitchRow(stringResource(R.string.settings_notifications), enabled) {
        onToggle(it)
        if (it && !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

/** Quiet hours: switch plus from/to times, each saved as soon as it is a valid HH:MM. */
@Composable
private fun QuietHoursSetting(quiet: QuietHours, onIntent: (SettingsIntent) -> Unit) {
    fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
    var start by remember(quiet.startMinute) { mutableStateOf(hhmm(quiet.startMinute)) }
    var end by remember(quiet.endMinute) { mutableStateOf(hhmm(quiet.endMinute)) }
    fun save(enabled: Boolean = quiet.enabled, s: String = start, e: String = end) {
        if (QuietHours.parseTime(s) != null && QuietHours.parseTime(e) != null) {
            onIntent(SettingsIntent.SetQuietHours(enabled, s, e))
        }
    }
    SwitchRow(stringResource(R.string.settings_quiet_hours), quiet.enabled) { save(enabled = it) }
    if (quiet.enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = start,
                onValueChange = { start = it; save(s = it) },
                label = { Text(stringResource(R.string.settings_quiet_from)) },
                isError = QuietHours.parseTime(start) == null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = end,
                onValueChange = { end = it; save(e = it) },
                label = { Text(stringResource(R.string.settings_quiet_to)) },
                isError = QuietHours.parseTime(end) == null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.settings_quiet_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
