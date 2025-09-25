package com.falsespring.eggincubatorapp.data.model

data class DeviceState(
    val temperature: Float = 0.0f,
    val humidity: Float = 0.0f,
    val isLightOn: Boolean = false,
    val isFanOn: Boolean = false,
    val isHumidifierOn: Boolean = false,
    val controlMode: String = "AUTO",
    val connectionStatus: String = "Connecting..."
)