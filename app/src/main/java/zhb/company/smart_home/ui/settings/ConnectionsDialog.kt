package zhb.company.smart_home.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen

@Composable
fun ConnectionsDialog(onDismiss: () -> Unit, viewModel: ConnectionsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.connections_title)) },
        text = {
            Column {
                state.nodeOnline.toSortedMap().forEach { (node, online) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = viewModel.labelFor(node))
                        Icon(
                            imageVector = if (online) Icons.Filled.CheckCircle else Icons.Filled.Error,
                            contentDescription = if (online) stringResource(R.string.connections_online) else stringResource(R.string.connections_offline),
                            tint = if (online) EnableGreen else DisableRed,
                        )
                    }
                }
            }
        },
    )
}
