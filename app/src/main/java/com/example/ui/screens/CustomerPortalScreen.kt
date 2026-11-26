package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.*
import com.example.ui.SilangUiState
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPortalScreen(
    state: SilangUiState,
    isAdminPreview: Boolean,
    isLiveGeminiEnabled: Boolean,
    onExitPreview: () -> Unit,
    onLogout: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onCreateDelivery: suspend (Delivery, Map<String, Int>) -> Result<Unit>,
    onUpdateDelivery: (Delivery) -> Unit,
    onUpdateProfile: (String, String, String, String, Double, Double, String?, String?) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onClearNotifications: (String) -> Unit,
    onSendAiMessage: (String) -> Unit,
    onMarkAiCartDone: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var subTab by remember { mutableStateOf("shop") } // shop, orders, tracking, profile
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var variantPickerProduct by remember { mutableStateOf<Product?>(null) }
    var showBagSheet by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var receiptDelivery by remember { mutableStateOf<Delivery?>(null) }
    var orderFilter by remember { mutableStateOf("active") } // active, archived, trash

    if (subTab != "shop") {
        BackHandler { subTab = "shop" }
    }

    val activeName = state.loggedInUser?.name ?: "Customer Guest"
    val customerProfile = state.customers.firstOrNull { it.name.equals(activeName, ignoreCase = true) }
    val myOrders = remember(state.deliveries, activeName) {
        state.deliveries.filter { it.customerName.equals(activeName, ignoreCase = true) }
    }

    fun addToBag(product: Product, variant: PackagingVariant, qtyToAdd: Int = 1) {
        if (!product.isSellable()) {
            onShowMessage("${product.name} is not currently available for online order.")
            return
        }
        val available = product.stockForBranch(state.currentBranch)
        val inCartUnits = cart.filter { it.product.id == product.id }.sumOf { it.totalBaseUnits }
        if (inCartUnits + (variant.qtyPerUnit * qtyToAdd) > available) {
            onShowMessage("Only $available ${product.unit} available at ${state.currentBranch}.")
            return
        }
        val idx = cart.indexOfFirst { it.product.id == product.id && it.selectedVariant.type == variant.type }
        cart = if (idx >= 0) {
            cart.toMutableList().also { it[idx] = it[idx].copy(quantity = it[idx].quantity + qtyToAdd) }
        } else {
            cart + CartItem(product, qtyToAdd, variant)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Silang Mini Mart", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isAdminPreview) "Preview Mode • ${state.currentBranch}" else "Welcome, $activeName",
                            style = MaterialTheme.typography.labelSmall,
                            color = SilangOrange
                        )
                    }
                },
                actions = {
                    if (isAdminPreview) {
                        TextButton(onClick = onExitPreview) { Text("Back to Admin") }
                    }
                    BadgedBox(badge = {
                        if (state.unreadNotificationCount > 0) Badge { Text("${state.unreadNotificationCount}") }
                    }) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.cd_notifications))
                        }
                    }
                    IconButton(onClick = onLogout, modifier = Modifier.testTag("customer_logout_button")) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_logout))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SilangWhite)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SilangWhite) {
                NavigationBarItem(selected = subTab == "shop", onClick = { subTab = "shop" }, icon = { Icon(Icons.Default.ShoppingBag, contentDescription = null) }, label = { Text("Shop") })
                NavigationBarItem(selected = subTab == "orders", onClick = { subTab = "orders" }, icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) }, label = { Text("Orders") })
                NavigationBarItem(selected = subTab == "tracking", onClick = { subTab = "tracking" }, icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }, label = { Text("Track GPS") })
                NavigationBarItem(selected = subTab == "profile", onClick = { subTab = "profile" }, icon = { Icon(Icons.Default.Person, contentDescription = null) }, label = { Text("Profile") })
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiSheet = true },
                containerColor = SilangSlate900,
                contentColor = SilangOrangeBright,
                modifier = Modifier.testTag("customer_ai_fab")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.cd_ai_assistant))
            }
        },
        containerColor = SilangSurface
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (subTab) {
                "shop" -> {
                    val filtered = remember(state.products, search, selectedCategory) {
                        val q = search.trim().lowercase()
                        state.products.filter { p ->
                            (q.isBlank() || p.name.lowercase().contains(q) || p.tagalogName.lowercase().contains(q)) &&
                                (selectedCategory == "All" || p.category == selectedCategory)
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.background(SilangWhite).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = search,
                                    onValueChange = { search = it },
                                    placeholder = { Text("Search groceries, bigas, ulam...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("customer_search_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                state.branches.filter { it.isActive }.forEach { b ->
                                    FilterChip(
                                        selected = state.currentBranch == b.name,
                                        onClick = { onSelectBranch(b.name) },
                                        label = { Text("${b.name} Branch") }
                                    )
                                }
                            }
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (listOf("All") + state.categories).forEach { cat ->
                                    FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
                                }
                            }
                        }

                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No products available in this branch yet.", color = SilangSlate600)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filtered, key = { it.id }) { prod ->
                                    val stock = prod.stockForBranch(state.currentBranch)
                                    val canOrder = prod.isSellable() && stock > 0
                                    Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            ProductThumbnail(prod)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(prod.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    if (canOrder) "$stock ${prod.unit} in stock" else "Unavailable",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (canOrder) SilangGreen else SilangRed
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(formatPhp(prod.price), style = MaterialTheme.typography.titleMedium, color = SilangOrange)
                                                Button(
                                                    onClick = {
                                                        val vars = prod.effectiveVariants()
                                                        if (vars.size > 1) variantPickerProduct = prod
                                                        else addToBag(prod, vars.first())
                                                    },
                                                    enabled = canOrder,
                                                    colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                                                ) {
                                                    Text(if (prod.variants.size > 1) "Choose" else "+ Bag")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (cart.isNotEmpty()) {
                        val bagTotal = cart.sumOf { it.lineTotal }
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(12.dp)
                                .clickable { showBagSheet = true },
                            color = SilangSlate900,
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${cart.sumOf { it.quantity }} item(s) in Delivery Bag", style = MaterialTheme.typography.labelMedium, color = SilangOrangeBright)
                                    Text(formatPhp(bagTotal), style = MaterialTheme.typography.titleMedium, color = SilangWhite)
                                }
                                Button(onClick = { showBagSheet = true }, colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)) {
                                    Text("Place Order")
                                }
                            }
                        }
                    }
                }

                "orders" -> {
                    val filteredOrders = remember(myOrders, orderFilter) {
                        when (orderFilter) {
                            "archived" -> myOrders.filter { it.archivedAt != null && it.deletedAt == null }
                            "trash" -> myOrders.filter { it.deletedAt != null }
                            else -> myOrders.filter { it.archivedAt == null && it.deletedAt == null }
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = orderFilter == "active", onClick = { orderFilter = "active" }, label = { Text("Active") })
                            FilterChip(selected = orderFilter == "archived", onClick = { orderFilter = "archived" }, label = { Text("Archived") })
                            FilterChip(selected = orderFilter == "trash", onClick = { orderFilter = "trash" }, label = { Text("Recycle Bin") })
                        }
                        if (filteredOrders.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No orders in $orderFilter.", color = SilangSlate600)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(filteredOrders, key = { it.id }) { del ->
                                    Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${del.id} • ${del.status}", style = MaterialTheme.typography.titleSmall)
                                                Text(formatPhp(del.totalAmount), style = MaterialTheme.typography.titleSmall, color = SilangOrange)
                                            }
                                            Text(del.items.joinToString { "${it.quantity}x ${it.name}" }, style = MaterialTheme.typography.bodySmall)
                                            Text("Payment: ${del.paymentMethod} (${del.paymentStatus})", style = MaterialTheme.typography.labelSmall, color = SilangSlate600)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = { receiptDelivery = del }) { Text("Receipt") }
                                                if (orderFilter == "active") {
                                                    TextButton(onClick = { onUpdateDelivery(del.copy(archivedAt = Instant.now().toString())) }) { Text("Archive") }
                                                    TextButton(onClick = { onUpdateDelivery(del.copy(deletedAt = Instant.now().toString())) }) { Text("Trash", color = SilangRed) }
                                                } else {
                                                    TextButton(onClick = { onUpdateDelivery(del.copy(archivedAt = null, deletedAt = null)) }) { Text("Restore") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "tracking" -> {
                    val activeTrack = myOrders.firstOrNull { it.status == "Out for Delivery" || it.status == "Preparing" || it.status == "Pending" }
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (activeTrack == null) {
                            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                                Text(
                                    "You have no active deliveries right now. Place an order in Shop to track live GPS!",
                                    modifier = Modifier.padding(16.dp),
                                    color = SilangSlate600
                                )
                            }
                        } else {
                            val bCoord = state.branchCoordinates[activeTrack.branch] ?: (14.2235 to 120.9739)
                            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Tracking ${activeTrack.id} • ${activeTrack.status}", style = MaterialTheme.typography.titleMedium)
                                    Text("Rider: ${activeTrack.driverName} • ETA ~${activeTrack.etaMinutes} mins", style = MaterialTheme.typography.bodySmall, color = SilangOrange)
                                }
                            }
                            RouteMapPreview(
                                branchName = activeTrack.branch,
                                branchLat = bCoord.first,
                                branchLng = bCoord.second,
                                customerName = activeTrack.customerName,
                                customerLat = activeTrack.lat,
                                customerLng = activeTrack.lng,
                                driverName = activeTrack.driverName,
                                driverLat = activeTrack.driverLat,
                                driverLng = activeTrack.driverLng
                            )
                        }
                    }
                }

                "profile" -> {
                    CustomerProfileEditor(
                        customer = customerProfile,
                        fallbackName = activeName,
                        branchCoord = state.branchCoordinates[state.currentBranch] ?: (14.2235 to 120.9739),
                        onSave = onUpdateProfile
                    )
                }
            }
        }
    }

    variantPickerProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { variantPickerProduct = null },
            title = { Text("Choose Variant • ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prod.effectiveVariants().forEach { v ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                addToBag(prod, v)
                                variantPickerProduct = null
                            },
                            colors = CardDefaults.cardColors(containerColor = SilangSurface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(v.label.ifBlank { v.type }, style = MaterialTheme.typography.titleSmall)
                                Text(formatPhp(v.price), style = MaterialTheme.typography.titleSmall, color = SilangOrange)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { variantPickerProduct = null }) { Text("Close") } }
        )
    }

    if (showBagSheet) {
        var paymentMethod by remember { mutableStateOf("Cash") }
        var paymentRef by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var proofUri by remember { mutableStateOf<String?>(null) }
        val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null) proofUri = uri.toString()
        }

        val itemsTotal = cart.sumOf { it.lineTotal }
        val deliveryFee = 50.0
        val grandTotal = itemsTotal + deliveryFee
        val branchCoord = state.branchCoordinates[state.currentBranch] ?: (14.2235 to 120.9739)
        val destLat = customerProfile?.lat ?: (branchCoord.first + 0.004)
        val destLng = customerProfile?.lng ?: (branchCoord.second + 0.004)

        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val bagSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                showBagSheet = false
            },
            sheetState = bagSheetState,
            containerColor = SilangSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Delivery Bag (${state.currentBranch})", style = MaterialTheme.typography.titleLarge)
                cart.forEachIndexed { idx, item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, style = MaterialTheme.typography.titleSmall)
                            Text("${item.quantity} x ${formatPhp(item.selectedVariant.price)}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                        }
                        IconButton(onClick = { cart = cart.filterIndexed { i, _ -> i != idx } }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove item", tint = SilangRed)
                        }
                    }
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "GCash", "Maya").forEach { m ->
                        FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(m) })
                    }
                }
                if (paymentMethod != "Cash") {
                    OutlinedTextField(
                        value = paymentRef,
                        onValueChange = { paymentRef = it },
                        label = { Text("$paymentMethod Reference No. *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { proofPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (proofUri == null) "Upload Payment Screenshot (Optional)" else "Screenshot Attached")
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Delivery Notes / Landmark") }, modifier = Modifier.fillMaxWidth())
                Text("Items: ${formatPhp(itemsTotal)} + Fee ${formatPhp(deliveryFee)} = ${formatPhp(grandTotal)}", style = MaterialTheme.typography.titleMedium, color = SilangOrange)

                Button(
                    onClick = {
                        if (paymentMethod != "Cash" && paymentRef.isBlank()) {
                            onShowMessage("Enter your $paymentMethod reference number.")
                            return@Button
                        }
                        val deductMap = cart.groupBy { it.product.id }.mapValues { e -> e.value.sumOf { it.totalBaseUnits } }
                        val del = Delivery(
                            id = "DEL-${System.currentTimeMillis().toString().takeLast(5)}",
                            branch = state.currentBranch,
                            customerName = activeName,
                            customerPhone = customerProfile?.phone ?: "09123456789",
                            address = customerProfile?.address ?: "Silang, Cavite",
                            barangay = customerProfile?.barangay ?: "Brgy. Sabutan",
                            lat = destLat,
                            lng = destLng,
                            status = "Pending",
                            items = cart.map {
                                DeliveryItem(
                                    it.product.id,
                                    "${it.product.name} [${it.selectedVariant.label}]",
                                    it.quantity,
                                    it.selectedVariant.price,
                                    it.product.batchNumber,
                                    it.product.expiryDate,
                                    it.product.supplierName,
                                    it.product.authenticityStatus
                                )
                            },
                            deliveryFee = deliveryFee,
                            totalAmount = grandTotal,
                            driverName = "Unassigned",
                            driverLat = branchCoord.first,
                            driverLng = branchCoord.second,
                            etaMinutes = ceil(haversineDistanceKm(branchCoord.first, branchCoord.second, destLat, destLng) * 4).toInt().coerceAtLeast(5),
                            notes = notes.trim(),
                            timestamp = Instant.now().toString(),
                            paymentMethod = paymentMethod,
                            paymentStatus = if (paymentMethod == "Cash") "Pending" else "Awaiting Verification",
                            paymentReference = paymentRef.ifBlank { null },
                            paymentProofUri = proofUri
                        )
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        scope.launch {
                            val res = onCreateDelivery(del, deductMap)
                            if (res.isSuccess) {
                                cart = emptyList()
                                showBagSheet = false
                                subTab = "orders"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("customer_place_order_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                ) {
                    Text("Confirm Order • ${formatPhp(grandTotal)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    receiptDelivery?.let { DeliveryReceiptDialog(it) { receiptDelivery = null } }

    if (showNotifications) {
        NotificationCenterSheet(
            notifications = state.activeNotificationsForRole,
            onMarkRead = onMarkNotificationRead,
            onMarkAllRead = { onMarkAllNotificationsRead("customer") },
            onDelete = onDeleteNotification,
            onClearAll = { onClearNotifications("customer") },
            onDismiss = { showNotifications = false }
        )
    }

    if (showAiSheet) {
        AiAssistantSheet(
            role = "customer",
            messages = state.aiMessages,
            isLoading = state.isAiLoading,
            isLiveGeminiEnabled = isLiveGeminiEnabled,
            onSendMessage = onSendAiMessage,
            onAddToCartFromAi = { msgId, actions ->
                actions.forEach { act ->
                    state.products.firstOrNull { it.id == act.productId }?.let { prod ->
                        addToBag(prod, prod.effectiveVariants().first(), act.quantity)
                    }
                }
                onMarkAiCartDone(msgId)
                onShowMessage("Added AI-recommended items to Delivery Bag.")
            },
            onDismiss = { showAiSheet = false }
        )
    }
}

@Composable
private fun CustomerProfileEditor(
    customer: CustomerRecord?,
    fallbackName: String,
    branchCoord: Pair<Double, Double>,
    onSave: (String, String, String, String, Double, Double, String?, String?) -> Unit
) {
    var phone by remember(customer) { mutableStateOf(customer?.phone ?: "") }
    var barangay by remember(customer) { mutableStateOf(customer?.barangay ?: "Brgy. Sabutan") }
    var address by remember(customer) { mutableStateOf(customer?.address ?: "") }
    var lat by remember(customer) { mutableStateOf(customer?.lat ?: branchCoord.first) }
    var lng by remember(customer) { mutableStateOf(customer?.lng ?: branchCoord.second) }
    var newPin by remember { mutableStateOf("") }
    var storeImg by remember(customer) { mutableStateOf(customer?.storeImageUri) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) storeImg = uri.toString()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Profile Settings ($fallbackName)", style = MaterialTheme.typography.titleLarge)
        if (customer == null) {
            Text("Register or sign in as a customer to save persistent delivery pin and store photo.", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
        }
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = barangay, onValueChange = { barangay = it }, label = { Text("Barangay") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Delivery Address") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(6) }, label = { Text("New PIN (leave blank to keep)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        RouteMapPreview(
            branchName = "Branch",
            branchLat = branchCoord.first,
            branchLng = branchCoord.second,
            customerName = fallbackName,
            customerLat = lat,
            customerLng = lng,
            interactivePinSelection = true,
            onMapCoordinateSelected = { pLat, pLng -> lat = pLat; lng = pLng }
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("Update Store Photo")
            }
            if (!storeImg.isNullOrBlank()) {
                AsyncImage(
                    model = storeImg,
                    contentDescription = "Customer store photo",
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Button(
            onClick = { onSave(fallbackName, phone, barangay, address, lat, lng, newPin.ifBlank { null }, storeImg) },
            enabled = customer != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
        ) {
            Text("Save Profile Changes", fontWeight = FontWeight.Bold)
        }
    }
}
