package zhb.company.smart_home.ui.mqttconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R

@Composable
fun MqttConnectScreen(viewModel: MqttConnectViewModel = koinViewModel(), onConnected: () -> Unit) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MqttConnectEffect.NavigateToHome -> onConnected()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.isConnecting -> {
                CircularProgressIndicator()
                Text(text = stringResource(R.string.mqtt_connecting), modifier = Modifier.padding(top = 16.dp))
            }
            state.isFailed -> {
                Text(text = stringResource(R.string.mqtt_connect_failed))
                Button(
                    onClick = { viewModel.onIntent(MqttConnectIntent.Retry) },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(text = stringResource(R.string.mqtt_reconnect))
                }
            }
        }
    }
}
