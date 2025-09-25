package com.falsespring.eggincubatorapp.ui.network

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.falsespring.eggincubatorapp.utils.readEsp32MacPrefixes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.NetworkInterface
import kotlin.collections.iterator

data class DisplayableNetwork(val ssid: String, val bssid: String)

data class NetworkScreenUiState(
    val displayedNetworks: List<DisplayableNetwork> = emptyList(),
    val macPrefixFilter: List<String> = emptyList(),
    val userMessage: String? = null,
    val locationEnabled: Boolean = true,
    val fineLocationPermissionGranted: Boolean = true,
    val isSingleIncubatorMode: Boolean = true,
    val connectedLocalNetworkSSID: String? = null,
    val connectedLocalNetworkBSSID: String? = null
)

@RequiresApi(Build.VERSION_CODES.P)
class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NetworkScreenUiState())
    val uiState: StateFlow<NetworkScreenUiState> = _uiState.asStateFlow()

    private val wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var autoRefreshJob: Job? = null
    private var scanJob: Job? = null

    private val autoRefreshInterval = 3000L
    private var isAutoRefreshActive = false

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) processScanResults()
            }
        }
    }

    init {
        viewModelScope.launch { loadMacPrefixes() }
        checkInitialPermissionsAndSettings()
    }

    private suspend fun loadMacPrefixes() {
        val prefixes = readEsp32MacPrefixes(getApplication())
        _uiState.update { it.copy(macPrefixFilter = prefixes) }
    }

    fun checkInitialPermissionsAndSettings() {
        val context = getApplication<Application>()
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val locationEnabled = locationManager?.isLocationEnabled ?: false

        _uiState.update {
            it.copy(
                fineLocationPermissionGranted = fineLocationGranted,
                locationEnabled = locationEnabled,
                userMessage = when {
                    !fineLocationGranted -> "Location permission is required to scan for Wi-Fi networks."
                    !locationEnabled -> "Please enable Location services to scan for Wi-Fi networks."
                    else -> null
                }
            )
        }

        if (fineLocationGranted && locationEnabled) {
            startAutoRefresh()
        }
    }

    fun startAutoRefresh() {
        if (isAutoRefreshActive) return

        isAutoRefreshActive = true
        autoRefreshJob?.cancel()

        autoRefreshJob = viewModelScope.launch {
            while (isActive && _uiState.value.fineLocationPermissionGranted && _uiState.value.locationEnabled) {
                performScan()
                delay(autoRefreshInterval)
            }
        }
    }

    fun stopAutoRefresh() {
        isAutoRefreshActive = false
        autoRefreshJob?.cancel()
    }

    private fun performScan() {
        if (!_uiState.value.fineLocationPermissionGranted || !_uiState.value.locationEnabled) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                registerReceiverSafe()
                wifiManager.startScan()
            } catch (e: Exception) {
                Log.e("NetworkVM", "Auto scan error", e)
            }
        }
    }

    private fun registerReceiverSafe() {
        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            getApplication<Application>().registerReceiver(wifiScanReceiver, filter)
        } catch (_: IllegalArgumentException) { }
    }

    private fun processScanResults() {
        @Suppress("DEPRECATION")
        val results = try { wifiManager.scanResults ?: emptyList() } catch (_: SecurityException) { emptyList() }

        val prefixes = _uiState.value.macPrefixFilter
        val espNetworks = results.mapNotNull { r ->
            val ssid = r.SSID.takeIf { it.isNotBlank() }
            val bssid = r.BSSID.takeIf { it.isNotBlank() }
            if (ssid == null || bssid == null) return@mapNotNull null
            val macPrefix = bssid.replace(":", "").uppercase().take(6)
            if (!prefixes.any { it.equals(macPrefix, true) }) return@mapNotNull null
            DisplayableNetwork(ssid, bssid)
        }.distinctBy { it.bssid }
            .toMutableList()

        val connectedSSID = wifiManager.connectionInfo?.ssid?.replace("\"", "")
        val connectedBSSID = wifiManager.connectionInfo?.bssid

        if (connectedBSSID != null && connectedSSID != null) {
            val connectedPrefix = connectedBSSID.replace(":", "").uppercase().take(6)
            if (prefixes.any { it.equals(connectedPrefix, true) } &&
                espNetworks.none { it.bssid == connectedBSSID }
            ) {
                espNetworks.add(DisplayableNetwork(connectedSSID, connectedBSSID))
            }
        }

        val current = _uiState.value

        if (
            espNetworks != current.displayedNetworks ||
            connectedSSID != current.connectedLocalNetworkSSID ||
            connectedBSSID != current.connectedLocalNetworkBSSID
        ) {
            _uiState.update {
                it.copy(
                    displayedNetworks = espNetworks,
                    connectedLocalNetworkSSID = connectedSSID,
                    connectedLocalNetworkBSSID = connectedBSSID,
                    userMessage = when {
                        results.isEmpty() -> "No Wi-Fi networks detected."
                        espNetworks.isEmpty() -> "No ESP32 networks found."
                        else -> null
                    }
                )
            }
        }

        try { getApplication<Application>().unregisterReceiver(wifiScanReceiver) }
        catch (_: IllegalArgumentException) { }
    }


    fun setIncubatorMode(single: Boolean) {
        _uiState.update {
            it.copy(
                isSingleIncubatorMode = single
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
        scanJob?.cancel()
        try { getApplication<Application>().unregisterReceiver(wifiScanReceiver) } catch (_: IllegalArgumentException) { }
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getIpForBssid(bssid: String?): String? {
        return null
    }
}