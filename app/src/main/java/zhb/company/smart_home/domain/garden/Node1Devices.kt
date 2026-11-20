package zhb.company.smart_home.domain.garden

/** Node 1 devices for the home "devices on" list and GoodNight. */
object Node1Devices {
    const val FOUNTAIN_PUMP = "Pumpa fontane"
    const val FOUNTAIN_LIGHT = "Svetlo fontane"
    const val SPRINKLERS = "Prskalice"

    fun onLabels(state: Node1State?): List<String> {
        state ?: return emptyList()
        return buildList {
            if (state.fountain.pump) add(FOUNTAIN_PUMP)
            if (state.fountain.light) add(FOUNTAIN_LIGHT)
            if (state.run != null || state.valve != null) add(SPRINKLERS)
        }
    }
}
