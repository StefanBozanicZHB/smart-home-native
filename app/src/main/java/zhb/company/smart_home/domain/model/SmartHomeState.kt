package zhb.company.smart_home.domain.model

/**
 * Single source of truth for every sensor reading / device state in the house,
 * mirroring the Flutter app's `Parameters` ChangeNotifier. Updated exclusively
 * by [zhb.company.smart_home.data.mqtt.SmartHomeRepository] as MQTT messages
 * arrive, and read by every screen ViewModel.
 */
data class SmartHomeState(
    // node0 - sprinkler auto-timer config
    val sprinklerStartTime: String = "00:00",
    val sprinklerDurationOnMinutes: Int = 1,
    val sprinklerDurationOffSeconds: Int = 0,
    val sprinklerSets: Int = 1,
    val sprinklerTimerAuto: Boolean = false,

    // node1 - sprinklers / fountain / status hub
    val sprinklerAll: Boolean = false,
    val sprinkler1: Boolean = false,
    val sprinkler2: Boolean = false,
    val sprinkler3: Boolean = false,
    val sprinkler4: Boolean = false,
    val sprinkler5: Boolean = false,
    val sprinkler6: Boolean = false,
    val sprinklerPower: Int = -1,
    val fountainPump: Boolean = false,
    val fountainLight: Boolean = false,

    // node2 - terrace / garden
    val ledStripTerrace: Boolean = false,
    val bulbTerrace: Boolean = false,
    val temperatureOutdoor: String = "--",
    val humidityOutdoor: String = "--",

    // node3 - main / front door
    val mainDoor: Boolean = false,

    // node4 - ground floor living room
    val temperatureLivingRoomDown: String = "--",
    val humidityLivingRoomDown: String = "--",
    val bulbLivingRoomDown1: Boolean = false,
    val bulbLivingRoomDown2: Boolean = false,
    val bulbLivingRoomDown3: Boolean = false,

    // node5 - heating: see zhb.company.smart_home.data.heating.Node5Repository

    // node6 - pool
    val poolPump: Boolean = false,
    val poolHeating: Boolean = false,
    val temperaturePool: String = "--",

    // node7 - shed
    val doorSmallHome: Boolean = false,
    val bulbSmallHome1: Boolean = false,
    val bulbSmallHome2: Boolean = false,
    val bulbSmallHome3: Boolean = false,
    val fingerSensorStatus: String = "",

    // node8 - first floor living room + AC
    val temperatureLivingRoomUp: String = "--",
    val humidityLivingRoomUp: String = "--",
    val gasLivingRoomUp: String = "--",
    val acMode: AcMode = AcMode.COOL,
    val acFanSpeed: AcFanSpeed = AcFanSpeed.AUTO,
    val acTemp: Int = 22,
    val acOn: Boolean = false,

    // node online flags, keyed 0..8
    val nodeOnline: Map<Int, Boolean> = emptyMap(),

    // devices currently on, human-readable, used for the Home "devices on" count + GoodNight
    val listPowerDevice: List<String> = emptyList(),
) {
    fun isNodeOnline(node: Int): Boolean = nodeOnline[node] == true
}

enum class AcMode(val code: String) {
    COOL("0"),
    AUTO("8"),
    FAN("4"),
    HEAT("C");

    companion object {
        fun fromCode(code: String): AcMode = entries.firstOrNull { it.code == code } ?: COOL
    }
}

enum class AcFanSpeed(val code: String) {
    AUTO("5"),
    MIN("4"),
    MED("2"),
    MAX("1");

    companion object {
        fun fromCode(code: String): AcFanSpeed = entries.firstOrNull { it.code == code } ?: AUTO
    }
}
