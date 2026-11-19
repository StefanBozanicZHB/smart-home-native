package zhb.company.smart_home.ui.home

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.components.PulsingStatusDot
import zhb.company.smart_home.ui.theme.DisableRed
import zhb.company.smart_home.ui.theme.EnableGreen
import zhb.company.smart_home.ui.theme.PrimaryColor
import zhb.company.smart_home.ui.theme.White

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onOpenGarden: () -> Unit,
    onOpenHeating: () -> Unit,
    onOpenHouse: () -> Unit,
    onOpenShed: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.ShowToast -> Toast.makeText(context, context.getString(effect.messageRes), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold { padding ->
            HomeContent(
                state = state,
                onIntent = viewModel::onIntent,
                padding = padding,
                onOpenHeating = onOpenHeating,
                onOpenGarden = onOpenGarden,
                onOpenPool = onOpenGarden,
                onOpenShed = onOpenShed,
                onOpenHouse = onOpenHouse,
                onOpenConnections = onOpenConnections,
                onOpenSettings = onOpenSettings,
            )
        }

        if (state.showDevicesOnDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onIntent(HomeIntent.DismissDevicesOnDialog) },
                confirmButton = {
                    TextButton(onClick = { viewModel.onIntent(HomeIntent.DismissDevicesOnDialog) }) { Text("OK") }
                },
                title = { Text(stringResource(R.string.home_devices_on)) },
                text = {
                    Column {
                        if (state.devicesOnList.isEmpty()) {
                            Text(stringResource(R.string.goodnight_toast_already_off))
                        } else {
                            state.devicesOnList.forEach { Text(text = it) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    padding: PaddingValues,
    onOpenHeating: () -> Unit,
    onOpenGarden: () -> Unit,
    onOpenPool: () -> Unit,
    onOpenShed: () -> Unit,
    onOpenHouse: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        item {
            HomeHeader(
                connected = state.connected,
                onOpenConnections = onOpenConnections,
                onOpenSettings = onOpenSettings,
            )
        }

        item { SectionTitle(stringResource(R.string.home_quick_glance)) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickGlanceCard(
                    icon = Icons.Filled.Thermostat,
                    title = stringResource(R.string.home_outdoor),
                    value = "${state.garden.temperature}° · ${state.garden.humidity}%",
                    online = state.garden.online,
                    onClick = onOpenGarden,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                QuickGlanceCard(
                    icon = Icons.Filled.LocalFireDepartment,
                    title = stringResource(R.string.home_heating),
                    value = "${state.heating.temperature}°C",
                    online = state.heating.online,
                    statusBadge = state.heating.statusBadges.firstOrNull(),
                    onClick = onOpenHeating,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        item {
            GoodnightHeroCard(
                devicesOnCount = state.devicesOnCount,
                onShowDevices = { onIntent(HomeIntent.ShowDevicesOnDialog) },
                onGoodnight = { onIntent(HomeIntent.Goodnight) },
            )
        }

        item { SectionTitle(stringResource(R.string.home_rooms)) }
        item {
            val rooms = listOf(
                RoomGridItem(stringResource(R.string.room_garden), state.garden, imageRes = R.drawable.img_garden, onClick = onOpenGarden),
                RoomGridItem(stringResource(R.string.room_pool), state.pool, imageRes = R.drawable.img_pool, onClick = onOpenPool),
                RoomGridItem(stringResource(R.string.menu_first_floor), state.firstFloor, imageRes = R.drawable.img_living_room_down, onClick = onOpenHouse),
                RoomGridItem(stringResource(R.string.menu_second_floor), state.secondFloor, imageRes = R.drawable.img_living_room_up, onClick = onOpenHouse),
                RoomGridItem(stringResource(R.string.room_heating), state.heating, imageRes = R.drawable.img_heating, onClick = onOpenHeating),
                RoomGridItem(stringResource(R.string.room_shed), state.shed, imageRes = R.drawable.img_shed, onClick = onOpenShed),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rooms.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { room -> RoomGridCard(room, modifier = Modifier.weight(1f)) }
                        if (rowItems.size < 2) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
    }
}

private fun greetingRes(hourOfDay: Int): Int = when (hourOfDay) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..17 -> R.string.home_greeting_day
    else -> R.string.home_greeting_evening
}

@Composable
private fun HomeHeader(connected: Boolean, onOpenConnections: () -> Unit, onOpenSettings: () -> Unit) {
    val greeting = stringResource(greetingRes(java.time.LocalTime.now().hour))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(PrimaryColor, PrimaryColor.copy(alpha = 0.82f)),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(text = greeting, color = White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.app_name),
                        color = White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderIconButton(Icons.Filled.Wifi, stringResource(R.string.nav_connections), onOpenConnections)
                    HeaderIconButton(Icons.Filled.Settings, stringResource(R.string.nav_settings), onOpenSettings)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = White.copy(alpha = 0.16f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = null,
                        tint = if (connected) EnableGreen else DisableRed,
                        modifier = Modifier.size(8.dp),
                    )
                    Text(
                        text = if (connected) stringResource(R.string.home_connected) else stringResource(R.string.home_disconnected),
                        color = White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = White.copy(alpha = 0.16f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = White,
            modifier = Modifier.padding(10.dp).size(20.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun QuickGlanceCard(
    icon: ImageVector,
    title: String,
    value: String,
    online: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusBadge: RoomStatusBadge? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = PrimaryColor, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
            when {
                statusBadge != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (statusBadge.active) EnableGreen else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Text(
                        text = statusBadge.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
                !online -> Text(
                    text = stringResource(R.string.home_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = DisableRed,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GoodnightHeroCard(devicesOnCount: Int, onShowDevices: () -> Unit, onGoodnight: () -> Unit) {
    val allOff = devicesOnCount == 0
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allOff) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else PrimaryColor.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = !allOff, onClick = onShowDevices),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (allOff) EnableGreen.copy(alpha = 0.16f) else PrimaryColor.copy(alpha = 0.16f),
            ) {
                Icon(
                    imageVector = if (allOff) Icons.Filled.CheckCircle else Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = if (allOff) EnableGreen else PrimaryColor,
                    modifier = Modifier.padding(12.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    text = if (allOff) stringResource(R.string.home_all_off_title) else stringResource(R.string.home_devices_on_full, devicesOnCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (allOff) stringResource(R.string.home_all_off_subtitle) else stringResource(R.string.home_tap_to_view),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!allOff) {
                FilledTonalButton(onClick = onGoodnight) {
                    Text(stringResource(R.string.home_goodnight_button))
                }
            }
        }
    }
}

private data class RoomGridItem(
    val title: String,
    val room: RoomCardState,
    @DrawableRes val imageRes: Int,
    val onClick: () -> Unit,
)

/**
 * The image keeps its own fixed aspect ratio (via [Modifier.aspectRatio], sized by width only),
 * while the gradient scrim and the outer clip use [Modifier.matchParentSize] - so the card's total
 * height is driven by whichever is tallest, the photo or the overlaid text. At normal font scale
 * that's always the photo (unchanged look), but at maximum system font size the text column can
 * grow past the photo's height without being clipped: the card simply grows a little taller
 * instead of cutting text off.
 */
@Composable
private fun RoomGridCard(item: RoomGridItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = item.onClick),
    ) {
        Image(
            painter = painterResource(item.imageRes),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.3f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 40f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(4.dp))
                PulsingStatusDot(online = item.room.online)
            }
            if (!item.room.online) {
                Text(text = stringResource(R.string.home_offline), color = DisableRed, style = MaterialTheme.typography.labelSmall)
            } else {
                RoomReadingRow(null, item.room.temperature, item.room.humidity.takeIf { it != "--" })
            }
            item.room.statusBadges.forEach { badge ->
                RoomStatusRow(badge)
            }
        }
    }
}

@Composable
private fun RoomReadingRow(label: String?, temperature: String, humidity: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = if (label != null) 1.dp else 0.dp),
    ) {
        if (label != null) {
            Text(
                text = label,
                color = White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        Icon(Icons.Filled.Thermostat, contentDescription = null, tint = White, modifier = Modifier.size(12.dp))
        Text(text = "$temperature°", color = White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp, end = if (humidity != null) 6.dp else 0.dp))
        if (humidity != null) {
            Icon(Icons.Filled.Opacity, contentDescription = null, tint = White, modifier = Modifier.size(12.dp))
            Text(text = "$humidity%", color = White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
private fun RoomStatusRow(badge: RoomStatusBadge) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (badge.active) EnableGreen else White.copy(alpha = 0.35f), CircleShape),
        )
        Text(
            text = badge.label,
            color = White.copy(alpha = 0.9f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
