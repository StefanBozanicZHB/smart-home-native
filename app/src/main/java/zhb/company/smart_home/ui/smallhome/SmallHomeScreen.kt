package zhb.company.smart_home.ui.smallhome

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.SensorDoor
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.components.CollapsingRoomHeader
import zhb.company.smart_home.ui.components.ToggleTile
import zhb.company.smart_home.ui.components.rememberCompactHeaderHeight
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen

private val SMALL_HOME_HEADER_HEIGHT = 300.dp

@Composable
fun SmallHomeScreen(
    viewModel: SmallHomeViewModel = koinViewModel(),
    onOpenHome: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenHeating: () -> Unit,
    onOpenHouse: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val compactHeaderHeight = rememberCompactHeaderHeight()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding -> SmallHomeContent(state, viewModel, padding, listState, compactHeaderHeight) }

        CollapsingRoomHeader(
            listState = listState,
            expandedHeight = SMALL_HOME_HEADER_HEIGHT,
            imageRes = R.drawable.img_shed,
            title = stringResource(R.string.menu_house),
            online = state.online,
            stats = emptyList(),
            onBack = onOpenHome,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun SmallHomeContent(
    state: SmallHomeState,
    viewModel: SmallHomeViewModel,
    padding: PaddingValues,
    listState: LazyListState,
    compactHeaderHeight: Dp,
) {
    LazyColumn(
        state = listState,
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        modifier = Modifier.fillMaxSize().padding(padding).padding(top = compactHeaderHeight),
    ) {
        item { Spacer(modifier = Modifier.height((SMALL_HOME_HEADER_HEIGHT - compactHeaderHeight).coerceAtLeast(0.dp))) }

        item {
            SectionLabel(stringResource(R.string.garden_devices_section))
            Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleTile(
                        label = stringResource(R.string.small_home_door),
                        icon = Icons.Filled.SensorDoor,
                        isOn = state.doorOn,
                        onToggle = { viewModel.onIntent(SmallHomeIntent.ToggleDoor(it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToggleTile(
                        label = stringResource(R.string.small_home_bulb_1),
                        icon = Icons.Filled.Lightbulb,
                        isOn = state.bulb1,
                        onToggle = { viewModel.onIntent(SmallHomeIntent.ToggleBulb1(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleTile(
                        label = stringResource(R.string.small_home_bulb_2),
                        icon = Icons.Filled.Lightbulb,
                        isOn = state.bulb2,
                        onToggle = { viewModel.onIntent(SmallHomeIntent.ToggleBulb2(it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToggleTile(
                        label = stringResource(R.string.small_home_bulb_3),
                        icon = Icons.Filled.Lightbulb,
                        isOn = state.bulb3,
                        onToggle = { viewModel.onIntent(SmallHomeIntent.ToggleBulb3(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { FingerprintCard(state, viewModel) }
        item { LogCard(state.fingerLog) }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun FingerprintCard(state: SmallHomeState, viewModel: SmallHomeViewModel) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.small_home_fingerprint_section))
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.addIdInput,
                    onValueChange = { viewModel.onIntent(SmallHomeIntent.AddIdInputChanged(it)) },
                    label = { Text(stringResource(R.string.small_home_add_id)) },
                    leadingIcon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { viewModel.onIntent(SmallHomeIntent.AddFingerprintId) },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EnableGreen.copy(alpha = 0.14f), contentColor = EnableGreen),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.small_home_add), modifier = Modifier.padding(start = 6.dp))
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = state.deleteIdInput,
                    onValueChange = { viewModel.onIntent(SmallHomeIntent.DeleteIdInputChanged(it)) },
                    label = { Text(stringResource(R.string.small_home_delete_id)) },
                    leadingIcon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.onIntent(SmallHomeIntent.DeleteFingerprintId) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DisableRed),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.small_home_delete), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.small_home_log))
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(16.dp)) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp),
                )
                Box(modifier = Modifier.padding(start = 10.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    if (log.isBlank()) {
                        Text(
                            text = stringResource(R.string.small_home_log_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = log,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
