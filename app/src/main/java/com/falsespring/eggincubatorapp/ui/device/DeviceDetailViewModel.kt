package com.falsespring.eggincubatorapp.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falsespring.eggincubatorapp.data.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class DeviceDetailViewModel : ViewModel() {

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules

    private var scheduleJob: Job? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    fun startFetchingSchedules(deviceIp: String) {
        scheduleJob?.cancel()
        scheduleJob = viewModelScope.launch(Dispatchers.IO) {
            while(isActive) {
                fetchSchedules(deviceIp)
                delay(5000)
            }
        }
    }

    private fun fetchSchedules(deviceIp: String) {
    }

    fun addSchedule(deviceIp: String, startTime: Long, endTime: Long, targetTemp: Float, targetHumid: Float) {
        viewModelScope.launch(Dispatchers.IO) {
        }
    }

    fun removeSchedule(deviceIp: String, index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        scheduleJob?.cancel()
    }
}