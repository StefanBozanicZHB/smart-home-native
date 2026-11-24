package zhb.company.smart_home.ui.command

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * How screens show a node command while it waits for the answer:
 * - the control shows the requested value at once, nothing else on the screen is disabled or
 *   moves (a normal answer takes a fraction of a second, so anything more only flickers);
 * - a "waiting" hint appears only when the answer is late ([SLOW_ANSWER_MS]);
 * - after an accepted command the screen waits briefly for the node's new state
 *   ([awaitNodeState]), so the control does not jump back in between.
 */

/** A normal answer takes a fraction of a second; a hint shown for that long would only flicker. */
const val SLOW_ANSWER_MS = 1_000L

/** The node publishes its new state right around the answer; this only bridges the gap. */
const val STATE_AFTER_ANSWER_MS = 3_000L

/** Runs [block]; calls [onSlow] once if it is still running after [SLOW_ANSWER_MS]. */
suspend fun <T> withSlowHint(onSlow: () -> Unit, block: suspend () -> T): T = coroutineScope {
    val hint = launch {
        delay(SLOW_ANSWER_MS)
        onSlow()
    }
    try {
        block()
    } finally {
        hint.cancel()
    }
}

/** Waits until [state] shows the change ([applied]), at most [STATE_AFTER_ANSWER_MS]. */
suspend fun <T> awaitNodeState(state: Flow<T>, applied: (T) -> Boolean) {
    withTimeoutOrNull(STATE_AFTER_ANSWER_MS) { state.first(applied) }
}
