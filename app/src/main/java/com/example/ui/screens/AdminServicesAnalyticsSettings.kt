package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.SilangUiState
import com.example.ui.components.ServiceReceiptDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@Composable
fun AdminServicesScreen(
    state: SilangUiState,
    onAddServiceTransaction: (ServiceTransaction) -> Unit,
    onShowMessage: (String) -> Unit
) {
    var subTab by remember { mutableStateOf("load") } // load, bills, remit, history
    var receiptToShow by remember { mutableStateOf<ServiceTransaction?>(null) }

    // E-Load state
    var network by remember { mutableStateOf("Smart") }
    var phone by remember { mutableStateOf("") }
    var promoAmount by remember { mutableStateOf("50") }
    var loadFee by remember { mutableStateOf("5") }

    // Bills state
    var billProvider by remember { mutableStateOf("Meralco (Electricity)") }
    var billAccount by remember { mutableStateOf("") }
    var billName by remember { mutableStateOf("") }
    var billAmount by remember { mutableStateOf("") }
    var billFee by remember { mutableStateOf("15") }

    // Remit state
    var remitProvider by remember { mutableStateOf("Palawan Express Padala") }
    var remitSender by remember { mutableStateOf("") }
    var remitRecipient by remember { mutableStateOf("") }
    var remitAmount by remember { mutableStateOf("") }
    var remitFee by remember { mutableStateOf("50") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = subTab == "load", onClick = { subTab = "load" }, label = { Text("E-Load") })
            FilterChip(selected = subTab == "bills", onClick = { subTab = "bills" }, label = { Text("Bayad Bills") })
            FilterChip(selected = subTab == "remit", onClick = { subTab = "remit" }, label = { Text("Remittance / Cash-in") })
            FilterChip(selected = subTab == "history", onClick = { subTab = "history" }, label = { Text("Ledger (${state.serviceTransactions.size})") })
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (subTab) {
                "load" -> {
                    Text("Electronic Loading Station", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Smart", "Globe", "TNT", "DITO").forEach { net ->
                            FilterChip(selected = network == net, onClick = { network = net }, label = { Text(net) })
                        }
                    }
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                        label = { Text("Customer Phone (11 digits) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("service_load_phone_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("20", "50", "99", "149").forEach { amt ->
                            FilterChip(selected = promoAmount == amt, onClick = { promoAmount = amt }, label = { Text("₱$amt") })
                        }
                    }
                    OutlinedTextField(
                        value = loadFee,
                        onValueChange = { loadFee = it.filter(Char::isDigit) },
                        label = { Text("Service Fee (₱)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val amt = promoAmount.toDoubleOrNull() ?: 0.0
                    val fee = loadFee.toDoubleOrNull() ?: 0.0
                    Button(
                        onClick = {
                            if (phone.length < 10) {
                                onShowMessage("Enter an 11-digit mobile number.")
                                return@Button
                            }
                            val srv = ServiceTransaction(
                                id = "SRV-${System.currentTimeMillis().toString().takeLast(5)}",
                                branch = state.currentBranch,
                                type = "E-Load",
                                provider = "$network E-Load",
                                recipient = phone,
                                senderOrAccountName = "Walk-in Customer",
                                amount = amt,
                                serviceFee = fee,
                                totalAmount = amt + fee,
                                timestamp = Instant.now().toString()
                            )
                            onAddServiceTransaction(srv)
                            receiptToShow = srv
                            phone = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("service_submit_load_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
                    ) {
                        Text("Send E-Load • ${formatPhp(amt + fee)}", fontWeight = FontWeight.Bold)
                    }
                }
                "bills" -> {
                    Text("Over-the-Counter Bills Payment", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Meralco (Electricity)", "Silang Water District", "PLDT Home Fibra", "Globe Broadband", "Cignal TV").forEach { prov ->
                            FilterChip(selected = billProvider == prov, onClick = { billProvider = prov }, label = { Text(prov) })
                        }
                    }
                    OutlinedTextField(value = billAccount, onValueChange = { billAccount = it }, label = { Text("Account Number *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = billName, onValueChange = { billName = it }, label = { Text("Account Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = billAmount,
                            onValueChange = { billAmount = it },
                            label = { Text("Bill Amount (₱) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = billFee,
                            onValueChange = { billFee = it },
                            label = { Text("Fee (₱)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    val amt = billAmount.toDoubleOrNull() ?: 0.0
                    val fee = billFee.toDoubleOrNull() ?: 0.0
                    Button(
                        onClick = {
                            if (billAccount.isBlank() || billName.isBlank() || amt <= 0) {
                                onShowMessage("Fill account number, name, and valid bill amount.")
                                return@Button
                            }
                            val srv = ServiceTransaction(
                                id = "SRV-${System.currentTimeMillis().toString().takeLast(5)}",
                                branch = state.currentBranch,
                                type = "Bills Payment",
                                provider = billProvider,
                                recipient = "Acct: $billAccount",
                                senderOrAccountName = billName.trim(),
                                amount = amt,
                                serviceFee = fee,
                                totalAmount = amt + fee,
                                timestamp = Instant.now().toString()
                            )
                            onAddServiceTransaction(srv)
                            receiptToShow = srv
                            billAccount = ""
                            billName = ""
                            billAmount = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
                    ) {
                        Text("Process Bill • ${formatPhp(amt + fee)}", fontWeight = FontWeight.Bold)
                    }
                }
                "remit" -> {
                    Text("Remittance & E-Wallet Cash-In", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("GCash Cash-In", "Maya Cash-In", "Palawan Express Padala", "Cebuana Lhuillier").forEach { prov ->
                            FilterChip(selected = remitProvider == prov, onClick = { remitProvider = prov }, label = { Text(prov) })
                        }
                    }
                    OutlinedTextField(value = remitSender, onValueChange = { remitSender = it }, label = { Text("Sender / Customer *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = remitRecipient, onValueChange = { remitRecipient = it }, label = { Text("Recipient / Mobile No. *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = remitAmount,
                            onValueChange = { remitAmount = it },
                            label = { Text("Amount (₱) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = remitFee,
                            onValueChange = { remitFee = it },
                            label = { Text("Fee (₱)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    val amt = remitAmount.toDoubleOrNull() ?: 0.0
                    val fee = remitFee.toDoubleOrNull() ?: 0.0
                    Button(
                        onClick = {
                            if (remitSender.isBlank() || remitRecipient.isBlank() || amt <= 0) {
                                onShowMessage("Fill sender, recipient, and valid amount.")
                                return@Button
                            }
                            val srv = ServiceTransaction(
                                id = "SRV-${System.currentTimeMillis().toString().takeLast(5)}",
                                branch = state.currentBranch,
                                type = "Remittance",
                                provider = remitProvider,
                                recipient = remitRecipient.trim(),
                                senderOrAccountName = remitSender.trim(),
                                amount = amt,
                                serviceFee = fee,
                                totalAmount = amt + fee,
                                timestamp = Instant.now().toString()
                            )
                            onAddServiceTransaction(srv)
                            receiptToShow = srv
                            remitSender = ""
                            remitRecipient = ""
                            remitAmount = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
                    ) {
                        Text("Process Remittance • ${formatPhp(amt + fee)}", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    if (state.serviceTransactions.isEmpty()) {
                        Text("No value-added service transactions recorded yet.", color = SilangSlate600)
                    } else {
                        state.serviceTransactions.forEach { srv ->
                            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), onClick = { receiptToShow = srv }) {
                                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("${srv.provider} • ${srv.id}", style = MaterialTheme.typography.titleSmall)
                                        Text("To: ${srv.recipient} • ${srv.branch}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                    }
                                    Text(formatPhp(srv.totalAmount), style = MaterialTheme.typography.titleSmall, color = SilangBlue)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    receiptToShow?.let { srv ->
        ServiceReceiptDialog(service = srv, onDismiss = { receiptToShow = null })
    }
}

@Composable
fun AdminAnalyticsScreen(state: SilangUiState) {
    val context = LocalContext.current
    var period by remember { mutableStateOf("All") } // Daily, Weekly, Monthly, All
    val cutoffDate = remember(period) {
        when (period) {
            "Daily" -> LocalDate.now()
            "Weekly" -> LocalDate.now().minusDays(7)
            "Monthly" -> LocalDate.now().minusDays(30)
            else -> LocalDate.of(2000, 1, 1)
        }
    }
    fun matchesPeriod(iso: String): Boolean = runCatching {
        !LocalDate.parse(iso.take(10)).isBefore(cutoffDate)
    }.getOrDefault(true)

    val txns = state.transactions.filter { matchesPeriod(it.timestamp) }
    val dels = state.deliveries.filter { it.status != "Cancelled" && matchesPeriod(it.timestamp) }
    val srvs = state.serviceTransactions.filter { matchesPeriod(it.timestamp) }

    val posRev = txns.sumOf { it.total }
    val delRev = dels.sumOf { it.totalAmount }
    val feeRev = srvs.sumOf { it.serviceFee }
    val grossRev = posRev + delRev + feeRev

    val categoryRevenue = remember(txns, state.products) {
        val productMap = state.products.associateBy { it.id }
        val totals = mutableMapOf<String, Double>()
        txns.forEach { t ->
            t.items.forEach { item ->
                val cat = productMap[item.productId]?.category ?: "Other"
                totals[cat] = (totals[cat] ?: 0.0) + (item.price * item.quantity)
            }
        }
        totals.entries.sortedByDescending { it.value }
    }

    val topProducts = remember(txns, dels) {
        val qtyMap = mutableMapOf<String, Int>()
        txns.forEach { t -> t.items.forEach { qtyMap[it.name] = (qtyMap[it.name] ?: 0) + it.quantity } }
        dels.forEach { d -> d.items.forEach { qtyMap[it.name] = (qtyMap[it.name] ?: 0) + it.quantity } }
        qtyMap.entries.sortedByDescending { it.value }.take(5)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Daily", "Weekly", "Monthly", "All").forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p) })
                }
            }
            IconButton(
                onClick = {
                    val summary = "Silang Mini Mart Report ($period)\nGross Revenue: ${formatPhp(grossRev)}\nPOS: ${formatPhp(posRev)}\nDeliveries: ${formatPhp(delRev)}\nService Fees: ${formatPhp(feeRev)}"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, summary)
                    }
                    runCatching { context.startActivity(Intent.createChooser(sendIntent, "Share Sales Report")) }
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share Report")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SilangSlate900), shape = RoundedCornerShape(18.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gross Revenue ($period)", style = MaterialTheme.typography.labelMedium, color = SilangOrangeBright)
                Text(formatPhp(grossRev), style = MaterialTheme.typography.displaySmall, color = SilangWhite)
                Text("POS ${formatPhp(posRev)} • Delivery ${formatPhp(delRev)} • Fees ${formatPhp(feeRev)}", style = MaterialTheme.typography.bodySmall, color = SilangSlate400)
            }
        }

        Text("Branch Performance", style = MaterialTheme.typography.titleMedium)
        state.branches.forEach { b ->
            val bRev = txns.filter { it.branch == b.name }.sumOf { it.total } +
                dels.filter { it.branch == b.name }.sumOf { it.totalAmount }
            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(b.name, style = MaterialTheme.typography.titleSmall)
                    Text(formatPhp(bRev), style = MaterialTheme.typography.titleSmall, color = SilangBlue)
                }
            }
        }

        if (categoryRevenue.isNotEmpty()) {
            Text("Category Revenue Breakdown", style = MaterialTheme.typography.titleMedium)
            val maxVal = categoryRevenue.maxOf { it.value }.coerceAtLeast(1.0)
            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    categoryRevenue.take(6).forEach { (cat, rev) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat, style = MaterialTheme.typography.bodySmall)
                                Text(formatPhp(rev), style = MaterialTheme.typography.labelSmall)
                            }
                            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                                drawRoundRect(SilangSlate200, cornerRadius = CornerRadius(8f, 8f))
                                drawRoundRect(
                                    color = SilangBlue,
                                    size = Size((size.width * (rev / maxVal).toFloat()).coerceAtLeast(8f), size.height),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Text("Top Best-Selling Items", style = MaterialTheme.typography.titleMedium)
        if (topProducts.isEmpty()) {
            Text("No item sales recorded in this period.", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
        } else {
            topProducts.forEachIndexed { idx, entry ->
                Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${idx + 1}. ${entry.key}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${entry.value} sold", style = MaterialTheme.typography.titleSmall, color = SilangOrange)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsScreen(
    state: SilangUiState,
    onSaveBranch: (BranchRecord, String?) -> Unit,
    onDeleteBranch: (Long) -> Unit,
    onCreateUser: suspend (String, String, String, String) -> Result<Unit>,
    onDeleteUser: (Long) -> Unit,
    onLoadStarterCatalog: () -> Unit,
    onResetData: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showBranchDialog by remember { mutableStateOf(false) }
    var editingBranch by remember { mutableStateOf<BranchRecord?>(null) }
    var showUserDialog by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Branch Manager
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Branch Locations (${state.branches.size})", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { editingBranch = null; showBranchDialog = true }) { Text("+ Branch") }
        }
        state.branches.forEach { b ->
            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                        Text("${b.address} • (${"%.4f".format(b.lat)}, ${"%.4f".format(b.lng)})", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                    }
                    Switch(checked = b.isActive, onCheckedChange = { onSaveBranch(b.copy(isActive = it), b.name) })
                    IconButton(onClick = { editingBranch = b; showBranchDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit branch")
                    }
                    if (state.branches.size > 1) {
                        IconButton(onClick = { onDeleteBranch(b.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete branch", tint = SilangRed)
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Staff, Rider & Supplier User Manager
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Staff, Riders & Suppliers (${state.users.size})", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { showUserDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)) {
                Text("+ Account")
            }
        }
        state.users.forEach { u ->
            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleSmall)
                        Text("@${u.username} • ${u.role.uppercase()}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                    }
                    if (!u.username.equals("admin", ignoreCase = true)) {
                        IconButton(onClick = { onDeleteUser(u.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete user", tint = SilangRed)
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Maintenance
        Text("Catalog & System Tools", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onLoadStarterCatalog, modifier = Modifier.weight(1f)) {
                Text("Load Starter Catalog")
            }
            Button(
                onClick = { confirmReset = true },
                colors = ButtonDefaults.buttonColors(containerColor = SilangRed),
                modifier = Modifier.weight(1f)
            ) {
                Text("Reset Store Data")
            }
        }
    }

    if (showBranchDialog) {
        var bName by remember { mutableStateOf(editingBranch?.name ?: "") }
        var bAddr by remember { mutableStateOf(editingBranch?.address ?: "") }
        var bLat by remember { mutableStateOf((editingBranch?.lat ?: 14.2235).toString()) }
        var bLng by remember { mutableStateOf((editingBranch?.lng ?: 120.9739).toString()) }
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            title = { Text(if (editingBranch == null) "Add Branch" else "Edit Branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = bName, onValueChange = { bName = it }, label = { Text("Branch Name *") }, singleLine = true)
                    OutlinedTextField(value = bAddr, onValueChange = { bAddr = it }, label = { Text("Address *") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = bLat, onValueChange = { bLat = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = bLng, onValueChange = { bLng = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (bName.isBlank()) return@Button
                    val rec = BranchRecord(
                        id = editingBranch?.id ?: 0L,
                        name = bName.trim(),
                        address = bAddr.trim().ifBlank { bName.trim() },
                        lat = bLat.toDoubleOrNull() ?: 14.2235,
                        lng = bLng.toDoubleOrNull() ?: 120.9739,
                        isActive = editingBranch?.isActive ?: true
                    )
                    onSaveBranch(rec, editingBranch?.name)
                    showBranchDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showBranchDialog = false }) { Text("Cancel") } }
        )
    }

    if (showUserDialog) {
        var uName by remember { mutableStateOf("") }
        var uUsername by remember { mutableStateOf("") }
        var uPass by remember { mutableStateOf("") }
        var uRole by remember { mutableStateOf("cashier") }
        var uError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showUserDialog = false },
            title = { Text("Create Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uError != null) Text(uError!!, color = SilangRed, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = uName, onValueChange = { uName = it }, label = { Text("Display Name *") }, singleLine = true)
                    OutlinedTextField(value = uUsername, onValueChange = { uUsername = it }, label = { Text("Username *") }, singleLine = true)
                    OutlinedTextField(value = uPass, onValueChange = { uPass = it }, label = { Text("Password (min 4) *") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("admin", "cashier", "rider", "supplier").forEach { r ->
                            FilterChip(selected = uRole == r, onClick = { uRole = r }, label = { Text(r) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = onCreateUser(uName, uUsername, uPass, uRole)
                        res.onSuccess { showUserDialog = false }
                            .onFailure { uError = it.message }
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showUserDialog = false }) { Text("Cancel") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Store Data?") },
            text = { Text("This clears all products, POS transactions, deliveries, and services while keeping branches and user accounts.") },
            confirmButton = {
                Button(
                    onClick = { onResetData(); confirmReset = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SilangRed)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}
