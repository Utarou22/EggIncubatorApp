package com.falsespring.eggincubatorapp.ui.navigation

sealed class Screen(val route: String) {
    object DashBoard : Screen("main_screen")
    object Network : Screen("network_screen")

    object DeviceDetail : Screen("device_detail/{deviceName}/{deviceIp}")
}