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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.components.CompactToggleTile
import zhb.company.smart_home.ui.components.CollapsingRoomHeader
import zhb.company.smart_home.ui.components.rememberCompactHeaderHeight
import zhb.company.smart_home.ui.components.RoomStat
import zhb.company.smart_home.ui.components.ToggleTile
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PanelBorderTeal
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

private val GARDEN_HEADER_HEIGHT = 300.dp

@Composable
fun GardenScreen(
    viewModel: GardenViewModel = koinViewModel(),
    onOpenHome: () -> Unit,
    onOpenHeating: () -> Unit,
    onOpenHouse: () -> Unit,
    onOpenShed: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val compactHeaderHeight = rememberCompactHeaderHeight()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding -> GardenContent(state, viewModel, padding, listState, compactHeaderHeight) }

        CollapsingRoomHeader(
            listState = listState,
            expandedHeight = GARDEN_HEADER_HEIGHT,
            imageRes = R.drawable.img_garden,
            title = stringResource(R.string.menu_garden),
            online = state.online,
            stats = listOf(
                RoomStat(Icons.Filled.Thermostat, "${state.temperature}°C"),
                RoomStat(Icons.Filled.WaterDrop, "${state.humidity}%"),
            ),
            onBack = onOpenHome,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (state.showSprinklerDialog) {
        SprinklerDialog(state = state, onToggle = { i, on -> viewModel.onIntent(GardenIntent.ToggleSprinkler(i, on)) }, onDismiss = { viewModel.onIntent(GardenIntent.DismissSprinklerDialog) })
    }
    if (state.showLocationDialog) {
        LocationDialog(activeIndex = state.sprinklerActiveIndex, onDismiss = { viewModel.onIntent(GardenIntent.DismissLocationDialog) })
    }
}

@Composable
private fun GardenContent(
    state: GardenState,
    viewModel: GardenViewModel,
    padding: PaddingValues,
    listState: LazyListState,
    compactHeaderHeight: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        state = listState,
        contentPadding = WindowInsets.navigationBars.asPaddingValues(),
        modifier = Modifier.fillMaxSize().padding(padding).padding(top = compactHeaderHeight),
    ) {
        item { Spacer(modifier = Modifier.height((GARDEN_HEADER_HEIGHT - compactHeaderHeight).coerceAtLeast(0.dp))) }

        item {
            SectionLabel(stringResource(R.string.garden_devices_section))
            Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleTile(
                        label = stringResource(R.string.garden_led_strip),
                        icon = Icons.Filled.Lightbulb,
                        isOn = state.ledStripOn,
                        onToggle = { viewModel.onIntent(GardenIntent.ToggleLedStrip(it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToggleTile(
                        label = stringResource(R.string.garden_bulb),
                        icon = Icons.Filled.WbIncandescent,
                        isOn = state.bulbOn,
                        onToggle = { viewModel.onIntent(GardenIntent.ToggleBulb(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleTile(
                        label = stringResource(R.string.garden_fountain_pump),
                        icon = Icons.Filled.Water,
                        isOn = state.fountainPumpOn,
                        onToggle = { viewModel.onIntent(GardenIntent.ToggleFountainPump(it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToggleTile(
                        label = stringResource(R.string.garden_fountain_light),
                        icon = Icons.Filled.NightlightRound,
                        isOn = state.fountainLightOn,
                        onToggle = { viewModel.onIntent(GardenIntent.ToggleFountainLight(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { SprinklerCard(state, viewModel) }
        item { PoolCard(state, viewModel) }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 10.dp),
    )
}

@Composable
private fun PoolCard(state: GardenState, viewModel: GardenViewModel) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.garden_pool))
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PrimaryColor.copy(alpha = 0.12f), shape = CircleShape) {
                        Icon(Icons.Filled.Pool, contentDescription = null, tint = PrimaryColor, modifier = Modifier.padding(10.dp).size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = stringResource(R.string.garden_pool), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${state.poolTemperature}°C",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleTile(
                        label = stringResource(R.string.garden_pool_pump),
                        icon = Icons.Filled.Water,
                        isOn = state.poolPumpOn,
                        onToggle = { viewModel.onIntent(GardenIntent.TogglePoolPump(it)) },
                        modifier = Modifier.weight(1f),
                    )
                    ToggleTile(
                        label = stringResource(R.string.garden_pool_heating),
                        icon = Icons.Filled.LocalFireDepartment,
                        isOn = state.poolHeatingOn,
                        onToggle = { viewModel.onIntent(GardenIntent.TogglePoolHeating(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SprinklerCard(state: GardenState, viewModel: GardenViewModel) {
    val active = state.sprinklerActiveIndex != -1
    val infiniteTransition = rememberInfiniteTransition(label = "sprinklerPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Column(modifier = Modifier.padding(top = 8.dp)) {
        SectionLabel(stringResource(R.string.garden_sprinklers))
        Card(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (active) PrimaryColor.copy(alpha = pulseAlpha * 0.18f + 0.06f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WaterDrop,
                            contentDescription = null,
                            tint = if (active) PrimaryColor.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp).size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (active) "${stringResource(R.string.garden_sprinkler_active)} ${state.sprinklerActiveIndex}" else stringResource(R.string.garden_sprinkler_none_active),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = if (state.sprinklerAuto) EnableGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                text = "${stringResource(R.string.garden_sprinkler_auto)}: ${if (state.sprinklerAuto) stringResource(R.string.on) else stringResource(R.string.off)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.sprinklerAuto) EnableGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.onIntent(GardenIntent.OpenLocationDialog) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.garden_sprinkler_location),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.onIntent(GardenIntent.OpenSprinklerDialog) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.garden_sprinkler_manual),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SprinklerDialog(state: GardenState, onToggle: (Int, Boolean) -> Unit, onDismiss: () -> Unit) {
    val labels = listOf(
        R.string.sprinkler_1, R.string.sprinkler_2, R.string.sprinkler_3,
        R.string.sprinkler_4, R.string.sprinkler_5, R.string.sprinkler_6,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.sprinkler_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.chunked(2).forEachIndexed { rowIndex, rowLabels ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowLabels.forEachIndexed { colIndex, labelRes ->
                            val index = rowIndex * 2 + colIndex
                            CompactToggleTile(
                                label = stringResource(labelRes),
                                isOn = state.sprinklers.getOrElse(index) { false },
                                onToggle = { on -> onToggle(index + 1, on) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
    )
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
                        .border(1.dp, PanelBorderTeal, RoundedCornerShape(14.dp)),
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
                                        .background(PrimaryColor, CircleShape),
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (isActive) PrimaryColor else White.copy(alpha = 0.92f),
                                border = if (isActive) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(if (isActive) 30.dp else 22.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "$number",
                                        color = if (isActive) White else MaterialTheme.colorScheme.onSurfaceVariant,
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
