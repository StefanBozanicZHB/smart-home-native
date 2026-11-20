package zhb.company.smart_home.ui.heating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.heating.CommandOutcome
import zhb.company.smart_home.data.heating.Node5Repository
import zhb.company.smart_home.domain.heating.BusSensor
import zhb.company.smart_home.domain.heating.PipeCalibration

enum class CalibrationPhase { INTRO, SAMPLING, RESULT }

data class PipeCalibrationState(
    val phase: CalibrationPhase = CalibrationPhase.INTRO,
    val sample: Int = 0,
    val sampleCount: Int = PipeCalibrationViewModel.SAMPLE_COUNT,
    /** Latest raw readings while sampling. */
    val bus: List<BusSensor> = emptyList(),
    val result: PipeCalibration.Result? = null,
    val sending: Boolean = false,
    val message: HeatingMessage? = null,
    /** Offsets were accepted by the node; the screen closes. */
    val done: Boolean = false,
)

sealed interface PipeCalibrationIntent {
    data object Start : PipeCalibrationIntent
    data object Send : PipeCalibrationIntent
    data object MessageShown : PipeCalibrationIntent
}

/**
 * Calibration helper: asks the node for fresh raw readings [SAMPLE_COUNT] times, [SAMPLE_INTERVAL_MS]
 * apart, then proposes offsets (PipeCalibration) and sends them in one sensors/set after confirmation.
 */
class PipeCalibrationViewModel(private val node5: Node5Repository) : ViewModel() {

    private val _state = MutableStateFlow(PipeCalibrationState())
    val state: StateFlow<PipeCalibrationState> = _state.asStateFlow()

    private var sampling: Job? = null

    fun onIntent(intent: PipeCalibrationIntent) {
        when (intent) {
            PipeCalibrationIntent.Start -> start()
            PipeCalibrationIntent.Send -> send()
            PipeCalibrationIntent.MessageShown -> _state.update { it.copy(message = null) }
        }
    }

    private fun start() {
        sampling?.cancel()
        _state.value = PipeCalibrationState(phase = CalibrationPhase.SAMPLING)
        sampling = viewModelScope.launch {
            val samples = mutableListOf<List<BusSensor>>()
            repeat(SAMPLE_COUNT) { i ->
                node5.requestState()
                delay(SAMPLE_INTERVAL_MS)
                val bus = node5.snapshot.value.sensors?.bus.orEmpty()
                samples += bus
                _state.update { it.copy(sample = i + 1, bus = bus) }
            }
            val sensors = node5.snapshot.value.sensors
            _state.update {
                it.copy(
                    phase = CalibrationPhase.RESULT,
                    result = if (sensors == null) PipeCalibration.Result.TooFewSensors else PipeCalibration.compute(samples, sensors),
                )
            }
        }
    }

    private fun send() {
        val proposal = _state.value.result as? PipeCalibration.Result.Proposal ?: return
        if (_state.value.sending || proposal.rows.isEmpty()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val outcome = node5.updateSensors(proposal.changes())
            _state.update {
                it.copy(
                    sending = false,
                    done = outcome is CommandOutcome.Accepted,
                    message = when (outcome) {
                        is CommandOutcome.Accepted -> HeatingMessage.Saved
                        is CommandOutcome.Rejected -> HeatingMessage.Rejected(outcome.error)
                        CommandOutcome.Timeout -> HeatingMessage.Timeout
                        CommandOutcome.NotConnected -> HeatingMessage.NotConnected
                    },
                )
            }
        }
    }

    companion object {
        const val SAMPLE_COUNT = 5
        const val SAMPLE_INTERVAL_MS = 8_000L
    }
}
