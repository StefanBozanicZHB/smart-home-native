package zhb.company.smart_home.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.heating.Node5Topics
import zhb.company.smart_home.data.local.AppPreferences
import zhb.company.smart_home.data.mqtt.MqttTopics
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.domain.SprinklerConfigCodec

class SettingsViewModel(
    private val repository: SmartHomeRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private data class Pending(
        val auto: Boolean? = null,
        val startTime: String? = null,
        val onMinutes: Int? = null,
        val offSeconds: Int? = null,
        val sets: Int? = null,
    )

    private val _pending = MutableStateFlow(Pending())

    val state: StateFlow<SettingsState> = combine(
        repository.state,
        preferences.isDarkTheme,
        preferences.node5Id,
        preferences.notificationsEnabled,
        _pending,
    ) { home, darkTheme, node5Id, notifications, pending ->
        SettingsState(
            darkTheme = darkTheme,
            notifications = notifications,
            node5TestBoard = node5Id == Node5Topics.BENCH,
            sprinklerAuto = pending.auto ?: home.sprinklerTimerAuto,
            sprinklerStartTime = pending.startTime ?: home.sprinklerStartTime,
            sprinklerOnMinutes = pending.onMinutes ?: home.sprinklerDurationOnMinutes,
            sprinklerOffSeconds = pending.offSeconds ?: home.sprinklerDurationOffSeconds,
            sprinklerSets = pending.sets ?: home.sprinklerSets,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.ToggleDarkTheme -> viewModelScope.launch { preferences.setDarkTheme(intent.enabled) }
            is SettingsIntent.ToggleNotifications -> viewModelScope.launch { preferences.setNotificationsEnabled(intent.enabled) }
            is SettingsIntent.ToggleNode5TestBoard -> viewModelScope.launch {
                preferences.setNode5Id(if (intent.enabled) Node5Topics.BENCH else Node5Topics.REAL)
            }
            is SettingsIntent.ToggleSprinklerAuto -> _pending.update { it.copy(auto = intent.enabled) }
            is SettingsIntent.SetStartTime -> _pending.update { it.copy(startTime = intent.time) }
            is SettingsIntent.SetOnMinutes -> _pending.update { it.copy(onMinutes = intent.minutes) }
            is SettingsIntent.SetOffSeconds -> _pending.update { it.copy(offSeconds = intent.seconds) }
            is SettingsIntent.SetSets -> _pending.update { it.copy(sets = intent.sets) }
            SettingsIntent.Save -> {
                val current = state.value
                val payload = SprinklerConfigCodec.encode(
                    startTime = current.sprinklerStartTime,
                    onDurationMinutes = current.sprinklerOnMinutes,
                    offDurationSeconds = current.sprinklerOffSeconds,
                    sets = current.sprinklerSets,
                    auto = current.sprinklerAuto,
                )
                viewModelScope.launch { repository.publish(MqttTopics.NODE0_SPRINKLER_CONFIG_CONTROL, payload) }
                _pending.update { Pending() }
            }
        }
    }
}
