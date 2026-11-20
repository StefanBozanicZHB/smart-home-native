package zhb.company.smart_home.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import zhb.company.smart_home.R
import zhb.company.smart_home.ui.auth.AuthScreen
import zhb.company.smart_home.ui.garden.GardenScreen
import zhb.company.smart_home.ui.heating.HeatingScreen
import zhb.company.smart_home.ui.home.HomeScreen
import zhb.company.smart_home.ui.house.HouseScreen
import zhb.company.smart_home.ui.mqttconnect.MqttConnectScreen
import zhb.company.smart_home.ui.smallhome.SmallHomeScreen
import zhb.company.smart_home.ui.system.SystemScreen

/**
 * Navigates through the auth/connect flow with the entire back stack cleared (popUpTo the graph's
 * own id, inclusive) so Auth/MqttConnect never linger underneath Home - system back from Home
 * exits the app instead of resurfacing the login screen.
 */
private fun NavController.navigateReplacing(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Switches bottom-navigation tabs, keeping one entry per tab and each tab's scroll state. */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Destination.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class Tab(val destination: Destination, val icon: ImageVector, val label: Int)

private val TABS = listOf(
    Tab(Destination.Home, Icons.Outlined.Home, R.string.home_tab_home),
    Tab(Destination.Heating, Icons.Outlined.LocalFireDepartment, R.string.home_tab_heating),
    Tab(Destination.Garden, Icons.Outlined.Grass, R.string.home_tab_garden),
    Tab(Destination.House, Icons.Outlined.Chair, R.string.home_tab_house),
    Tab(Destination.System, Icons.Outlined.Memory, R.string.home_tab_system),
)

/** Routes without the bottom bar (sign-in flow). */
private val FLOW_ROUTES = setOf(Destination.Auth.route, Destination.MqttConnect.route)

@Composable
fun SmartHomeNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // The shed is opened from the system tab, so that tab stays selected there.
    val selectedRoute = if (currentRoute == Destination.SmallHome.route) Destination.System.route else currentRoute
    val tab: (Destination) -> Unit = { navController.navigateToTab(it.route) }

    Scaffold(
        bottomBar = {
            if (currentRoute != null && currentRoute !in FLOW_ROUTES) {
                NavigationBar {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = selectedRoute == t.destination.route,
                            onClick = { tab(t.destination) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Auth.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Destination.Auth.route) {
                AuthScreen(onAuthenticated = { navController.navigateReplacing(Destination.MqttConnect.route) })
            }
            composable(Destination.MqttConnect.route) {
                MqttConnectScreen(onConnected = { navController.navigateReplacing(Destination.Home.route) })
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenHeating = { tab(Destination.Heating) },
                    onOpenGarden = { tab(Destination.Garden) },
                    onOpenHouse = { tab(Destination.House) },
                )
            }
            composable(Destination.Garden.route) {
                GardenScreen(
                    onOpenHome = { tab(Destination.Home) },
                    onOpenHeating = { tab(Destination.Heating) },
                    onOpenHouse = { tab(Destination.House) },
                    onOpenShed = { navController.navigate(Destination.SmallHome.route) },
                )
            }
            composable(Destination.Heating.route) {
                HeatingScreen(
                    onOpenHome = { tab(Destination.Home) },
                    onOpenGarden = { tab(Destination.Garden) },
                    onOpenHouse = { tab(Destination.House) },
                    onOpenShed = { navController.navigate(Destination.SmallHome.route) },
                )
            }
            composable(Destination.House.route) {
                HouseScreen(
                    onOpenHome = { tab(Destination.Home) },
                    onOpenGarden = { tab(Destination.Garden) },
                    onOpenHeating = { tab(Destination.Heating) },
                    onOpenShed = { navController.navigate(Destination.SmallHome.route) },
                )
            }
            composable(Destination.System.route) {
                SystemScreen(onOpenShed = { navController.navigate(Destination.SmallHome.route) })
            }
            composable(Destination.SmallHome.route) {
                SmallHomeScreen(
                    onOpenHome = { tab(Destination.Home) },
                    onOpenGarden = { tab(Destination.Garden) },
                    onOpenHeating = { tab(Destination.Heating) },
                    onOpenHouse = { tab(Destination.House) },
                )
            }
        }
    }
}
