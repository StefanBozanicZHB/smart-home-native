package zhb.company.smart_home.ui.navigation

sealed class Destination(val route: String) {
    data object Auth : Destination("auth")
    data object MqttConnect : Destination("mqtt_connect")
    data object Home : Destination("home")
    data object Garden : Destination("garden")
    data object Heating : Destination("heating")
    data object House : Destination("house")
    data object SmallHome : Destination("small_home")
    data object System : Destination("system")
    data object HeatingSensors : Destination("heating_sensors")
    data object HeatingCalibration : Destination("heating_calibration")
    data object Notifications : Destination("notifications")
    data object Reports : Destination("heating_reports")
    data object ReportDetail : Destination("heating_report/{date}") {
        const val ARG = "date"
        fun of(date: String) = "heating_report/$date"
    }
    data object NodeDetail : Destination("node/{nodeId}") {
        const val ARG = "nodeId"
        fun of(nodeId: String) = "node/$nodeId"
    }
}
