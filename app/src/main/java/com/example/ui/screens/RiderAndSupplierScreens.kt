package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.*
import com.example.ui.SilangUiState
import com.example.ui.components.*
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderDashboardScreen(
    state: SilangUiState,
    isLiveGeminiEnabled: Boolean,
    onUpdateDelivery: (Delivery) -> Unit,
    onLogout: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearNotifications: (String) -> Unit,
    onSendAiMessage: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val riderName = state.loggedInUser?.name ?: "Rider"
    val myDeliveries = remember(state.deliveries, riderName) {
        state.deliveries.filter { it.driverName.equals(riderName, ignoreCase = true) }
    }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val activeOrder = myDeliveries.firstOrNull { it.id == selectedId }
        ?: myDeliveries.firstOrNull { it.status == "Out for Delivery" }
        ?: myDeliveries.firstOrNull()

    var podOrder by remember { mutableStateOf<Delivery?>(null) }
    var paymentRefOrder by remember { mutableStateOf<Delivery?>(null) }
    var receiptOrder by remember { mutableStateOf<Delivery?>(null) }
    var showNotifications by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }

    @SuppressLint("MissingPermission")
    fun pushDeviceGpsToOrder(order: Delivery) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) {
            onUpdateDelivery(
                order.copy(
                    driverLat = loc.latitude,
                    driverLng = loc.longitude,
                    driverLocationUpdatedAt = Instant.now().toString(),
                    driverLocationAccuracyMeters = loc.accuracy.toDouble()
                )
            )
            onShowMessage("GPS fix updated (${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}).")
        } else {
            onShowMessage("No cached GPS fix on emulator yet — set GPS coordinates in emulator controls.")
        }
    }

    val gpsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && activeOrder != null) {
            pushDeviceGpsToOrder(activeOrder)
        } else {
            onShowMessage("Location permission is needed to share live rider GPS.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rider Portal", style = MaterialTheme.typography.titleMedium)
                        Text(riderName, style = MaterialTheme.typography.labelSmall, color = SilangOrange)
                    }
                },
                actions = {
                    BadgedBox(badge = {
                        if (state.unreadNotificationCount > 0) Badge { Text("${state.unreadNotificationCount}") }
                    }) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.cd_notifications))
                        }
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("rider_logout_button")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_logout))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SilangWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiSheet = true },
                containerColor = SilangSlate900,
                contentColor = SilangOrangeBright
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.cd_ai_assistant))
            }
        },
        containerColor = SilangSurface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeOrder != null) {
                item {
                    val bCoord = state.branchCoordinates[activeOrder.branch] ?: (14.2235 to 120.9739)
                    Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Active Task: ${activeOrder.id}", style = MaterialTheme.typography.titleMedium)
                                Text(activeOrder.status, style = MaterialTheme.typography.labelMedium, color = SilangOrange)
                            }
                            Text("${activeOrder.customerName} • ${activeOrder.barangay}", style = MaterialTheme.typography.bodyMedium)
                            Text(activeOrder.address, style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                            Text("Collect: ${formatPhp(activeOrder.totalAmount)} (${activeOrder.paymentMethod} • ${activeOrder.paymentStatus})", style = MaterialTheme.typography.titleSmall, color = SilangBlue)

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${activeOrder.customerPhone}"))) }
                                }) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Call")
                                }
                                OutlinedButton(onClick = {
                                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) pushDeviceGpsToOrder(activeOrder)
                                    else gpsPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                }) {
                                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Send GPS Fix")
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (activeOrder.status == "Pending" || activeOrder.status == "Preparing") {
                                    Button(
                                        onClick = {
                                            if (activeOrder.paymentMethod != "Cash" && activeOrder.paymentStatus != "Paid" && activeOrder.paymentReference.isNullOrBlank()) {
                                                paymentRefOrder = activeOrder
                                            } else {
                                                onUpdateDelivery(activeOrder.copy(status = "Out for Delivery"))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                                    ) { Text("Start Delivery") }
                                }
                                if (activeOrder.status == "Out for Delivery") {
                                    Button(
                                        onClick = { podOrder = activeOrder },
                                        colors = ButtonDefaults.buttonColors(containerColor = SilangGreen)
                                    ) { Text("Complete POD") }
                                }
                                TextButton(onClick = { receiptOrder = activeOrder }) { Text("Receipt") }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    RouteMapPreview(
                        branchName = activeOrder.branch,
                        branchLat = bCoord.first,
                        branchLng = bCoord.second,
                        customerName = activeOrder.customerName,
                        customerLat = activeOrder.lat,
                        customerLng = activeOrder.lng,
                        driverName = riderName,
                        driverLat = activeOrder.driverLat,
                        driverLng = activeOrder.driverLng
                    )
                }
            } else {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                        Text(
                            "No deliveries assigned to $riderName yet. Ask Admin to assign an order to your account.",
                            modifier = Modifier.padding(16.dp),
                            color = SilangSlate600
                        )
                    }
                }
            }

            item { Text("My Delivery Queue (${myDeliveries.size})", style = MaterialTheme.typography.titleMedium) }
            items(myDeliveries, key = { it.id }) { del ->
                Card(
                    onClick = { selectedId = del.id },
                    colors = CardDefaults.cardColors(containerColor = SilangWhite),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("${del.id} • ${del.customerName}", style = MaterialTheme.typography.titleSmall)
                            Text("${del.barangay} • ${del.status}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                        }
                        Text(formatPhp(del.totalAmount), style = MaterialTheme.typography.titleSmall, color = SilangBlue)
                    }
                }
            }
        }
    }

    paymentRefOrder?.let { order ->
        var ref by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paymentRefOrder = null },
            title = { Text("Submit ${order.paymentMethod} Proof") },
            text = {
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Reference Number *") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    if (ref.isBlank()) return@Button
                    onUpdateDelivery(order.copy(paymentReference = ref.trim(), paymentStatus = "Awaiting Verification", status = "Out for Delivery"))
                    paymentRefOrder = null
                }) { Text("Submit & Start") }
            },
            dismissButton = { TextButton(onClick = { paymentRefOrder = null }) { Text("Cancel") } }
        )
    }

    podOrder?.let { order ->
        var podUri by remember { mutableStateOf<String?>(null) }
        var confirmed by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { podOrder = null },
            title = { Text("Proof of Delivery • ${order.id}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeviceImagePickerField(
                        label = "Proof of Delivery Photo (Optional)",
                        imageUri = podUri,
                        onImageSelected = { podUri = it },
                        testTagPrefix = "rider_pod"
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("Customer received items & payment settled", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDelivery(
                            order.copy(
                                status = "Delivered",
                                paymentStatus = "Paid",
                                podPhotoUri = podUri,
                                podUploadedBy = riderName,
                                podUploadedAt = Instant.now().toString(),
                                deliveredAt = Instant.now().toString(),
                                handoverConfirmed = confirmed
                            )
                        )
                        podOrder = null
                    },
                    enabled = confirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = SilangGreen)
                ) { Text("Mark Delivered") }
            },
            dismissButton = { TextButton(onClick = { podOrder = null }) { Text("Cancel") } }
        )
    }

    receiptOrder?.let { DeliveryReceiptDialog(it) { receiptOrder = null } }

    if (showNotifications) {
        NotificationCenterSheet(
            notifications = state.activeNotificationsForRole,
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = { onMarkAllNotificationsRead("rider") },
            onDelete = onDeleteNotification,
            onClearAll = { onClearNotifications("rider") },
            onDismiss = { showNotifications = false }
        )
    }

    if (showAiSheet) {
        AiAssistantSheet(
            role = "rider",
            messages = state.aiMessages,
            isLoading = state.isAiLoading,
            isLiveGeminiEnabled = isLiveGeminiEnabled,
            onSendMessage = onSendAiMessage,
            onAddToCartFromAi = { _, _ -> },
            onDismiss = { showAiSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDashboardScreen(
    state: SilangUiState,
    onCreateSupplierDelivery: (SupplierDelivery) -> Unit,
    onLogout: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearNotifications: (String) -> Unit
) {
    val supplierName = state.loggedInUser?.name ?: "Supplier"
    val myDeliveries = remember(state.supplierDeliveries, supplierName) {
        state.supplierDeliveries.filter { it.supplierName.equals(supplierName, ignoreCase = true) }
    }
    var selectedBranch by remember { mutableStateOf(state.branches.firstOrNull()?.name ?: "Poblacion") }
    var deliveryDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var draftItems by remember { mutableStateOf<List<SupplierDeliveryItem>>(emptyList()) }
    var showNotifications by remember { mutableStateOf(false) }

    val pendingCount = myDeliveries.count { it.status == "Pending" }
    val receivedCount = myDeliveries.count { it.status == "Received" }
    val receivedValue = myDeliveries.filter { it.status == "Received" }.sumOf { it.totalCost }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Supplier Portal", style = MaterialTheme.typography.titleMedium, color = SilangWhite)
                        Text(supplierName, style = MaterialTheme.typography.labelSmall, color = SilangOrangeBright)
                    }
                },
                actions = {
                    BadgedBox(badge = {
                        if (state.unreadNotificationCount > 0) Badge { Text("${state.unreadNotificationCount}") }
                    }) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.cd_notifications), tint = SilangWhite)
                        }
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("supplier_logout_button")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_logout), tint = SilangWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SilangSlate900)
            )
        },
        containerColor = SilangSlate950
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SupplierMetricCard("Pending", "$pendingCount", SilangAmber, Modifier.weight(1f))
                SupplierMetricCard("Received", "$receivedCount", SilangGreen, Modifier.weight(1f))
                SupplierMetricCard("Value", formatPhp(receivedValue), SilangOrangeBright, Modifier.weight(1f))
            }

            Card(colors = CardDefaults.cardColors(containerColor = SilangSlate900), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Create Supply Delivery", style = MaterialTheme.typography.titleMedium, color = SilangWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.branches.forEach { b ->
                            FilterChip(selected = selectedBranch == b.name, onClick = { selectedBranch = b.name }, label = { Text(b.name) })
                        }
                    }
                    OutlinedTextField(
                        value = deliveryDate,
                        onValueChange = { deliveryDate = it },
                        label = { Text("Delivery Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    var prodMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { prodMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Add Product from Catalog")
                        }
                        DropdownMenu(expanded = prodMenuOpen, onDismissRequest = { prodMenuOpen = false }) {
                            state.products.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name} (Cost ${formatPhp(p.cost)})") },
                                    onClick = {
                                        if (draftItems.none { it.productId == p.id }) {
                                            draftItems = draftItems + SupplierDeliveryItem(p.id, p.name, 10, p.cost)
                                        }
                                        prodMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    draftItems.forEachIndexed { idx, item ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.productName, color = SilangWhite, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = item.quantity.toString(),
                                onValueChange = { v ->
                                    val q = v.filter(Char::isDigit).toIntOrNull() ?: 1
                                    draftItems = draftItems.toMutableList().also { it[idx] = item.copy(quantity = q.coerceAtLeast(1)) }
                                },
                                label = { Text("Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                            IconButton(onClick = { draftItems = draftItems.filterIndexed { i, _ -> i != idx } }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = SilangRed)
                            }
                        }
                    }

                    val totalCost = draftItems.sumOf { it.quantity * it.cost }
                    Button(
                        onClick = {
                            onCreateSupplierDelivery(
                                SupplierDelivery(
                                    id = "SUP-${System.currentTimeMillis().toString().takeLast(5)}",
                                    supplierName = supplierName,
                                    branch = selectedBranch,
                                    deliveryDate = deliveryDate,
                                    items = draftItems,
                                    totalCost = totalCost,
                                    status = "Pending"
                                )
                            )
                            draftItems = emptyList()
                        },
                        enabled = draftItems.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                    ) {
                        Text("Submit Delivery • ${formatPhp(totalCost)}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Delivery History (${myDeliveries.size})", style = MaterialTheme.typography.titleMedium, color = SilangWhite)
            if (myDeliveries.isEmpty()) {
                Text("No supplier deliveries submitted yet.", color = SilangSlate400)
            } else {
                myDeliveries.forEach { sd ->
                    Card(colors = CardDefaults.cardColors(containerColor = SilangSlate900), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(sd.id, style = MaterialTheme.typography.titleSmall, color = SilangWhite)
                                Text(sd.status, color = if (sd.status == "Received") SilangGreen else SilangAmber, style = MaterialTheme.typography.labelMedium)
                            }
                            Text("${sd.branch} • ${sd.deliveryDate}", style = MaterialTheme.typography.bodySmall, color = SilangSlate400)
                            Text(sd.items.joinToString(" • ") { "${it.quantity}x ${it.productName}" }, style = MaterialTheme.typography.bodySmall, color = SilangWhite)
                            Text(formatPhp(sd.totalCost), style = MaterialTheme.typography.titleSmall, color = SilangOrangeBright)
                        }
                    }
                }
            }
        }
    }

    if (showNotifications) {
        NotificationCenterSheet(
            notifications = state.activeNotificationsForRole,
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = { onMarkAllNotificationsRead("supplier") },
            onDelete = onDeleteNotification,
            onClearAll = { onClearNotifications("supplier") },
            onDismiss = { showNotifications = false }
        )
    }
}

@Composable
private fun SupplierMetricCard(label: String, value: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = SilangSlate900), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = SilangSlate400)
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent)
        }
    }
}
