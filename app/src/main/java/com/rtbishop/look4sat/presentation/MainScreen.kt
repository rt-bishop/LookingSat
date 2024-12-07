package com.rtbishop.look4sat.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rtbishop.look4sat.R
import com.rtbishop.look4sat.presentation.info.infoDestination
import com.rtbishop.look4sat.presentation.map.mapDestination
import com.rtbishop.look4sat.presentation.passes.passesDestination
import com.rtbishop.look4sat.presentation.radar.radarDestination
import com.rtbishop.look4sat.presentation.satellites.satellitesDestination
import com.rtbishop.look4sat.presentation.settings.settingsDestination

sealed class Screen(var title: String, var icon: Int, var route: String) {
    data object Main : Screen("Main", R.drawable.ic_sputnik, "main")
    data object Radar : Screen("Radar", R.drawable.ic_sputnik, "radar")
    data object Satellites : Screen("Satellites", R.drawable.ic_sputnik, "satellites")
    data object Passes : Screen("Passes", R.drawable.ic_passes, "passes")
    data object Map : Screen("Map", R.drawable.ic_map, "map")
    data object Settings : Screen("Settings", R.drawable.ic_settings, "settings")
    data object Info : Screen("Info", R.drawable.ic_info, "info")
}

@Composable
fun MainScreen() {
    val outerNavController: NavHostController = rememberNavController()
    val navigateToRadar = { catNum: Int, aosTime: Long ->
        val routeWithParams = "${Screen.Radar.route}?catNum=${catNum}&aosTime=${aosTime}"
        outerNavController.navigate(routeWithParams)
    }
    val radarRoute = "${Screen.Radar.route}?catNum={catNum}&aosTime={aosTime}"
    val radarArgs = listOf(
        navArgument("catNum") { defaultValue = 0 },
        navArgument("aosTime") { defaultValue = 0L }
    )
    NavHost(navController = outerNavController, startDestination = Screen.Main.route) {
        mainDestination(navigateToRadar)
        radarDestination(radarRoute, radarArgs)
    }
}

private fun NavGraphBuilder.mainDestination(navigateToRadar: (Int, Long) -> Unit) {
    composable(Screen.Main.route) { NavBarScreen(navigateToRadar) }
}

@Composable
private fun NavBarScreen(navigateToRadar: (Int, Long) -> Unit) {
    val innerNavController: NavHostController = rememberNavController()
    val navigateToPasses = { innerNavController.navigate(Screen.Passes.route) }
    Scaffold(bottomBar = { MainNavBar(innerNavController) }) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = Screen.Passes.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            satellitesDestination(navigateToPasses)
            passesDestination(navigateToRadar)
            mapDestination()
            settingsDestination()
            infoDestination()
        }
    }
}

@Composable
private fun MainNavBar(navController: NavController) {
    val items = listOf(Screen.Satellites, Screen.Passes, Screen.Map, Screen.Settings, Screen.Info)
    val destinationRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(painterResource(item.icon), item.title) },
                label = { Text(item.title) },
                selected = destinationRoute?.contains(item.route) ?: false,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            )
        }
    }
}
