package com.falsespring.eggincubatorapp.utils.network
    
    import android.content.Context
    import android.net.*
    import android.net.wifi.WifiConfiguration
    import android.net.wifi.WifiManager
    import android.net.wifi.WifiNetworkSpecifier
    import android.os.Build
    import android.os.Handler
    import android.os.Looper
    import android.widget.Toast

@Suppress("DEPRECATION")
    fun connectToWifi(
        context: Context,
        ssid: String,
        password: String? = null,
        timeoutMs: Long = 30000L,
        onConnected: () -> Unit,
        onFailed: (() -> Unit)? = null,
        onReadyToOpenWeb: (() -> Unit)? = null
    ) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .apply { if (!password.isNullOrEmpty()) setWpa2Passphrase(password) }
                .build()
    
            val request = NetworkRequest.Builder()
                .addTransportType(  NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()
    
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        connectivityManager.bindProcessToNetwork(network)
                    }
                    onReadyToOpenWeb?.invoke()
                }
    
                override fun onUnavailable() {
                    onFailed?.invoke()
                }
            })
        }
        else {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                allowedKeyManagement.set(if (password.isNullOrEmpty()) WifiConfiguration.KeyMgmt.NONE else WifiConfiguration.KeyMgmt.WPA_PSK)
                if (!password.isNullOrEmpty()) preSharedKey = "\"$password\""
            }
    
            val netId = wifiManager.addNetwork(config)
            if (netId != -1) {
                wifiManager.isWifiEnabled = true
                wifiManager.disconnect()
                val netId = wifiManager.addNetwork(config)
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
    
                Handler(Looper.getMainLooper()).postDelayed({
                    val info = wifiManager.connectionInfo
                    if (info.ssid.replace("\"", "") == ssid) {
                        onConnected()
                    } else {
                        Toast.makeText(context, "Failed to connect to $ssid", Toast.LENGTH_SHORT).show()
                        onFailed?.invoke()
                    }
                }, 5000)
            } else {
                Toast.makeText(context, "Failed to add network $ssid", Toast.LENGTH_SHORT).show()
                onFailed?.invoke()
            }
        }
    }