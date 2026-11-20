package zhb.company.smart_home.ui.heating

import zhb.company.smart_home.domain.heating.Pipe

data class PipeRowUi(
    val pipe: Pipe,
    /** Assigned sensor address, null if the pipe has none. */
    val address: String? = null,
    val offsetHundredths: Int = 0,
    /** Pipe temperature with the offset, from node5/state. */
    val temperature: String? = null,
    /** Raw reading of the assigned sensor, from the bus list. */
    val rawTemperature: String? = null,
    /** Assigned, but the node did not find this sensor on the bus. */
    val missingOnBus: Boolean = false,
)

data class BusRowUi(
    val address: String,
    val rawTemperature: String,
    val pipe: Pipe?,
    /** Warmest sensor on the bus: the one held in the hand when identifying sensors. */
    val warmest: Boolean = false,
)

data class PipeSensorsState(
    val nodeId: String = "node5",
    val connected: Boolean = false,
    /** True until node5/sensors has arrived (also: firmware without pipe sensors). */
    val loading: Boolean = true,
    val fallback: Boolean = false,
    val pipes: List<PipeRowUi> = Pipe.entries.map { PipeRowUi(it) },
    val bus: List<BusRowUi> = emptyList(),
    val sending: Boolean = false,
    val message: HeatingMessage? = null,
    /** Bus sensor chosen for assignment; the pipe picker is open while set. */
    val pickingFor: String? = null,
    /** Pipe whose offset is being edited. */
    val editingOffset: Pipe? = null,
    val offsetError: Boolean = false,
    val confirmRemove: Pipe? = null,
    /** Address shown in full in a dialog. */
    val addressDetail: String? = null,
)

sealed interface PipeSensorsIntent {
    data object Refresh : PipeSensorsIntent
    data class PickSensor(val address: String) : PipeSensorsIntent
    data class AssignTo(val pipe: Pipe) : PipeSensorsIntent
    data object DismissPicker : PipeSensorsIntent
    data class EditOffset(val pipe: Pipe) : PipeSensorsIntent
    data class SaveOffset(val text: String) : PipeSensorsIntent
    data object DismissOffset : PipeSensorsIntent
    data class RequestRemove(val pipe: Pipe) : PipeSensorsIntent
    data object ConfirmRemove : PipeSensorsIntent
    data object DismissRemove : PipeSensorsIntent
    data class ShowAddress(val address: String) : PipeSensorsIntent
    data object DismissAddress : PipeSensorsIntent
    data object MessageShown : PipeSensorsIntent
}
