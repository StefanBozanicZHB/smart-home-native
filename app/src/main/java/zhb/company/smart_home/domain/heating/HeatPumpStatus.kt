package zhb.company.smart_home.domain.heating

/**
 * What the heat pump is doing right now and why, in one of a few states the user can read at a
 * glance. Countdowns are estimates from the node's uptime (minimum run / minimum pause).
 */
sealed interface HeatPumpStatus {
    /** No connection or no state from the node yet. */
    data object Unknown : HeatPumpStatus

    /** Main switch off. */
    data object Off : HeatPumpStatus

    /** The node switched the pump off (or keeps it off) in safe mode; [reason] is the firmware code. */
    data class SafeMode(val reason: String?) : HeatPumpStatus

    /**
     * Running for [minutes] (null if unknown). [demand] false means the target is reached and the
     * pump only finishes its minimum run, about [minRunLeftMinutes] more.
     */
    data class Running(val minutes: Long?, val demand: Boolean, val minRunLeftMinutes: Long?) : HeatPumpStatus

    /** A zone wants heat but the pump is in its minimum pause; starts in about [startsInMinutes]. */
    data class Waiting(val startsInMinutes: Long?) : HeatPumpStatus

    /** Off because no zone needs heat; off since [sinceTime] ("HH:MM", node time) if known. */
    data class Idle(val sinceTime: String?) : HeatPumpStatus

    companion object {
        fun from(connected: Boolean, config: Node5Config?, live: Node5LiveState?, pump: PumpEvent?, safeMode: SafeModeState?): HeatPumpStatus {
            if (!connected || live == null) return Unknown
            val safeReason = safeMode?.takeIf { it.active }?.reason ?: live.safeReason
            if ((safeMode?.active == true || live.safeReason != null) && !live.pump) return SafeMode(safeReason)
            if (!(config?.mainSwitch ?: live.mainSwitch)) return Off

            // Seconds since the last pump change, if that event belongs to the current state.
            fun secondsSinceChange(expectOn: Boolean): Long? =
                pump?.takeIf { it.pump == expectOn }?.let { live.uptimeSeconds - it.atUptimeSeconds }?.takeIf { it >= 0 }

            if (live.pump) {
                val running = secondsSinceChange(expectOn = true)
                val minRunLeft = if (!live.demand) remainingMinutes(config?.minOnSeconds, running) else null
                return Running(running?.div(60), live.demand, minRunLeft)
            }
            if (live.demand) {
                return Waiting(remainingMinutes(config?.minOffSeconds, secondsSinceChange(expectOn = false)))
            }
            val off = secondsSinceChange(expectOn = false)
            return Idle(sinceTime = off?.let { minutesBefore(live.time, it / 60) })
        }

        /** Whole minutes (rounded up) until [limitSeconds] have passed since [elapsedSeconds]; null if done or unknown. */
        private fun remainingMinutes(limitSeconds: Int?, elapsedSeconds: Long?): Long? {
            if (limitSeconds == null || elapsedSeconds == null) return null
            val left = limitSeconds - elapsedSeconds
            return if (left > 0) (left + 59) / 60 else null
        }

        /** "21:35" and 25 minutes → "21:10"; null without node time or for a day or more. */
        private fun minutesBefore(time: String?, minutes: Long): String? {
            val now = TimeOfDay.parse(time) ?: return null
            if (minutes >= MINUTES_PER_DAY) return null
            return TimeOfDay.format(((now - minutes) % MINUTES_PER_DAY + MINUTES_PER_DAY).toInt() % MINUTES_PER_DAY)
        }
    }
}
