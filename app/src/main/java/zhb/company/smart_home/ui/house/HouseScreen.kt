package zhb.company.smart_home.ui.house

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import zhb.company.smart_home.R
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.ui.components.CardHeader
import zhb.company.smart_home.ui.components.DashboardCard
import zhb.company.smart_home.ui.components.ScreenHeader
import zhb.company.smart_home.ui.components.StatTile
import zhb.company.smart_home.ui.components.StatusChip
import zhb.company.smart_home.ui.components.TileGrid
import zhb.company.smart_home.ui.components.Tone
import zhb.company.smart_home.ui.components.adaptiveColumns
import zhb.company.smart_home.ui.history.ClimateHistoryCard
import zhb.company.smart_home.ui.history.HistoryAutoLoad
import zhb.company.smart_home.ui.history.HistoryState
import zhb.company.smart_home.ui.history.HistoryViewModel

@Composable
fun HouseScreen(viewModel: HouseViewModel = koinViewModel(), historyViewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by historyViewModel.state.collectAsStateWithLifecycle()
    HistoryAutoLoad(historyViewModel, HistoryPeriod.entries.toSet())
    HouseContent(state, history)
}

@Composable
private fun HouseContent(state: HouseState, history: HistoryState = HistoryState()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(title = stringResource(R.string.menu_house_combined), horizontalPadding = 0.dp) }
        item { RoomCard(Icons.Outlined.Weekend, stringResource(R.string.home_room_ground), state.ground) }
        item { RoomCard(Icons.Outlined.Chair, stringResource(R.string.home_room_upper), state.upper) }
        item { ClimateHistoryCard(history) }
    }
}

@Composable
private fun RoomCard(icon: ImageVector, title: String, room: RoomUi) {
    DashboardCard {
        CardHeader(icon, title) {
            if (!room.online) StatusChip(stringResource(R.string.home_node_offline_tile), Tone.DANGER)
        }
        val tiles = buildList<@Composable (Modifier) -> Unit> {
            add { m -> StatTile(stringResource(R.string.house_temperature), "${room.temperature}°", m) }
            add { m -> StatTile(stringResource(R.string.house_humidity), stringResource(R.string.home_humidity, room.humidity), m) }
            room.extras.forEach { extra -> add { m -> StatTile(stringResource(extra.label), extra.value, m) } }
        }
        TileGrid(columns = adaptiveColumns(tiles.size), tiles = tiles)
    }
}

// ---- previews ------------------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "House", heightDp = 900)
@androidx.compose.ui.tooling.preview.Preview(name = "House - font 200%", heightDp = 1800, fontScale = 2f)
@Composable
private fun HousePreview() {
    zhb.company.smart_home.ui.theme.SmartHomeTheme(darkTheme = false) {
        Surface {
            HouseContent(
                HouseState(
                    ground = RoomUi(online = true, temperature = "21.5", humidity = "45"),
                    upper = RoomUi(online = true, temperature = "22.4", humidity = "40"),
                ),
            )
        }
    }
}
