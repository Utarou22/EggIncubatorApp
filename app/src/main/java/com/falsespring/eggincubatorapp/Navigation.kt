package com.falsespring.eggincubatorapp

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.falsespring.eggincubatorapp.ui.navigation.Screen // Import your routes
import com.falsespring.eggincubatorapp.ui.dashboard.DashboardScreen // Import your screens
import com.falsespring.eggincubatorapp.ui.network.NetworkScreen
import com.falsespring.eggincubatorapp.ui.components.WebAppScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.falsespring.eggincubatorapp.ui.network.WifiConfigScreen
import com.falsespring.eggincubatorapp.ui.device.DeviceDetailScreen

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.DashBoard.route,
    ) {
        composable(Screen.DashBoard.route) {
            DashboardScreen(navController=navController)
        }

        composable(route = Screen.Network.route) {
            NetworkScreen(navController=navController)
        }

        composable(
            route = "webapp/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")
                ?: "http://192.168.4.1:8080"
            WebAppScreen(url = url)
        }

        composable(
            route = "wifi_config/{ip}",
            arguments = listOf(navArgument("ip") { type = NavType.StringType })
        ) { backStackEntry ->
            val ip = backStackEntry.arguments?.getString("ip") ?: "192.168.4.1"
            WifiConfigScreen(ip = ip, navController = navController)
        }

        composable(
            route = "device_detail/{deviceId}",
            arguments = listOf(navArgument("deviceId") {
                type = NavType.StringType
            })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")
            if (deviceId != null) {
                DeviceDetailScreen(navController = navController, deviceId = deviceId)
            }
        }

    }
}
