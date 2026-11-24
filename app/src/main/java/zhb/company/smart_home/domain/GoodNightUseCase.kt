package zhb.company.smart_home.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import zhb.company.smart_home.data.garden.Node1Repository
import zhb.company.smart_home.data.garden.Node2Repository
import zhb.company.smart_home.data.garden.Node6Repository
import zhb.company.smart_home.data.node.CommandOutcome

/** What "Laku noć" switches off. */
enum class GoodNightDevice { FOUNTAIN_PUMP, FOUNTAIN_LIGHT, WATERING, NEON, LAMP, POOL_PUMP, POOL_HEATING }

/**
 * [sent] devices were on and got a switch-off command; [failed] of them did not confirm it (no
 * answer, refused, or not connected), so they may still be on.
 */
data class GoodNightResult(val sent: List<GoodNightDevice>, val failed: List<GoodNightDevice>) {
    val alreadyOff: Boolean get() = sent.isEmpty()
}

/**
 * Turns off everything that is on: Node 1 fountain and watering, Node 2 yard lights, Node 6 pool
 * pump and heating. The commands are sent together and their answers awaited.
 */
class GoodNightUseCase(
    private val node1: Node1Repository,
    private val node2: Node2Repository,
    private val node6: Node6Repository,
) {

    suspend operator fun invoke(): GoodNightResult {
        val yard = node1.snapshot.value.state
        val lights = node2.state.value
        val pool = node6.state.value
        val commands = buildList<Pair<GoodNightDevice, suspend () -> CommandOutcome>> {
            if (yard?.fountain?.pump == true) add(GoodNightDevice.FOUNTAIN_PUMP to { node1.setPump(false) })
            if (yard?.fountain?.light == true) add(GoodNightDevice.FOUNTAIN_LIGHT to { node1.setLight(false) })
            if (yard?.run != null) add(GoodNightDevice.WATERING to { node1.stopRun() })
            if (lights?.neon == true) add(GoodNightDevice.NEON to { node2.setNeon(false) })
            if (lights?.lamp == true) add(GoodNightDevice.LAMP to { node2.setLamp(false) })
            if (pool?.pump == true) add(GoodNightDevice.POOL_PUMP to { node6.setPump(false) })
            if (pool?.heating == true) add(GoodNightDevice.POOL_HEATING to { node6.setHeating(false) })
        }
        val outcomes = coroutineScope { commands.map { (device, send) -> async { device to send() } }.awaitAll() }
        return GoodNightResult(
            sent = commands.map { it.first },
            failed = outcomes.filter { (_, outcome) -> outcome !is CommandOutcome.Accepted }.map { it.first },
        )
    }
}
