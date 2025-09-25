package com.falsespring.eggincubatorapp.ui.network

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.falsespring.eggincubatorapp.ui.components.NetworkCard
import kotlinx.coroutines.delay

@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    networkViewModel: NetworkViewModel = viewModel()
) {
    val uiState by networkViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        networkViewModel.checkInitialPermissionsAndSettings()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(500) // Small delay to ensure initialization
            networkViewModel.checkInitialPermissionsAndSettings()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> networkViewModel.startAutoRefresh()
                Lifecycle.Event.ON_PAUSE -> networkViewModel.stopAutoRefresh()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            networkViewModel.stopAutoRefresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Networks") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.Wifi, contentDescription = "Wi-Fi Settings")
                    }
                }
            )
        },
        bottomBar = {
            IncubatorModeToggle(
                selectedMode = if (uiState.isSingleIncubatorMode) "S" else "M",
                onModeSelected = { selected ->
                    networkViewModel.setIncubatorMode(selected == "S")
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            when {
                !uiState.fineLocationPermissionGranted -> {
                    PermissionOrSettingRequired(
                        message = "Location permission is required to find Wi-Fi networks.",
                        buttonText = "Grant Permission",
                        onClick = { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                    return@Column
                }
                !uiState.locationEnabled -> {
                    PermissionOrSettingRequired(
                        message = "Location services must be enabled to find Wi-Fi networks.",
                        buttonText = "Open Location Settings",
                        onClick = {
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                    return@Column
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.displayedNetworks.isNotEmpty()) {
                Text(
                    text = "Available ESP32 Networks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            val networksToShow = remember(uiState.displayedNetworks, uiState.isSingleIncubatorMode) {
                if (uiState.isSingleIncubatorMode) {
                    uiState.displayedNetworks
                } else {
                    uiState.displayedNetworks
                }
            }

            if (networksToShow.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(networksToShow, key = { it.bssid ?: it.ssid }) { network ->
                        NetworkCard(
                            ssid = network.ssid,
                            isConnected = network.bssid == uiState.connectedLocalNetworkBSSID,
                            onClick = if (uiState.isSingleIncubatorMode) {
                                if (network.bssid == uiState.connectedLocalNetworkBSSID) {
                                    {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("http://192.168.4.1:8080")
                                        )
                                        context.startActivity(intent)
                                    }
                                } else null
                            } else {
                                if (network.bssid == uiState.connectedLocalNetworkBSSID) {
                                    {
                                        val ip = "192.168.4.1" // still the ESP32 AP gateway
                                        navController.navigate("wifi_config/$ip")
                                    }
                                } else null
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
                Text(
                    "Searching for ESP32 networks...",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun NetworkDashboard(
    connectedSSID: String?,
    connectedBSSID: String?,
    espBSSIDs: List<String>,
    onOpenWeb: () -> Unit
) {
    val isConnectedToESP32 = connectedBSSID != null && espBSSIDs.contains(connectedBSSID)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Currently Connected: ${connectedSSID ?: "None"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onOpenWeb,
                    enabled = isConnectedToESP32
                ) {
                    Text("Open Incubator Control Panel")
                }
            }
        }
    }
}

@Composable
fun PermissionOrSettingRequired(message: String, buttonText: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Warning",
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
fun IncubatorModeToggle(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeButton("S", "Single", selectedMode == "S") { onModeSelected("S") }
                ModeButton("M", "Multi", selectedMode == "M") { onModeSelected("M") }
            }

            Text(
                text = if (selectedMode == "S") "Single Incubator Mode" else "Multi Incubator Mode",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun ModeButton(symbol: String, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.2f
                    ),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Preview(showBackground = true)
@Composable
fun NetworkScreenPreview() {
    val navController = rememberNavController()
    NetworkScreen(navController = navController)
}
