package com.falsespring.eggincubatorapp.ui.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiConfigScreen(ip: String = "192.168.4.1", navController: NavController) {
    val context = LocalContext.current
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var networks by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scanWifi(context) { results ->
                    networks = results.filter { it.SSID.isNotEmpty() }
                        .distinctBy { it.SSID }
                        .sortedByDescending { it.level }
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Location permission required for Wi-Fi scan")
                }
            }
        }

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            scanWifi(context) { results ->
                networks = results.filter { it.SSID.isNotEmpty() }
                    .distinctBy { it.SSID }
                    .sortedByDescending { it.level }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Wi-Fi") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val focusManager = LocalFocusManager.current

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("SSID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !isLoading
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                enabled = !isLoading
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    isLoading = true
                    scope.launch {
                        if (!isConnectedToEsp32(context, "Incubator_Setup")) {
                            snackbarHostState.showSnackbar("Error: Not connected to the 'Incubator_Setup' Wi-Fi network.")
                            isLoading = false
                            return@launch
                        }

                        if (ssid.isBlank()) {
                            snackbarHostState.showSnackbar("Please select or enter a Wi-Fi network SSID.")
                            isLoading = false
                            return@launch
                        }

                        val success = sendWifiConfig(ip, ssid, password)
                        if (success) {
                            snackbarHostState.showSnackbar(
                                message = "Success! Device is rebooting to connect to '$ssid'. Reconnect your phone to your home Wi-Fi.",
                                duration = SnackbarDuration.Long
                            )
                        } else {
                            snackbarHostState.showSnackbar("Failed to send configuration. Please try again.")
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save & Connect")
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Nearby Networks", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(networks) { network ->
                    ListItem(
                        headlineContent = { Text(network.SSID) },
                        modifier = Modifier.clickable(enabled = !isLoading) {
                            ssid = network.SSID
                        }
                    )
                    Divider()
                }
            }
        }
    }
}

@RequiresPermission(
    allOf = [
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION
    ]
)
fun scanWifi(context: Context, onResults: (List<ScanResult>) -> Unit) {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                @Suppress("MissingPermission")
                onResults(wifiManager.scanResults)
            } else {
                onResults(emptyList())
            }
            context.unregisterReceiver(this)
        }
    }

    val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
    context.registerReceiver(broadcastReceiver, intentFilter)

    if (!wifiManager.startScan()) {
        @Suppress("MissingPermission")
        onResults(wifiManager.scanResults)
        context.unregisterReceiver(broadcastReceiver)
    }
}
@RequiresApi(Build.VERSION_CODES.M)
@RequiresPermission(allOf = [Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE])
fun isConnectedToEsp32(context: Context, expectedSsid: String): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val currentSsid = wifiInfo.ssid.removeSurrounding("\"")
        return currentSsid == expectedSsid
    }
    return false
}

suspend fun sendWifiConfig(ip: String, ssid: String, password: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        val formBody = FormBody.Builder()
            .add("s", ssid)
            .add("p", password)
            .build()

        val request = Request.Builder()
            .url("http://$ip/wifisave")
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            return@withContext response.isSuccessful
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}