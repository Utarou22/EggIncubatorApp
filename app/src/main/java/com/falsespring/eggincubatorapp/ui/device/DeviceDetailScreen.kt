package com.falsespring.eggincubatorapp.ui.device

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vanpra.composematerialdialogs.MaterialDialog
import com.vanpra.composematerialdialogs.datetime.date.datepicker
import com.vanpra.composematerialdialogs.datetime.time.timepicker
import com.vanpra.composematerialdialogs.rememberMaterialDialogState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.map
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import com.falsespring.eggincubatorapp.data.model.Schedule
import com.falsespring.eggincubatorapp.ui.dashboard.DashboardViewModel

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    navController: NavController,
    deviceId: String,
    viewModel: DeviceDetailViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val device by dashboardViewModel.savedDevices
        .map { deviceList -> deviceList.find { it.id == deviceId } }
        .collectAsState(initial = null)

    val schedules by viewModel.schedules.collectAsState()
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    LaunchedEffect(device?.ip) {
        device?.ip?.let { ip ->
            viewModel.startFetchingSchedules(ip)
        }
    }

    device?.let { currentDevice ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentDevice.name) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddScheduleDialog = true }) {
                    Icon(Icons.Default.Add, "Add Schedule")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    Text(
                        text = if (currentDevice.isOnline) "Online" else "Offline",
                        color = if (currentDevice.isOnline) Color(0xFF4CAF50) else Color.Gray,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard("Temperature", "${String.format("%.1f", currentDevice.temperature)} °C")
                        MetricCard("Humidity", "${String.format("%.1f", currentDevice.humidity)} %")
                    }
                    Divider()
                }

                item {
                    Text(
                        "Component Status",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                item { StatusRow("Heater / Light", currentDevice.isLightOn) }
                item { StatusRow("Fan", currentDevice.isFanOn) }
                item { StatusRow("Humidifier", currentDevice.isHumidifierOn) }

                item {
                    Divider(modifier = Modifier.padding(top = 16.dp))
                    Text(
                        "Programmed Schedules",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                if (schedules.isEmpty()) {
                    item {
                        Text(
                            "No schedules programmed. Tap '+' to add one.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    itemsIndexed(schedules) { index, schedule ->
                        ScheduleCard(
                            schedule = schedule,
                            onDelete = {
                                viewModel.removeSchedule(currentDevice.ip, index)
                            }
                        )
                    }
                }
            }
        }

        if (showAddScheduleDialog) {
            AddScheduleDialog(
                onDismiss = { showAddScheduleDialog = false },
                onConfirm = { startTime, endTime, temp, humid ->
                    viewModel.addSchedule(currentDevice.ip, startTime, endTime, temp, humid)
                    showAddScheduleDialog = false
                }
            )
        }
    }
}
@Composable
fun ScheduleCard(schedule: Schedule, onDelete: () -> Unit) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    val zoneId = ZoneId.systemDefault()

    val startTime = Instant.ofEpochSecond(schedule.startTime).atZone(zoneId)
    val endTime = Instant.ofEpochSecond(schedule.endTime).atZone(zoneId)
    val now = Instant.now().atZone(zoneId)

    val isActive = now.isAfter(startTime) && now.isBefore(endTime)
    val targetTemp = (schedule.tempMin + schedule.tempMax) / 2
    val targetHumid = (schedule.humidMin + schedule.humidMax) / 2

    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isActive) "ACTIVE" else "QUEUED",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF4CAF50) else Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Schedule", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("From: ${startTime.format(formatter)}")
            Text("To:   ${endTime.format(formatter)}")
            Spacer(Modifier.height(8.dp))
            Text(
                "Targets: ${String.format("%.1f", targetTemp)}°C | ${String.format("%.1f", targetHumid)}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AddScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (startTime: Long, endTime: Long, temp: Float, humid: Float) -> Unit
) {
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var startTime by remember { mutableStateOf(LocalTime.now()) }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var endTime by remember { mutableStateOf(LocalTime.now()) }
    var targetTemp by remember { mutableStateOf("37.5") }
    var targetHumid by remember { mutableStateOf("60.0") }

    val startDateDialog = rememberMaterialDialogState()
    val startTimeDialog = rememberMaterialDialogState()
    val endDateDialog = rememberMaterialDialogState()
    val endTimeDialog = rememberMaterialDialogState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Schedule") },
        text = {
            Column {
                Text("Start Time")
                Row {
                    Button(onClick = { startDateDialog.show() }) { Text(startDate.toString()) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { startTimeDialog.show() }) { Text(startTime.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
                Spacer(Modifier.height(16.dp))
                Text("End Time")
                Row {
                    Button(onClick = { endDateDialog.show() }) { Text(endDate.toString()) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { endTimeDialog.show() }) { Text(endTime.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = targetTemp,
                    onValueChange = { targetTemp = it },
                    label = { Text("Target Temp (°C)") }
                )
                OutlinedTextField(
                    value = targetHumid,
                    onValueChange = { targetHumid = it },
                    label = { Text("Target Humidity (%)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val startEpoch = startDate.atTime(startTime).atZone(ZoneId.systemDefault()).toEpochSecond()
                val endEpoch = endDate.atTime(endTime).atZone(ZoneId.systemDefault()).toEpochSecond()
                onConfirm(startEpoch, endEpoch, targetTemp.toFloatOrNull() ?: 0f, targetHumid.toFloatOrNull() ?: 0f)
            }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    // Material Dialogs for picking date and time
    MaterialDialog(dialogState = startDateDialog, buttons = { positiveButton("Ok"); negativeButton("Cancel") }) {
        datepicker { date -> startDate = date }
    }
    MaterialDialog(dialogState = startTimeDialog, buttons = { positiveButton("Ok"); negativeButton("Cancel") }) {
        timepicker { time -> startTime = time }
    }
    MaterialDialog(dialogState = endDateDialog, buttons = { positiveButton("Ok"); negativeButton("Cancel") }) {
        datepicker { date -> endDate = date }
    }
    MaterialDialog(dialogState = endTimeDialog, buttons = { positiveButton("Ok"); negativeButton("Cancel") }) {
        timepicker { time -> endTime = time }
    }
}

@Composable
fun MetricCard(label: String, value: String) {
    Card(modifier = Modifier.size(150.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatusRow(label: String, isOn: Boolean) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = if (isOn) "ON" else "OFF",
                color = if (isOn) Color(0xFF4CAF50) else Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}