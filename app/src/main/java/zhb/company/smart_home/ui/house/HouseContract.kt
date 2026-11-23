package zhb.company.smart_home.ui.house

import zhb.company.smart_home.ui.format.NO_VALUE

/**
 * One room with its sensor node. Temperature and humidity come from every room node today;
 * [extras] holds whatever a node adds later (e.g. presence from the LD2410 radar planned for
 * Node 8) without changing the screen's structure.
 */
data class RoomUi(
    val online: Boolean = false,
    val temperature: String = NO_VALUE,
    val humidity: String = NO_VALUE,
    val extras: List<RoomReading> = emptyList(),
)

/** An extra reading shown as one more tile: a label resource and a ready-to-show value. */
data class RoomReading(@androidx.annotation.StringRes val label: Int, val value: String)

data class HouseState(
    val ground: RoomUi = RoomUi(),
    val upper: RoomUi = RoomUi(),
)
