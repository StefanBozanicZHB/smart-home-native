package zhb.company.smart_home.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zhb.company.smart_home.data.reports.HeatingReport
import zhb.company.smart_home.data.reports.HeatingReportsApi
import zhb.company.smart_home.data.reports.ReportSummary
import zhb.company.smart_home.data.reports.ReportsError
import zhb.company.smart_home.data.remote.toRtdbError

data class ReportsState(
    val loading: Boolean = true,
    val items: List<ReportSummary> = emptyList(),
    val error: ReportsError? = null,
)

class ReportsViewModel(private val api: HeatingReportsApi) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            api.list().fold(
                onSuccess = { items -> _state.value = ReportsState(loading = false, items = items) },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toRtdbError()) } },
            )
        }
    }
}

data class ReportDetailState(
    val loading: Boolean = true,
    val report: HeatingReport? = null,
    /** Loaded, but there is no report for that day (no heating). */
    val missing: Boolean = false,
    val error: ReportsError? = null,
)

class ReportDetailViewModel(private val date: String, private val api: HeatingReportsApi) : ViewModel() {
    private val _state = MutableStateFlow(ReportDetailState())
    val state: StateFlow<ReportDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            api.get(date).fold(
                onSuccess = { r -> _state.value = ReportDetailState(loading = false, report = r, missing = r == null) },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toRtdbError()) } },
            )
        }
    }
}

