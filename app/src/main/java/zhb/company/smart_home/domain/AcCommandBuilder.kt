package zhb.company.smart_home.domain

import zhb.company.smart_home.domain.model.AcFanSpeed
import zhb.company.smart_home.domain.model.AcMode

/**
 * Builds the composite IR remote command string published to the AC unit
 * (node8), in the form `B2<fanCode><'F'><tempHexCode><modeCode>`.
 *
 * NOTE: the exact temperature->hex lookup table used by the original Flutter
 * app was not fully recovered (only its stated endpoints, 17->'0' and
 * 30->'B', were documented). This implementation uses a sequential hex-digit
 * mapping over the 17..30 range, which reproduces the documented endpoint at
 * 17 exactly; the far end lands on 'D' rather than 'B' with a strictly
 * sequential table, so treat this mapping as best-effort and verify it
 * against the real remote / original Dart source before relying on it.
 */
object AcCommandBuilder {
    const val POWER_OFF_COMMAND = "B27BE0"
    const val MIN_TEMP = 17
    const val MAX_TEMP = 30

    private val tempHexTable: Map<Int, Char> = (MIN_TEMP..MAX_TEMP)
        .mapIndexed { index, temp -> temp to index.toString(16).uppercase()[0] }
        .toMap()

    fun buildCommand(fanSpeed: AcFanSpeed, temp: Int, mode: AcMode): String {
        val clampedTemp = temp.coerceIn(MIN_TEMP, MAX_TEMP)
        val tempHex = tempHexTable[clampedTemp] ?: '0'
        return "B2${fanSpeed.code}F$tempHex${mode.code}"
    }
}
