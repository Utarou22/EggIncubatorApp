package com.falsespring.eggincubatorapp.ui.dashboard

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.falsespring.eggincubatorapp.data.model.Esp32Device
import com.falsespring.eggincubatorapp.ui.navigation.Screen
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: DashboardViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val savedDevices by viewModel.savedDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val currentSsid by viewModel.currentSsid.collectAsState()

    var showRenameDialog by remember { mutableStateOf<Esp32Device?>(null) }
    var showUnpairDialog by remember { mutableStateOf<Esp32Device?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Hatchly Dashboard") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(Screen.Network.route) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add New Device")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Current Network", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentSsid,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    "My Devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (savedDevices.isEmpty()) {
                item {
                    Text(
                        "No devices have been added yet.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(items = savedDevices, key = { it.id }) { device ->
                    Card(
                        onClick = {
                            navController.navigate("device_detail/${device.id}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        SavedDeviceCard(
                            device = device,
                            onRenameClick = { showRenameDialog = device },
                            onUnpairClick = { showUnpairDialog = device }
                        )
                    }
                }
            }

            item {
                Text(
                    "Discovered Devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp)
                )
            }
            val unassignedDiscovered: List<Esp32Device> =
                discoveredDevices.filter { discovered -> savedDevices.none { saved -> saved.id == discovered.id } }
            if (unassignedDiscovered.isEmpty()) {
                item {
                    Text(
                        "Searching...",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(items = unassignedDiscovered, key = { it.id }) { device ->
                    DiscoveredDeviceCard(
                        device = device,
                        onAddClick = { viewModel.pairDevice(device) }
                    )
                }
            }
        }
    }

    showRenameDialog?.let { device ->
        var newName by remember { mutableStateOf(device.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Incubator") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Device Name") })
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameDevice(device, newName)
                    showRenameDialog = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel") } }
        )
    }

    showUnpairDialog?.let { device ->
        AlertDialog(
            onDismissRequest = { showUnpairDialog = null },
            title = { Text("Unpair Device?") },
            text = { Text("This will release the incubator and allow it to be paired by another device. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.unpairDevice(device)
                        showUnpairDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Unpair") }
            },
            dismissButton = { TextButton(onClick = { showUnpairDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SavedDeviceCard(
    device: Esp32Device,
    onRenameClick: () -> Unit,
    onUnpairClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (device.isOnline) Color(0xFF4CAF50) else Color.Gray,
                    shape = CircleShape
                )
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text("ID: ${device.id}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            if (device.isOnline) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Online",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${String.format("%.1f", device.temperature)}°C",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${String.format("%.1f", device.humidity)}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Device Options"
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        onRenameClick()
                        menuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Unpair") },
                    onClick = {
                        onUnpairClick()
                        menuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DiscoveredDeviceCard(device: Esp32Device, onAddClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("New Incubator Found", style = MaterialTheme.typography.titleMedium)
                Text("ID: ${device.id}", style = MaterialTheme.typography.bodySmall)
                Text("IP: ${device.ip}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onAddClick) { Text("Add") }
        }
    }
}