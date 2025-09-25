package com.falsespring.eggincubatorapp.ui.dashboard

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.falsespring.eggincubatorapp.data.model.Esp32Device
import com.falsespring.eggincubatorapp.data.repository.DeviceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.M)
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceRepository(application)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .build()
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _savedDevices = MutableStateFlow<List<Esp32Device>>(emptyList())
    val savedDevices: StateFlow<List<Esp32Device>> = _savedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<Map<String, Esp32Device>>(emptyMap())
    val discoveredDevices: StateFlow<List<Esp32Device>> = _discoveredDevices
        .map { it.values.toList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentSsid = MutableStateFlow("N/A")
    val currentSsid: StateFlow<String> = _currentSsid.asStateFlow()

    val phoneId: String = repository.getPhoneId()

    init {
        loadSavedDevices()
        startUdpListener()
        listenForNetworkChanges()
        startDeviceStatusPoller()
    }

    private fun loadSavedDevices() {
        _savedDevices.value = repository.getSavedDevices()
    }

    private fun listenForNetworkChanges() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                _discoveredDevices.value = emptyMap()
                updateCurrentSsid()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                _discoveredDevices.value = emptyMap()
                updateCurrentSsid()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        updateCurrentSsid()
    }

    private fun updateCurrentSsid() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiManager = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            _currentSsid.value = wifiInfo.ssid.removeSurrounding("\"")
        } else {
            _currentSsid.value = "N/A"
        }
    }

    fun pairDevice(deviceToPair: Esp32Device) {
        val newDevice = deviceToPair.copy(name = "My Incubator")
        repository.saveDevice(newDevice)
        loadSavedDevices()

        viewModelScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply { put("phone_id", phoneId) }.toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("http://${deviceToPair.ip}:8080/pair")
                .post(body)
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _savedDevices.update { list ->
                            list.map {
                                if (it.id == newDevice.id) {
                                    val onlineDevice = it.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                                    repository.saveDevice(onlineDevice) // Persist online state immediately
                                    onlineDevice
                                } else it
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("Pairing", "Network error during pairing.", e)
            }
        }
    }

    fun unpairDevice(deviceToUnpair: Esp32Device) {
        repository.removeDevice(deviceToUnpair.id)
        loadSavedDevices()
        // Code to notify the device is omitted for brevity but would go here
    }

    fun renameDevice(device: Esp32Device, newName: String) {
        viewModelScope.launch {
            val updatedDevice = device.copy(name = newName)
            repository.saveDevice(updatedDevice)
            loadSavedDevices()
            // Code to notify the device is omitted for brevity but would go here
        }
    }

    private fun startUdpListener() {
        viewModelScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            // This lock is crucial for receiving broadcast packets on modern Android
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifi = getApplication<Application>().getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifi.createMulticastLock("multicastLock").apply {
                    setReferenceCounted(true)
                    acquire()
                }

                // Listen on port 8080 on all network interfaces
                socket = DatagramSocket(8080, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true
                val buffer = ByteArray(1024)
                Log.d("UdpListener", "UDP listener started on port 8080")

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet) // This line blocks until a packet is received
                    val message = String(packet.data, 0, packet.length)

                    try {
                        val json = JSONObject(message)
                        when {
                            // Case 1: A PAIRED device sends a lightweight heartbeat
                            json.has("heartbeat") -> {
                                val deviceId = json.getString("heartbeat")
                                val ip = json.getString("ip")
                                _savedDevices.update { list ->
                                    list.map {
                                        if (it.id == deviceId) {
                                            // Mark as online and update its last seen time and potentially new IP
                                            val updatedDevice = it.copy(isOnline = true, lastSeen = System.currentTimeMillis(), ip = ip)
                                            // If the IP has changed, save the new one to storage
                                            if (it.ip != ip) {
                                                repository.saveDevice(updatedDevice)
                                            }
                                            updatedDevice
                                        } else it
                                    }
                                }
                            }
                            // Case 2: An UNPAIRED device sends a full discovery broadcast
                            json.has("device_id") -> {
                                val device = Esp32Device(
                                    id = json.getString("device_id"),
                                    ip = json.getString("ip"),
                                    name = json.getString("name"),
                                    lastSeen = System.currentTimeMillis()
                                )
                                // Add to the map of discovered devices, using ID as the key to prevent duplicates
                                _discoveredDevices.update { it + (device.id to device) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UdpListener", "Failed to parse UDP packet: $message", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("UdpListener", "Error in UDP listener", e)
            } finally {
                // Clean up resources when the listener stops
                socket?.close()
                multicastLock?.takeIf { it.isHeld }?.release()
                Log.d("UdpListener", "UDP listener stopped.")
            }
        }
    }

    private fun startDeviceStatusPoller() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10_000) // Poll every 10 seconds
                val now = System.currentTimeMillis()
                val devicesToCheck = _savedDevices.value.toList()

                devicesToCheck.forEach { device ->
                    // If a device seems online but we haven't heard from it, ping it
                    if (device.isOnline && (now - device.lastSeen > 12_000)) {
                        if (!pingDevice(device)) {
                            // Ping failed, mark it offline
                            _savedDevices.update { list ->
                                list.map {
                                    if (it.id == device.id) it.copy(isOnline = false) else it
                                }
                            }
                        }
                    }
                }

                // Cleanup stale discovered devices
                _discoveredDevices.update { currentMap ->
                    currentMap.filterValues { it.lastSeen > (now - 15000) }
                }
            }
        }
    }

    private fun pingDevice(device: Esp32Device): Boolean {
        val request = Request.Builder().url("http://${device.ip}:8080/data").build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body!!.string())
                    _savedDevices.update { list ->
                        list.map {
                            if (it.id == device.id) {
                                it.copy(
                                    lastSeen = System.currentTimeMillis(),
                                    temperature = json.getDouble("temperature").toFloat(),
                                    humidity = json.getDouble("humidity").toFloat(),
                                    isLightOn = json.getBoolean("lightState"),
                                    isFanOn = json.getBoolean("fanState"),
                                    isHumidifierOn = json.getBoolean("humidifierState")
                                )
                            } else it
                        }
                    }
                    true
                } else {
                    false
                }
            }
        } catch (e: IOException) {
            false
        }
    }

    fun getDeviceById(id: String): Esp32Device? {
        return savedDevices.value.find { it.id == id }
    }
}