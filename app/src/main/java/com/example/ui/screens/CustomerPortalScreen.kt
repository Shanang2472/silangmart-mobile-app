package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.*
import com.example.data.network.LocationSearchResult
import com.example.data.network.RouteNetworkHelper
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
    var selectedTrackOrderId by remember { mutableStateOf<String?>(null) }

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
                                                TextButton(onClick = {
                                                    selectedTrackOrderId = del.id
                                                    subTab = "tracking"
                                                }) {
                                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = SilangOrange)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Track GPS", color = SilangOrange)
                                                }
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
                    val trackableOrders = remember(myOrders) {
                        val nonDeleted = myOrders.filter { it.deletedAt == null }
                        val active = nonDeleted.filter { it.status == "Out for Delivery" || it.status == "Preparing" || it.status == "Pending" }
                        val past = nonDeleted.filter { it !in active }
                        active + past
                    }
                    val activeTrack = trackableOrders.firstOrNull { it.id == selectedTrackOrderId }
                        ?: trackableOrders.firstOrNull()

                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeTrack == null) {
                            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("No Deliveries to Track", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Place an order in Shop to watch your rider and delivery route live on the street map!",
                                        color = SilangSlate600,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(onClick = { subTab = "shop" }, colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)) {
                                        Text("Browse Shop")
                                    }
                                }
                            }
                        } else {
                            if (trackableOrders.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    trackableOrders.forEach { ord ->
                                        FilterChip(
                                            selected = ord.id == activeTrack.id,
                                            onClick = { selectedTrackOrderId = ord.id },
                                            label = { Text("${ord.id} (${ord.status})") }
                                        )
                                    }
                                }
                            }

                            val bCoord = state.branchCoordinates[activeTrack.branch] ?: (14.2235 to 120.9739)
                            Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Order ${activeTrack.id}", style = MaterialTheme.typography.titleMedium)
                                            Text("${activeTrack.branch} Branch → ${activeTrack.barangay}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                        }
                                        Surface(
                                            color = when (activeTrack.status) {
                                                "Delivered" -> SilangGreenContainer
                                                "Out for Delivery" -> SilangOrangeContainer
                                                "Cancelled" -> SilangRedContainer
                                                else -> SilangBlueContainer
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(activeTrack.status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }

                                    // Visual Delivery Stepper
                                    val steps = listOf("Pending", "Preparing", "Out for Delivery", "Delivered")
                                    val currentIdx = steps.indexOf(activeTrack.status).coerceAtLeast(0)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        steps.forEachIndexed { idx, label ->
                                            val done = activeTrack.status != "Cancelled" && idx <= currentIdx
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(if (done) SilangGreen else SilangSlate200),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        if (done) Icons.Default.Check else Icons.Default.Circle,
                                                        contentDescription = null,
                                                        tint = if (done) SilangWhite else SilangSlate400,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = if (label == "Out for Delivery") "En Route" else label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (done) SilangSlate900 else SilangSlate400,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Rider: ${activeTrack.driverName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("ETA ~${activeTrack.etaMinutes} mins • ${formatPhp(activeTrack.totalAmount)}", style = MaterialTheme.typography.labelMedium, color = SilangOrange)
                                    }
                                    Text("Drop-off: ${activeTrack.address}, ${activeTrack.barangay}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
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
                                driverLng = activeTrack.driverLng,
                                title = "Live Delivery Route (${activeTrack.id})",
                                compactHeightDp = 280
                            )
                        }
                    }
                }

                "profile" -> {
                    CustomerProfileEditor(
                        customer = customerProfile,
                        fallbackName = activeName,
                        branchName = state.currentBranch,
                        branchCoord = state.branchCoordinates[state.currentBranch] ?: (14.2235 to 120.9739),
                        onSave = onUpdateProfile,
                        onShowMessage = onShowMessage
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
        val branchCoord = state.branchCoordinates[state.currentBranch] ?: (14.2235 to 120.9739)
        var paymentMethod by remember { mutableStateOf("Cash") }
        var paymentRef by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var deliveryAddress by remember(customerProfile) { mutableStateOf(customerProfile?.address ?: "Silang, Cavite") }
        var deliveryBarangay by remember(customerProfile) { mutableStateOf(customerProfile?.barangay ?: "Brgy. Sabutan") }
        var destLat by remember(customerProfile, state.currentBranch) { mutableStateOf(customerProfile?.lat ?: (branchCoord.first + 0.004)) }
        var destLng by remember(customerProfile, state.currentBranch) { mutableStateOf(customerProfile?.lng ?: (branchCoord.second + 0.004)) }
        var searchLocQuery by remember { mutableStateOf("") }
        var searchLocHits by remember { mutableStateOf<List<LocationSearchResult>>(emptyList()) }
        var proofUri by remember { mutableStateOf<String?>(null) }

        val itemsTotal = cart.sumOf { it.lineTotal }
        val deliveryFee = 50.0
        val grandTotal = itemsTotal + deliveryFee

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

                // Delivery Address & Interactive Drop-off Map Pin
                Text("Drop-off Location & GPS Pin", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = deliveryBarangay,
                    onValueChange = { deliveryBarangay = it },
                    label = { Text("Barangay") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deliveryAddress,
                    onValueChange = { deliveryAddress = it },
                    label = { Text("Street / Landmark *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchLocQuery,
                        onValueChange = { searchLocQuery = it },
                        label = { Text("Search street in Silang...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            scope.launch { searchLocHits = RouteNetworkHelper.searchLocations(searchLocQuery) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
                    ) { Text("Find") }
                }
                searchLocHits.forEach { hit ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            destLat = hit.lat
                            destLng = hit.lng
                            if (hit.barangay.isNotBlank()) deliveryBarangay = hit.barangay
                            deliveryAddress = hit.displayName
                            searchLocHits = emptyList()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = SilangWhite
                    ) {
                        Text(hit.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }

                RouteMapPreview(
                    branchName = state.currentBranch,
                    branchLat = branchCoord.first,
                    branchLng = branchCoord.second,
                    customerName = activeName,
                    customerLat = destLat,
                    customerLng = destLng,
                    interactivePinSelection = true,
                    onMapCoordinateSelected = { pLat, pLng -> destLat = pLat; destLng = pLng },
                    compactHeightDp = 175
                )

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
                    DeviceImagePickerField(
                        label = "$paymentMethod Screenshot (Optional)",
                        imageUri = proofUri,
                        onImageSelected = { proofUri = it },
                        testTagPrefix = "payment_proof"
                    )
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
                            address = deliveryAddress.trim().ifBlank { "Silang, Cavite" },
                            barangay = deliveryBarangay.trim().ifBlank { "Brgy. Sabutan" },
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
                                selectedTrackOrderId = del.id
                                subTab = "tracking"
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

@SuppressLint("MissingPermission")
@Composable
private fun CustomerProfileEditor(
    customer: CustomerRecord?,
    fallbackName: String,
    branchName: String,
    branchCoord: Pair<Double, Double>,
    onSave: (String, String, String, String, Double, Double, String?, String?) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phone by remember(customer) { mutableStateOf(customer?.phone ?: "") }
    var barangay by remember(customer) { mutableStateOf(customer?.barangay ?: "Brgy. Sabutan") }
    var address by remember(customer) { mutableStateOf(customer?.address ?: "") }
    var lat by remember(customer) { mutableStateOf(customer?.lat ?: branchCoord.first) }
    var lng by remember(customer) { mutableStateOf(customer?.lng ?: branchCoord.second) }
    var newPin by remember { mutableStateOf("") }
    var storeImg by remember(customer) { mutableStateOf(customer?.storeImageUri) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<LocationSearchResult>>(emptyList()) }

    fun applyDeviceGps() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) {
            lat = loc.latitude
            lng = loc.longitude
            onShowMessage("GPS pin updated to current device location.")
        } else {
            onShowMessage("No cached GPS fix on device yet — tap the map to place your pin.")
        }
    }

    val gpsPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            applyDeviceGps()
        } else {
            onShowMessage("Location permission denied.")
        }
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search address in Silang...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { scope.launch { searchResults = RouteNetworkHelper.searchLocations(searchQuery) } },
                colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
            ) { Text("Find") }
        }
        searchResults.forEach { hit ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    lat = hit.lat
                    lng = hit.lng
                    if (hit.barangay.isNotBlank()) barangay = hit.barangay
                    address = hit.displayName
                    searchResults = emptyList()
                },
                shape = RoundedCornerShape(10.dp),
                color = SilangWhite
            ) {
                Text(hit.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
            }
        }

        OutlinedButton(
            onClick = {
                val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) applyDeviceGps()
                else gpsPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Use My Current GPS Location")
        }

        RouteMapPreview(
            branchName = branchName,
            branchLat = branchCoord.first,
            branchLng = branchCoord.second,
            customerName = fallbackName,
            customerLat = lat,
            customerLng = lng,
            interactivePinSelection = true,
            onMapCoordinateSelected = { pLat, pLng -> lat = pLat; lng = pLng }
        )

        DeviceImagePickerField(
            label = "Store / Gate Photo",
            imageUri = storeImg,
            onImageSelected = { storeImg = it },
            testTagPrefix = "profile_store_photo"
        )

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
