package com.falsespring.eggincubatorapp.data.model

data class Esp32Device(
    val id: String,
    val ip: String,
    val name: String = "Incubator",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val temperature: Float = 0.0f,
    val humidity: Float = 0.0f,
    val isLightOn: Boolean = false,
    val isFanOn: Boolean = false,
    val isHumidifierOn: Boolean = false
)