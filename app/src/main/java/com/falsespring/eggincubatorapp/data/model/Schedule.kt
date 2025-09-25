package com.falsespring.eggincubatorapp.data.model

data class Schedule(
    val startTime: Long,
    val endTime: Long,
    val tempMin: Float,
    val tempMax: Float,
    val humidMin: Float,
    val humidMax: Float
)