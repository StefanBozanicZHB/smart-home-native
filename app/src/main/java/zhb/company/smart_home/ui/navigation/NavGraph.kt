package zhb.company.smart_home.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import zhb.company.smart_home.ui.auth.AuthScreen
import zhb.company.smart_home.ui.garden.GardenScreen
import zhb.company.smart_home.ui.heating.HeatingScreen
import zhb.company.smart_home.ui.home.HomeScreen
import zhb.company.smart_home.ui.house.HouseScreen
import zhb.company.smart_home.ui.mqttconnect.MqttConnectScreen
import zhb.company.smart_home.ui.settings.ConnectionsDialog
import zhb.company.smart_home.ui.settings.SettingsDialog
import zhb.company.smart_home.ui.smallhome.SmallHomeScreen

/**
 * Navigates through the auth/connect flow with the entire back stack cleared (popUpTo the graph's
 * own id, inclusive) so Auth/MqttConnect never linger underneath Home - system back from Home
 * exits the app instead of resurfacing the login screen.
 */
private fun androidx.navigation.NavController.navigateReplacing(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun SmartHomeNavGraph() {
    val navController = rememberNavController()
    var showSettings by remember { mutableStateOf(false) }
    var showConnections by remember { mutableStateOf(false) }

    fun openRoom(route: String) {
        navController.navigate(route) {
            popUpTo(Destination.Home.route) { inclusive = false; saveState = false }
            launchSingleTop = true
        }
    }

    Box {
        NavHost(navController = navController, startDestination = Destination.Auth.route) {
            composable(Destination.Auth.route) {
                AuthScreen(onAuthenticated = { navController.navigateReplacing(Destination.MqttConnect.route) })
            }
            composable(Destination.MqttConnect.route) {
                MqttConnectScreen(onConnected = { navController.navigateReplacing(Destination.Home.route) })
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenGarden = { openRoom(Destination.Garden.route) },
                    onOpenHeating = { openRoom(Destination.Heating.route) },
                    onOpenHouse = { openRoom(Destination.House.route) },
                    onOpenShed = { openRoom(Destination.SmallHome.route) },
                    onOpenConnections = { showConnections = true },
                    onOpenSettings = { showSettings = true },
                )
            }
            composable(Destination.Garden.route) {
                GardenScreen(
                    onOpenHome = { openRoom(Destination.Home.route) },
                    onOpenHeating = { openRoom(Destination.Heating.route) },
                    onOpenHouse = { openRoom(Destination.House.route) },
                    onOpenShed = { openRoom(Destination.SmallHome.route) },
                )
            }
            composable(Destination.Heating.route) {
                HeatingScreen(
                    onOpenHome = { openRoom(Destination.Home.route) },
                    onOpenGarden = { openRoom(Destination.Garden.route) },
                    onOpenHouse = { openRoom(Destination.House.route) },
                    onOpenShed = { openRoom(Destination.SmallHome.route) },
                )
            }
            composable(Destination.House.route) {
                HouseScreen(
                    onOpenHome = { openRoom(Destination.Home.route) },
                    onOpenGarden = { openRoom(Destination.Garden.route) },
                    onOpenHeating = { openRoom(Destination.Heating.route) },
                    onOpenShed = { openRoom(Destination.SmallHome.route) },
                )
            }
            composable(Destination.SmallHome.route) {
                SmallHomeScreen(
                    onOpenHome = { openRoom(Destination.Home.route) },
                    onOpenGarden = { openRoom(Destination.Garden.route) },
                    onOpenHeating = { openRoom(Destination.Heating.route) },
                    onOpenHouse = { openRoom(Destination.House.route) },
                )
            }
        }

        if (showSettings) SettingsDialog(onDismiss = { showSettings = false })
        if (showConnections) ConnectionsDialog(onDismiss = { showConnections = false })
    }
}
