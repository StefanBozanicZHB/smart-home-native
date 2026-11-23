package zhb.company.smart_home.data.mqtt

/** Every MQTT topic used by the app, centralized (the Flutter source left many of these inline). */
object MqttTopics {
    const val SUBSCRIBE_ALL = "bozaSub/#"
    private const val BASE = "bozaSub/kuca"

    // node1 - fountain and sprinklers: new JSON API, see data/garden/Node1Repository.kt.

    /**
     * `s` on this topic: node7 (shed), the only node still on old firmware, publishes its state.
     * Nodes with the new firmware ignore it. Remove once node7 is migrated or dropped.
     */
    const val LEGACY_STATE_REQUEST = "$BASE/node1/stanje"

    // node2 - yard (node2-2.0.0): outdoor temperature from a DS18B20, °C; deleted (empty retained
    // message) after 5 min without a valid reading. Humidity is no longer measured. The lights use
    // the JSON API, see data/garden/Node2Repository.kt.
    const val NODE2_TEMPERATURE = "$BASE/node2/temperature"

    // node3 - front door: JSON API (node3-2.0.0), see data/door/Node3Repository.kt.

    // node4 - ground floor living room
    const val NODE4_HUMIDITY = "$BASE/node4/humidity"
    const val NODE4_TEMPERATURE = "$BASE/node4/temperature"

    // node5 - heating: new JSON API with a switchable node id, see data/heating/Node5Topics.kt

    // node6 - pool (node6-2.0.0): water temperature, °C; deleted (empty retained message) after
    // 5 min without a valid reading. The pump uses the JSON API, see data/garden/Node6Repository.kt.
    const val NODE6_TEMPERATURE = "$BASE/node6/temperature"

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

    // node8 - living room, upper floor (node8-2.0.0, the same as node4): AHT10, calibrated on the
    // node; values deleted (empty retained message) after 5 min without a valid reading.
    const val NODE8_TEMPERATURE = "$BASE/node8/temperature"
    const val NODE8_HUMIDITY = "$BASE/node8/humidity"

    /** Regex used to extract the node number from an incoming topic, e.g. "bozaSub/kuca/node3/..." -> 3. */
    val NODE_NUMBER_REGEX = Regex("""bozaSub/kuca/node(\d)""")
}
