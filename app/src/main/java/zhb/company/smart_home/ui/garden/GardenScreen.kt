package zhb.company.smart_home.ui.garden

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Light
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbIncandescent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.components.BigValue
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.DeviceTile
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.SectionLabel
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.TileShape
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.settings.SettingsDialog
import zhb.company.smart_home.ui.theme.AppTheme

@Composable
fun GardenScreen(viewModel: GardenViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    GardenContent(state, viewModel::onIntent, onOpenSettings = { showSettings = true })
    if (state.showLocationDialog) {
        LocationDialog(activeIndex = state.sprinklerActiveIndex, onDismiss = { viewModel.onIntent(GardenIntent.DismissLocationDialog) })
    }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
}

@Composable
private fun GardenContent(state: GardenState, onIntent: (GardenIntent) -> Unit, onOpenSettings: () -> Unit) {
    val offline = stringResource(R.string.home_node_offline_tile)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.menu_garden),
                subtitle = "${state.temperature}° · ${stringResource(R.string.home_humidity, state.humidity)}",
                horizontalPadding = 0.dp,
            )
        }
        item { SprinklerCard(state, onIntent, onOpenSettings) }
        item { SoilMoistureCard() }
        item { SectionLabel(stringResource(R.string.garden_fountain_and_lights)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceTile(
                    Icons.Outlined.Water, stringResource(R.string.garden_fountain_pump), state.fountainPumpOn,
                    { onIntent(GardenIntent.ToggleFountainPump(it)) }, Modifier.weight(1f),
                    enabled = state.fountainOnline, disabledReason = offline,
                )
                DeviceTile(
                    Icons.Outlined.Light, stringResource(R.string.garden_fountain_light), state.fountainLightOn,
                    { onIntent(GardenIntent.ToggleFountainLight(it)) }, Modifier.weight(1f),
                    enabled = state.fountainOnline, disabledReason = offline,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceTile(
                    Icons.Outlined.Lightbulb, stringResource(R.string.garden_led_strip), state.ledStripOn,
                    { onIntent(GardenIntent.ToggleLedStrip(it)) }, Modifier.weight(1f),
                    enabled = state.yardOnline, disabledReason = offline,
                )
                DeviceTile(
                    Icons.Outlined.WbIncandescent, stringResource(R.string.garden_bulb), state.bulbOn,
                    { onIntent(GardenIntent.ToggleBulb(it)) }, Modifier.weight(1f),
                    enabled = state.yardOnline, disabledReason = offline,
                )
            }
        }
        item { PoolCard(state, onIntent) }
    }
}

/** Six zones in a row; the running one is highlighted. Tapping a zone switches it (one zone at a time). */
@Composable
private fun SprinklerCard(state: GardenState, onIntent: (GardenIntent) -> Unit, onOpenSettings: () -> Unit) {
    val running = state.sprinklerActiveIndex.takeIf { it in 1..6 }
    DashboardCard {
        CardHeader(Icons.Outlined.WaterDrop, stringResource(R.string.garden_sprinklers)) {
            when {
                running != null -> StatusChip(stringResource(R.string.home_sprinklers_running, running), Tone.ACTIVE)
                state.sprinklerAuto -> StatusChip(stringResource(R.string.home_sprinklers_auto), Tone.ACTIVE)
                else -> StatusChip(stringResource(R.string.home_sprinklers_manual), Tone.NEUTRAL)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.sprinklers.forEachIndexed { i, on ->
                val zone = i + 1
                val active = on || zone == running
                val description = stringResource(R.string.garden_zone_desc, zone)
                Surface(
                    onClick = { onIntent(GardenIntent.ToggleSprinkler(zone, !active)) },
                    enabled = state.sprinklerTimerOnline,
                    shape = TileShape,
                    color = if (active) AppTheme.status.activeContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f).height(44.dp).semantics {
                        contentDescription = description
                        stateDescription = if (active) "uključeno" else "isključeno"
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "$zone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) AppTheme.status.onActiveContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (running != null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.sprinklerAuto) {
                    stringResource(R.string.garden_plan_auto, state.sprinklerStartTime, state.sprinklerOnMinutes, state.sprinklerSets)
                } else {
                    stringResource(R.string.garden_plan_manual)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onIntent(GardenIntent.OpenLocationDialog) }) { Text(stringResource(R.string.garden_map)) }
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.garden_settings)) }
        }
    }
}

/** Placeholder until the soil moisture sensor exists (planned next year). */
@Composable
private fun SoilMoistureCard() {
    DashboardCard {
        CardHeader(Icons.Outlined.Grass, stringResource(R.string.garden_soil)) {
            StatusChip(stringResource(R.string.garden_soon), Tone.NEUTRAL)
        }
        Text(stringResource(R.string.garden_soil_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PoolCard(state: GardenState, onIntent: (GardenIntent) -> Unit) {
    DashboardCard {
        CardHeader(Icons.Outlined.Pool, stringResource(R.string.garden_pool)) {
            if (!state.poolOnline) StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
        }
        BigValue("${state.poolTemperature}°")
        SwitchRow(stringResource(R.string.garden_pool_pump), state.poolPumpOn, state.poolOnline) { onIntent(GardenIntent.TogglePoolPump(it)) }
        SwitchRow(stringResource(R.string.garden_pool_heating), state.poolHeatingOn, state.poolOnline) { onIntent(GardenIntent.TogglePoolHeating(it)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * Normalized (bias, from -1f..1f on each axis) positions of the 6 sprinkler zones over the
 * yard/house diagram (`img_nodes_diagram`), picked to sit on the green lawn areas of that
 * 392x381 image: a front strip along the top, a side strip along the left, and the back-yard
 * patch at the bottom (avoiding the house block and the two existing pool/fountain markers).
 */
private val sprinklerMapPositions = listOf(
    BiasAlignment(-0.69f, -0.90f), // 1 - front yard, left
    BiasAlignment(0.17f, -0.90f), // 2 - front yard, right
    BiasAlignment(-0.75f, -0.48f), // 3 - side yard, upper
    BiasAlignment(-0.75f, 0.15f), // 4 - side yard, lower
    BiasAlignment(-0.50f, 0.70f), // 5 - back yard, left
    BiasAlignment(0.10f, 0.70f), // 6 - back yard, right
)

@Composable
private fun LocationDialog(activeIndex: Int, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pinPulse")
    val pinPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pinPulseScale",
    )
    val pinPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pinPulseAlpha",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.garden_sprinkler_location)) },
        text = {
            Column {
                Text(
                    text = if (activeIndex != -1) "${stringResource(R.string.garden_sprinkler_active)} $activeIndex" else stringResource(R.string.garden_sprinkler_none_active),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(392f / 381f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_nodes_diagram),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    sprinklerMapPositions.forEachIndexed { i, bias ->
                        val number = i + 1
                        val isActive = number == activeIndex
                        Box(modifier = Modifier.align(bias)) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .align(Alignment.Center)
                                        .graphicsLayer { scaleX = pinPulse; scaleY = pinPulse; alpha = pinPulseAlpha }
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                border = if (isActive) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(if (isActive) 30.dp else 22.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "$number",
                                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = if (isActive) 14.sp else 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

// ---- previews ------------------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "Garden - summer evening", heightDp = 1100)
@Composable
private fun GardenPreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            GardenContent(
                GardenState(
                    yardOnline = true, fountainOnline = true, sprinklerTimerOnline = true, poolOnline = true,
                    temperature = "28.4", humidity = "41", ledStripOn = true, poolPumpOn = true, poolTemperature = "26.4",
                    sprinklerActiveIndex = 3, sprinklerAuto = true, sprinklerStartTime = "05:30", sprinklerOnMinutes = 5, sprinklerSets = 2,
                    sprinklers = listOf(false, false, true, false, false, false),
                ),
                onIntent = {},
                onOpenSettings = {},
            )
        }
    }
}
