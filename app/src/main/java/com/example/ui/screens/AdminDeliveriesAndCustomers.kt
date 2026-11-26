package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.*
import com.example.ui.SilangUiState
import com.example.ui.components.DeliveryReceiptDialog
import com.example.ui.components.RouteMapPreview
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.ceil

@Composable
fun AdminDeliveriesScreen(
    state: SilangUiState,
    onUpdateDelivery: (Delivery) -> Unit,
    onVerifyPayment: (deliveryId: String, approved: Boolean, reason: String?) -> Unit,
    onCreateDelivery: suspend (Delivery, Map<String, Int>) -> Result<Unit>,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var statusFilter by remember { mutableStateOf("All") }
    var trackedDelivery by remember { mutableStateOf<Delivery?>(null) }
    var receiptDelivery by remember { mutableStateOf<Delivery?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val riders = remember(state.users) { state.users.filter { it.role == "rider" && it.status == "active" } }
    val filtered = remember(state.deliveries, statusFilter) {
        state.deliveries.filter { d -> statusFilter == "All" || d.status == statusFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Deliveries (${state.deliveries.size})", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SilangOrange),
                modifier = Modifier.testTag("create_delivery_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Order")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Pending", "Preparing", "Out for Delivery", "Delivered", "Cancelled").forEach { st ->
                FilterChip(selected = statusFilter == st, onClick = { statusFilter = st }, label = { Text(st) })
            }
        }

        if (trackedDelivery != null) {
            val d = state.deliveries.firstOrNull { it.id == trackedDelivery?.id } ?: trackedDelivery!!
            val branchCoord = state.branchCoordinates[d.branch] ?: (14.2235 to 120.9739)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                RouteMapPreview(
                    branchName = d.branch,
                    branchLat = branchCoord.first,
                    branchLng = branchCoord.second,
                    customerName = d.customerName,
                    customerLat = d.lat,
                    customerLng = d.lng,
                    driverName = d.driverName,
                    driverLat = d.driverLat,
                    driverLng = d.driverLng
                )
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No delivery orders in this status.", color = SilangSlate600)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { del ->
                    Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${del.id} • ${del.customerName}", style = MaterialTheme.typography.titleMedium)
                                    Text("${del.address}, ${del.barangay} • ${del.customerPhone}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                }
                                Surface(
                                    color = when (del.status) {
                                        "Delivered" -> SilangGreenContainer
                                        "Out for Delivery" -> SilangOrangeContainer
                                        "Cancelled" -> SilangRedContainer
                                        else -> SilangBlueContainer
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(del.status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }

                            Text(
                                "${del.items.joinToString { "${it.quantity}x ${it.name}" }} • Total ${formatPhp(del.totalAmount)}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            // Payment Verification Badge & Controls for GCash / Maya
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Payment: ${del.paymentMethod} (${del.paymentStatus})" +
                                        (if (!del.paymentReference.isNullOrBlank()) " • Ref: ${del.paymentReference}" else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (del.paymentStatus == "Paid") SilangGreen else SilangAmber
                                )
                                if (del.paymentMethod != "Cash" && del.paymentStatus != "Paid" && del.status != "Cancelled") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = { onVerifyPayment(del.id, true, null) }) { Text("Verify Paid", color = SilangGreen) }
                                        TextButton(onClick = { onVerifyPayment(del.id, false, "Reference mismatch") }) { Text("Reject", color = SilangRed) }
                                    }
                                }
                            }

                            // Assign Rider Picker
                            var riderMenuExpanded by remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box {
                                    OutlinedButton(onClick = { riderMenuExpanded = true }) {
                                        Icon(Icons.Default.TwoWheeler, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(del.driverName)
                                    }
                                    DropdownMenu(expanded = riderMenuExpanded, onDismissRequest = { riderMenuExpanded = false }) {
                                        riders.forEach { r ->
                                            DropdownMenuItem(
                                                text = { Text(r.name) },
                                                onClick = {
                                                    riderMenuExpanded = false
                                                    onUpdateDelivery(del.copy(driverName = r.name))
                                                }
                                            )
                                        }
                                    }
                                }
                                TextButton(onClick = { trackedDelivery = if (trackedDelivery?.id == del.id) null else del }) {
                                    Text(if (trackedDelivery?.id == del.id) "Hide Map" else "Track GPS")
                                }
                                TextButton(onClick = { receiptDelivery = del }) {
                                    Text("Receipt")
                                }
                            }

                            // Lifecycle Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (del.status == "Pending") {
                                    FilledTonalButton(onClick = { onUpdateDelivery(del.copy(status = "Preparing")) }) { Text("Prepare") }
                                }
                                if (del.status == "Pending" || del.status == "Preparing") {
                                    Button(
                                        onClick = {
                                            if (del.driverName.contains("Unassigned")) {
                                                onShowMessage("Assign a rider first before dispatching.")
                                            } else {
                                                onUpdateDelivery(del.copy(status = "Out for Delivery"))
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                                    ) { Text("Dispatch") }
                                }
                                if (del.status == "Out for Delivery") {
                                    Button(
                                        onClick = {
                                            onUpdateDelivery(
                                                del.copy(
                                                    status = "Delivered",
                                                    paymentStatus = "Paid",
                                                    deliveredAt = Instant.now().toString(),
                                                    handoverConfirmed = true
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SilangGreen)
                                    ) { Text("Complete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    receiptDelivery?.let { d ->
        DeliveryReceiptDialog(delivery = d, onDismiss = { receiptDelivery = null })
    }

    if (showCreateDialog) {
        CreateDeliveryDialog(
            state = state,
            onDismiss = { showCreateDialog = false },
            onSubmit = { del, deductMap ->
                scope.launch {
                    val res = onCreateDelivery(del, deductMap)
                    if (res.isSuccess) showCreateDialog = false
                }
            }
        )
    }
}

@Composable
private fun CreateDeliveryDialog(
    state: SilangUiState,
    onDismiss: () -> Unit,
    onSubmit: (Delivery, Map<String, Int>) -> Unit
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var barangay by remember { mutableStateOf("Brgy. Sabutan") }
    var selectedProductId by remember { mutableStateOf(state.products.firstOrNull { it.isSellable() }?.id ?: "") }
    var qtyText by remember { mutableStateOf("1") }
    var feeText by remember { mutableStateOf("50") }
    var paymentMethod by remember { mutableStateOf("Cash") }

    val branchCoord = state.branchCoordinates[state.currentBranch] ?: (14.2235 to 120.9739)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Delivery Order (${state.currentBranch})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("Customer Name *") }, singleLine = true)
                OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("Phone *") }, singleLine = true)
                OutlinedTextField(value = barangay, onValueChange = { barangay = it }, label = { Text("Barangay") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Street / Landmark *") }, singleLine = true)
                val sellable = state.products.filter { it.isSellable() && it.stockForBranch(state.currentBranch) > 0 }
                if (sellable.isEmpty()) {
                    Text("No verified in-stock products at ${state.currentBranch}.", color = SilangRed, style = MaterialTheme.typography.bodySmall)
                } else {
                    val chosen = sellable.firstOrNull { it.id == selectedProductId } ?: sellable.first()
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${chosen.name} (${formatPhp(chosen.price)})")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            sellable.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name} (${p.stockForBranch(state.currentBranch)} left)") },
                                    onClick = { selectedProductId = p.id; menuOpen = false }
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qtyText, onValueChange = { qtyText = it.filter(Char::isDigit) }, label = { Text("Qty") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = feeText, onValueChange = { feeText = it.filter(Char::isDigit) }, label = { Text("Fee (₱)") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Cash", "GCash", "Maya").forEach { m ->
                        FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(m) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val prod = state.products.firstOrNull { it.id == selectedProductId } ?: return@Button
                    val qty = (qtyText.toIntOrNull() ?: 1).coerceAtLeast(1)
                    val fee = feeText.toDoubleOrNull() ?: 50.0
                    if (customerName.isBlank() || customerPhone.isBlank() || address.isBlank()) return@Button
                    val total = prod.price * qty + fee
                    val destLat = branchCoord.first + 0.005
                    val destLng = branchCoord.second + 0.004
                    val del = Delivery(
                        id = "DEL-${System.currentTimeMillis().toString().takeLast(5)}",
                        branch = state.currentBranch,
                        customerName = customerName.trim(),
                        customerPhone = customerPhone.trim(),
                        address = address.trim(),
                        barangay = barangay.trim(),
                        lat = destLat,
                        lng = destLng,
                        status = "Pending",
                        items = listOf(DeliveryItem(prod.id, prod.name, qty, prod.price, prod.batchNumber, prod.expiryDate, prod.supplierName, prod.authenticityStatus)),
                        deliveryFee = fee,
                        totalAmount = total,
                        driverName = "Unassigned",
                        driverLat = branchCoord.first,
                        driverLng = branchCoord.second,
                        etaMinutes = ceil(haversineDistanceKm(branchCoord.first, branchCoord.second, destLat, destLng) * 4).toInt().coerceAtLeast(5),
                        timestamp = Instant.now().toString(),
                        paymentMethod = paymentMethod,
                        paymentStatus = if (paymentMethod == "Cash") "Pending" else "Awaiting Verification"
                    )
                    onSubmit(del, mapOf(prod.id to qty))
                },
                colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
            ) { Text("Create Order") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdminCustomersScreen(state: SilangUiState) {
    var search by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<CustomerRecord?>(null) }

    val filtered = remember(state.customers, search) {
        val q = search.trim().lowercase()
        state.customers.filter {
            q.isBlank() || it.name.lowercase().contains(q) || it.phone.contains(q) || it.barangay.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search customer name, phone, or barangay...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("customer_search_input")
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No registered customers yet.", color = SilangSlate600)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { cust ->
                    val custOrders = state.deliveries.filter { it.customerName.equals(cust.name, ignoreCase = true) }
                    val totalSpend = custOrders.filter { it.status != "Cancelled" }.sumOf { it.totalAmount }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedCustomer = cust },
                        colors = CardDefaults.cardColors(containerColor = SilangWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!cust.storeImageUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = cust.storeImageUri,
                                    contentDescription = cust.name,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = SilangBlue, modifier = Modifier.size(40.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cust.name, style = MaterialTheme.typography.titleMedium)
                                Text("${cust.barangay} • ${cust.phone}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                Text("${custOrders.size} orders • Total ${formatPhp(totalSpend)}", style = MaterialTheme.typography.labelSmall, color = SilangGreen)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SilangSlate400)
                        }
                    }
                }
            }
        }
    }

    selectedCustomer?.let { cust ->
        val orders = state.deliveries.filter { it.customerName.equals(cust.name, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { selectedCustomer = null },
            title = { Text(cust.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Phone: ${cust.phone}")
                    Text("Address: ${cust.address}, ${cust.barangay}")
                    Text("GPS Pin: ${"%.5f".format(cust.lat)}, ${"%.5f".format(cust.lng)}", style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider()
                    Text("Order History (${orders.size})", style = MaterialTheme.typography.titleSmall)
                    if (orders.isEmpty()) {
                        Text("No orders yet.", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                    } else {
                        orders.take(5).forEach { o ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${o.id} (${o.status})", style = MaterialTheme.typography.bodySmall)
                                Text(formatPhp(o.totalAmount), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { selectedCustomer = null }) { Text("Close") } }
        )
    }
}
