package com.falsespring.eggincubatorapp.utils.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.falsespring.eggincubatorapp.data.model.Esp32Device

class NsdHelper(
    context: Context,
    private val onDeviceDiscovered: (Esp32Device) -> Unit
) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_http._tcp."
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery() {
        stopDiscovery()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NsdHelper", "Service found: ${service.serviceName}")
                if (service.serviceType.contains(serviceType)) {
                    nsdManager.resolveService(service, createResolveListener())
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e("NsdHelper", "Service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i("NsdHelper", "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery failed: Error code: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Stop Discovery failed: Error code: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun createResolveListener(): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.i("NsdHelper", "Resolve Succeeded. $serviceInfo")
                val deviceIdAttribute = serviceInfo.attributes["deviceId"]
                val deviceId = if (deviceIdAttribute != null) {
                    String(deviceIdAttribute, Charsets.UTF_8)
                } else {
                    "unknown_${serviceInfo.host.hostAddress}"
                }

                val device = Esp32Device(
                    id = deviceId,
                    ip = serviceInfo.host.hostAddress ?: "0.0.0.0",
                    name = serviceInfo.serviceName
                )
                onDeviceDiscovered(device)
            }
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NsdHelper", "Error stopping discovery", e)
            }
            discoveryListener = null
        }
    }
}