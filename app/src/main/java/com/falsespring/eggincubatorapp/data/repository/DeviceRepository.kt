package com.falsespring.eggincubatorapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.falsespring.eggincubatorapp.data.model.Esp32Device
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DeviceRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PHONE_ID = "phone_id"
        private const val KEY_SAVED_DEVICES = "saved_devices"
    }

    fun getPhoneId(): String {
        var id = prefs.getString(KEY_PHONE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_PHONE_ID, id).apply()
        }
        return id
    }

    fun getSavedDevices(): List<Esp32Device> {
        val jsonString = prefs.getString(KEY_SAVED_DEVICES, "[]")
        val devices = mutableListOf<Esp32Device>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                devices.add(
                    Esp32Device(
                        id = jsonObject.getString("id"),
                        ip = jsonObject.getString("ip"),
                        name = jsonObject.optString("name", "Incubator"),
                        // Load saved status, default to false if offline
                        isOnline = jsonObject.optBoolean("isOnline", false),
                        lastSeen = jsonObject.optLong("lastSeen", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return devices
    }

    fun saveDevice(device: Esp32Device) {
        val currentDevices = getSavedDevices().toMutableList()
        currentDevices.removeAll { it.id == device.id }
        currentDevices.add(device)
        saveDeviceList(currentDevices)
    }

    fun removeDevice(deviceId: String) {
        val currentDevices = getSavedDevices().toMutableList()
        currentDevices.removeAll { it.id == deviceId }
        saveDeviceList(currentDevices)
    }

    private fun saveDeviceList(devices: List<Esp32Device>) {
        val jsonArray = JSONArray()
        devices.forEach { device ->
            val jsonObject = JSONObject().apply {
                put("id", device.id)
                put("ip", device.ip)
                put("name", device.name)
                // Save the new properties
                put("isOnline", device.isOnline)
                put("lastSeen", device.lastSeen)
            }
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(KEY_SAVED_DEVICES, jsonArray.toString()).apply()
    }
}