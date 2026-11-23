package zhb.company.smart_home.data.mqtt

/**
 * Plain MQTT topics read by [SmartHomeRepository]. Nodes with a JSON API have their own topics
 * next to their repositories: node1, node2 and node6 in data/garden, node3 in data/door, node5 in
 * data/heating. Measurements are deleted by the node (empty retained message) after 5 min
 * without a valid reading; the app then shows "--".
 */
object MqttTopics {
    const val SUBSCRIBE_ALL = "bozaSub/#"
    private const val BASE = "bozaSub/kuca"

    // node2 - yard: outdoor temperature (DS18B20), °C
    const val NODE2_TEMPERATURE = "$BASE/node2/temperature"

    // node4 - living room, ground floor: AHT10, calibrated on the node
    const val NODE4_TEMPERATURE = "$BASE/node4/temperature"
    const val NODE4_HUMIDITY = "$BASE/node4/humidity"

    // node6 - pool: water temperature (DS18B20), °C
    const val NODE6_TEMPERATURE = "$BASE/node6/temperature"

    // node8 - living room, upper floor: the same as node4
    const val NODE8_TEMPERATURE = "$BASE/node8/temperature"
    const val NODE8_HUMIDITY = "$BASE/node8/humidity"
}
