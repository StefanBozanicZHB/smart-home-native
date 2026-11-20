package zhb.company.smart_home.ui.mqttconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.theme.AppTheme

@Composable
fun MqttConnectScreen(viewModel: MqttConnectViewModel = koinViewModel(), onConnected: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MqttConnectEffect.NavigateToHome -> onConnected()
            }
        }
    }

    MqttConnectContent(state, onRetry = { viewModel.onIntent(MqttConnectIntent.Retry) })
}

@Composable
private fun MqttConnectContent(state: MqttConnectState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val failed = state.isFailed && !state.isConnecting
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (failed) AppTheme.status.dangerContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (failed) Icons.Outlined.CloudOff else Icons.Outlined.HomeWork,
                    contentDescription = null,
                    tint = if (failed) AppTheme.status.onDangerContainer else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(38.dp),
                )
            }
        }
        if (failed) {
            Text(stringResource(R.string.mqtt_connect_failed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.mqtt_connect_failed_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.mqtt_reconnect)) }
        } else {
            CircularProgressIndicator()
            Text(stringResource(R.string.mqtt_connecting), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Connect - failed", heightDp = 700)
@Composable
private fun MqttConnectFailedPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface { MqttConnectContent(MqttConnectState(isConnecting = false, isFailed = true), onRetry = {}) }
    }
}
