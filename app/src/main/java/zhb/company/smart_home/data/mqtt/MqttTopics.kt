package zhb.company.smart_home.data.mqtt

/** Every MQTT topic used by the app, centralized (the Flutter source left many of these inline). */
object MqttTopics {
    const val SUBSCRIBE_ALL = "bozaSub/#"
    private const val BASE = "bozaSub/kuca"

    // node0 - sprinkler auto-timer config
    const val NODE0_SPRINKLER_CONFIG_CONTROL = "$BASE/node0/sprinkler/config"
    const val NODE0_SPRINKLER_CONFIG_STATUS = "$BASE/node0/sprinkler/status"
    const val NODE0_SPRINKLER_TIMER_STATUS = "$BASE/node0/timer"

    // node1 - sprinklers / fountain / status hub
    const val NODE1_REQUEST_STATUS = "$BASE/node1/stanje"
    const val NODE1_ALL_STATUS = "$BASE/node1/stanje/stanje"
    const val NODE1_ALL_SPRINKLERS_CONTROL = "$BASE/node0/sprinkler/manual/all"
    const val NODE1_ALL_SPRINKLERS_STATUS = "$BASE/node0/sprinkler/manual/status"
    const val NODE1_SPRINKLER_ALL_STOP = "$BASE/node1/prskalicaAll"
    fun node0SprinklerControl(index: Int) = "$BASE/node0/sprinkler/manual/sprinkler$index"
    fun node1SprinklerStatus(index: Int) = "$BASE/node1/prskalica$index/stanje"
    const val NODE1_FOUNTAIN_PUMP_CONTROL = "$BASE/node1/fontanaPumpa"
    const val NODE1_FOUNTAIN_PUMP_STATUS = "$BASE/node1/fontanaPumpa/stanje"
    const val NODE1_FOUNTAIN_LIGHT_CONTROL = "$BASE/node1/fontanaSvetlo"
    const val NODE1_FOUNTAIN_LIGHT_STATUS = "$BASE/node1/fontanaSvetlo/stanje"

    // node2 - terrace / garden
    const val NODE2_HUMIDITY = "$BASE/node2/dvoriste/vlaznost"
    const val NODE2_TEMPERATURE = "$BASE/node2/dvoriste/temperatura"
    const val NODE2_LED_STRIP_CONTROL = "$BASE/node2/neonka"
    const val NODE2_LED_STRIP_STATUS = "$BASE/node2/neonka/stanje"
    const val NODE2_BULB_CONTROL = "$BASE/node2/sijalica"
    const val NODE2_BULB_STATUS = "$BASE/node2/sijalica/stanje"

    // node3 - main door
    const val NODE3_DOOR_CONTROL = "$BASE/node3/vrata"
    const val NODE3_DOOR_STATUS = "$BASE/node3/vrata/stanje"

    // node4 - ground floor living room
    const val NODE4_HUMIDITY = "$BASE/node4/dnevniBoravak/vlaznost"
    const val NODE4_TEMPERATURE = "$BASE/node4/dnevniBoravak/temperatura"
    fun node4BulbControl(index: Int) = "$BASE/node4/dnevniBoravak/sijalica$index"
    fun node4BulbStatus(index: Int) = "$BASE/node4/dnevniBoravak/sijalica$index/stanje"

    // node5 - heating: new JSON API with a switchable node id, see data/heating/Node5Topics.kt

    // node6 - pool
    const val NODE6_POOL_PUMP_STATUS = "$BASE/node6/pool/pump/status"
    const val NODE6_POOL_PUMP_CONTROL = "$BASE/node6/pool/pump"
    const val NODE6_POOL_HEATING_STATUS = "$BASE/node6/pool/heating/status"
    const val NODE6_POOL_HEATING_CONTROL = "$BASE/node6/pool/heating"
    const val NODE6_POOL_TEMPERATURE = "$BASE/node6/pool/temperature"

    // node7 - shed / fingerprint access
    const val NODE7_FINGER_ALL_STATUS = "$BASE/node7/all/fid/stanje"
    const val NODE7_FINGER_ADD_STATUS = "$BASE/node7/add/fid/stanje"
    const val NODE7_FINGER_DEL_STATUS = "$BASE/node7/del/fid/stanje"
    const val NODE7_FINGER_ADD_CONTROL = "$BASE/node7/add/fid"
    const val NODE7_FINGER_DEL_CONTROL = "$BASE/node7/del/fid"
    fun node7BulbControl(index: Int) = "$BASE/node7/sijalica$index"
    fun node7BulbStatus(index: Int) = "$BASE/node7/sijalica$index/stanje"
    const val NODE7_DOOR_CONTROL = "$BASE/node7/vrata"
    const val NODE7_DOOR_STATUS = "$BASE/node7/vrata/stanje"

    // node8 - first floor living room + AC
    const val NODE8_HUMIDITY = "$BASE/node8/humidity"
    const val NODE8_TEMPERATURE = "$BASE/node8/temp"
    const val NODE8_GAS = "$BASE/node8/gas"
    const val NODE8_AC_MODE = "$BASE/node8/acMode"
    const val NODE8_AC_FAN = "$BASE/node8/acFan"
    const val NODE8_AC_TEMP = "$BASE/node8/acTemp"
    const val NODE8_AC_COMMAND = "$BASE/node8/aq"
    const val NODE8_MELODY = "$BASE/node8/melody"

    /** The hardware calibration offset applied to node8's raw temperature reading. */
    const val NODE8_TEMP_CALIBRATION_OFFSET = -6.0

    /** Regex used to extract the node number from an incoming topic, e.g. "bozaSub/kuca/node3/..." -> 3. */
    val NODE_NUMBER_REGEX = Regex("""bozaSub/kuca/node(\d)""")
}
