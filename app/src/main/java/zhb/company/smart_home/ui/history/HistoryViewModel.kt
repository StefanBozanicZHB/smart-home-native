package zhb.company.smart_home.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.history.HistoryDoc
import zhb.company.smart_home.data.history.HistoryMeta
import zhb.company.smart_home.data.history.HistoryPeriod
import zhb.company.smart_home.data.history.HistoryRepository
import zhb.company.smart_home.data.remote.RtdbError
import zhb.company.smart_home.data.remote.toRtdbError

data class HistoryState(
    /** Loaded periods; a null value means the RPi has not written that period yet. */
    val docs: Map<HistoryPeriod, HistoryDoc?> = emptyMap(),
    val loading: Set<HistoryPeriod> = emptySet(),
    val meta: HistoryMeta? = null,
    val metaLoaded: Boolean = false,
    val error: RtdbError? = null,
) {
    fun doc(period: HistoryPeriod): HistoryDoc? = docs[period]
    fun isLoading(period: HistoryPeriod) = period in loading && period !in docs
}

/** History for the chart cards of one screen; the repository cache is shared by all screens. */
class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun load(periods: Collection<HistoryPeriod>, withMeta: Boolean = false) {
        periods.forEach { period ->
            if (period in _state.value.loading) return@forEach
            _state.update { it.copy(loading = it.loading + period) }
            viewModelScope.launch {
                repository.get(period).fold(
                    onSuccess = { doc -> _state.update { it.copy(docs = it.docs + (period to doc), loading = it.loading - period, error = null) } },
                    onFailure = { e -> _state.update { it.copy(loading = it.loading - period, error = e.toRtdbError()) } },
                )
            }
        }
        if (withMeta) {
            viewModelScope.launch {
                repository.meta().fold(
                    onSuccess = { m -> _state.update { it.copy(meta = m, metaLoaded = true) } },
                    onFailure = { e -> _state.update { it.copy(metaLoaded = true, error = e.toRtdbError()) } },
                )
            }
        }
    }
}

/**
 * Loads [periods] while the screen is visible and again every few minutes (the RPi writes every
 * 10 min; the repository cache keeps this from going to the network more often than needed).
 */
@Composable
fun HistoryAutoLoad(viewModel: HistoryViewModel, periods: Set<HistoryPeriod>, withMeta: Boolean = false) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(periods, withMeta) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.load(periods, withMeta)
                delay(HistoryRepository.MAX_AGE_MS)
            }
        }
    }
}
