package zhb.company.smart_home.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.garden.Node6Repository

/**
 * Turns off everything that is on: Node 1 fountain and watering, Node 2 yard lights, Node 6 pool
 * pump and heating. The commands are sent together and their answers awaited.
 */
class GoodNightUseCase(
    private val node1: Node1Repository,
    private val node2: Node2Repository,
    private val node6: Node6Repository,
) {

    suspend operator fun invoke(): Boolean {
        val yard = node1.snapshot.value.state
        val lights = node2.state.value
        val jsonCommands = buildList<suspend () -> Unit> {
            if (yard?.fountain?.pump == true) add { node1.setPump(false) }
            if (yard?.fountain?.light == true) add { node1.setLight(false) }
            if (yard?.run != null) add { node1.stopRun() }
            if (lights?.neon == true) add { node2.setNeon(false) }
            if (lights?.lamp == true) add { node2.setLamp(false) }
            if (node6.state.value?.pump == true) add { node6.setPump(false) }
            if (node6.state.value?.heating == true) add { node6.setHeating(false) }
        }
        coroutineScope { jsonCommands.map { async { it() } }.awaitAll() }
        return jsonCommands.isNotEmpty()
    }
}
