package zhb.company.smart_home.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import zhb.company.smart_home.R
import zhb.company.smart_home.data.mqtt.SmartHomeRepository
import zhb.company.smart_home.ui.auth.AuthScreen
import zhb.company.smart_home.ui.components.Motion
import zhb.company.smart_home.ui.garden.GardenScreen
import zhb.company.smart_home.ui.garden.IrrigationPlanScreen
import zhb.company.smart_home.ui.garden.LightSchedulesScreen
import zhb.company.smart_home.ui.heating.HeatingScreen
import zhb.company.smart_home.ui.heating.PipeCalibrationScreen
import zhb.company.smart_home.ui.heating.PipeSensorsScreen
import zhb.company.smart_home.ui.home.HomeScreen
import zhb.company.smart_home.ui.house.HouseScreen
import zhb.company.smart_home.ui.mqttconnect.MqttConnectScreen
import zhb.company.smart_home.ui.notifications.NotificationsScreen
import zhb.company.smart_home.ui.reports.ReportDetailScreen
import zhb.company.smart_home.ui.reports.ReportsScreen
import zhb.company.smart_home.ui.system.NodeDetailScreen
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

private const val FADE_MS = 220

/** A screen opened from a tab (not a tab, not the sign-in flow). */
private fun isSubScreen(route: String?): Boolean =
    route != null && route !in FLOW_ROUTES && TABS.none { it.destination.route == route }

/** A sub-screen entering from the right edge. */
private fun slideIn(): EnterTransition =
    slideInHorizontally(Motion.standard(Motion.LONG_MS)) { width -> width } + fadeIn(tween(Motion.MEDIUM_MS))

/** The screen under a new sub-screen moves a little to the left and fades. */
private fun slideOutBehind(): ExitTransition =
    slideOutHorizontally(Motion.standard(Motion.LONG_MS)) { width -> -width / 4 } + fadeOut(tween(Motion.LONG_MS))

/** Back: the screen under the sub-screen returns from the left. */
private fun slideInFromBehind(): EnterTransition =
    slideInHorizontally(Motion.standard(Motion.LONG_MS)) { width -> -width / 4 } + fadeIn(tween(Motion.LONG_MS))

/** Back: the sub-screen leaves to the right. */
private fun slideOut(): ExitTransition =
    slideOutHorizontally(Motion.standard(Motion.LONG_MS)) { width -> width } + fadeOut(tween(Motion.MEDIUM_MS))

/** Routes without the bottom bar (sign-in flow). */
private val FLOW_ROUTES = setOf(Destination.Auth.route, Destination.MqttConnect.route)

@Composable
fun SmartHomeNavGraph(
    repository: SmartHomeRepository = koinInject(),
    pendingNavigation: PendingNavigation = koinInject(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val pendingRoute by pendingNavigation.route.collectAsState()

    // A screen requested from outside (tapped notification) opens once sign-in is done.
    LaunchedEffect(currentRoute, pendingRoute) {
        if (pendingRoute != null && currentRoute != null && currentRoute !in FLOW_ROUTES && repository.hasSession) {
            pendingNavigation.consume()?.let { navController.navigate(it) { launchSingleTop = true } }
        }
    }

    // After the system kills the process, Android restores the screens but not the sign-in
    // session (token, broker config live only in memory): start again from sign-in.
    LaunchedEffect(currentRoute) {
        if (currentRoute != null && currentRoute !in FLOW_ROUTES && !repository.hasSession) {
            navController.navigateReplacing(Destination.Auth.route)
        }
    }
    // Sub-screens keep the tab they were opened from selected.
    val selectedRoute = when (currentRoute) {
        Destination.NodeDetail.route -> Destination.System.route
        Destination.HeatingSensors.route, Destination.HeatingCalibration.route,
        Destination.Reports.route, Destination.ReportDetail.route -> Destination.Heating.route
        Destination.Notifications.route -> Destination.Home.route
        Destination.IrrigationPlan.route, Destination.LightSchedules.route -> Destination.Garden.route
        else -> currentRoute
    }
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
            // Tabs and the sign-in flow cross-fade. A sub-screen (plan, sensors, reports, node
            // detail…) slides in from the right and back out to the right, so going deeper and
            // coming back can be seen; the screen under it moves a little the other way. The
            // predictive back gesture uses the same pair (its default shrinks the leaving screen).
            enterTransition = {
                if (isSubScreen(targetState.destination.route)) slideIn() else fadeIn(tween(FADE_MS))
            },
            exitTransition = {
                if (isSubScreen(targetState.destination.route)) slideOutBehind() else fadeOut(tween(FADE_MS))
            },
            popEnterTransition = {
                if (isSubScreen(initialState.destination.route)) slideInFromBehind() else fadeIn(tween(FADE_MS))
            },
            popExitTransition = {
                if (isSubScreen(initialState.destination.route)) slideOut() else fadeOut(tween(FADE_MS))
            },
            predictivePopEnterTransition = { _ ->
                if (isSubScreen(initialState.destination.route)) slideInFromBehind() else fadeIn(tween(FADE_MS))
            },
            predictivePopExitTransition = { _ ->
                if (isSubScreen(initialState.destination.route)) slideOut() else fadeOut(tween(FADE_MS))
            },
            sizeTransform = null,
        ) {
            composable(Destination.Auth.route) {
                AuthScreen(onAuthenticated = { navController.navigateReplacing(Destination.MqttConnect.route) })
            }
            composable(Destination.MqttConnect.route) {
                MqttConnectScreen(
                    onConnected = { navController.navigateReplacing(Destination.Home.route) },
                    onSignInAgain = { navController.navigateReplacing(Destination.Auth.route) },
                )
            }
            composable(Destination.Home.route) {
                HomeScreen(
                    onOpenHeating = { tab(Destination.Heating) },
                    onOpenGarden = { tab(Destination.Garden) },
                    onOpenHouse = { tab(Destination.House) },
                    onOpenNotifications = { navController.navigate(Destination.Notifications.route) },
                )
            }
            composable(Destination.Garden.route) {
                GardenScreen(
                    onOpenPlan = { navController.navigate(Destination.IrrigationPlan.route) },
                    onOpenSchedules = { navController.navigate(Destination.LightSchedules.route) },
                )
            }
            composable(Destination.IrrigationPlan.route) {
                IrrigationPlanScreen(onBack = { navController.navigateUp() })
            }
            composable(Destination.LightSchedules.route) {
                LightSchedulesScreen(onBack = { navController.navigateUp() })
            }
            composable(Destination.Heating.route) {
                HeatingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSensors = { navController.navigate(Destination.HeatingSensors.route) },
                    onOpenReports = { navController.navigate(Destination.Reports.route) },
                )
            }
            composable(Destination.Notifications.route) {
                NotificationsScreen(
                    onBack = { navController.navigateUp() },
                    onOpenReport = { navController.navigate(Destination.ReportDetail.of(it)) },
                )
            }
            composable(Destination.Reports.route) {
                ReportsScreen(onOpen = { navController.navigate(Destination.ReportDetail.of(it)) }, onBack = { navController.navigateUp() })
            }
            composable(Destination.ReportDetail.route) { entry ->
                ReportDetailScreen(date = entry.arguments?.getString(Destination.ReportDetail.ARG).orEmpty(), onBack = { navController.navigateUp() })
            }
            composable(Destination.House.route) {
                HouseScreen()
            }
            composable(Destination.HeatingSensors.route) {
                PipeSensorsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCalibration = { navController.navigate(Destination.HeatingCalibration.route) },
                )
            }
            composable(Destination.HeatingCalibration.route) {
                PipeCalibrationScreen(onDone = { navController.popBackStack() })
            }
            composable(Destination.System.route) {
                SystemScreen(onOpenNode = { navController.navigate(Destination.NodeDetail.of(it)) })
            }
            composable(Destination.NodeDetail.route) { entry ->
                NodeDetailScreen(nodeId = entry.arguments?.getString(Destination.NodeDetail.ARG).orEmpty(), onBack = { navController.navigateUp() })
            }
        }
    }
}
