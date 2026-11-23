package zhb.company.smart_home.ui.house

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.ui.format.Readings

/** Room climate: ground floor (Node 4) and upper floor (Node 8). */
class HouseViewModel(repository: SmartHomeRepository) : ViewModel() {

    val state: StateFlow<HouseState> = repository.state.map { home ->
        HouseState(
            ground = RoomUi(
                online = home.isNodeOnline(4),
                temperature = Readings.temperature(home.temperatureLivingRoomDown),
                humidity = Readings.percent(home.humidityLivingRoomDown),
            ),
            upper = RoomUi(
                online = home.isNodeOnline(8),
                temperature = Readings.temperature(home.temperatureLivingRoomUp),
                humidity = Readings.percent(home.humidityLivingRoomUp),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HouseState())
}
