package zhb.company.smart_home.domain

import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode

/**
 * Builds the composite IR remote command string published to the AC unit
 * (node8), in the form `B2<fanCode>F<tempCode><modeCode>`.
 *
 * The temperature codes are not sequential: they follow the AC's IR protocol
 * (Gray-code-like ordering). The table is copied from the Flutter app
 * (`Parameters.sendCommandIcLivingRoomUp`), which is known to work with the unit.
 */
object AcCommandBuilder {
    const val POWER_OFF_COMMAND = "B27BE0"
    const val MIN_TEMP = 17
    const val MAX_TEMP = 30

    private val tempCodes: Map<Int, Char> = mapOf(
        17 to '0', 18 to '1', 19 to '3', 20 to '2', 21 to '6', 22 to '7', 23 to '5',
        24 to '4', 25 to 'C', 26 to 'D', 27 to '9', 28 to '8', 29 to 'A', 30 to 'B',
    )

    fun buildCommand(fanSpeed: AcFanSpeed, temp: Int, mode: AcMode): String {
        val tempCode = tempCodes.getValue(temp.coerceIn(MIN_TEMP, MAX_TEMP))
        return "B2${fanSpeed.code}F$tempCode${mode.code}"
    }
}
