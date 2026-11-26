package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.AdminTab
import com.example.ui.SilangUiState
import com.example.ui.components.PosReceiptDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun AdminDashboardOverview(
    state: SilangUiState,
    onNavigateTab: (AdminTab) -> Unit,
    onQuickRestock: (productId: String, branchName: String, delta: Int) -> Unit,
    onLoadStarterCatalog: () -> Unit
) {
    val todayPrefix = remember { LocalDate.now().toString() }
    val branchTxns = state.transactions.filter { it.branch == state.currentBranch }
    val todaySales = branchTxns.filter { it.timestamp.startsWith(todayPrefix) }.sumOf { it.total } +
        state.deliveries.filter { it.branch == state.currentBranch && it.timestamp.startsWith(todayPrefix) && it.status != "Cancelled" }.sumOf { it.totalAmount }
    val pendingDeliveries = state.deliveries.count { it.status == "Pending" || it.status == "Preparing" || it.status == "Out for Delivery" }
    val activeRiders = state.users.count { it.role == "rider" && it.status == "active" }
    val lowStockProducts = state.products.filter { it.stockForBranch(state.currentBranch) <= it.lowStockThreshold }
    val outOfStockCount = state.products.count { it.stockForBranch(state.currentBranch) <= 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (state.products.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SilangOrangeContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Store Catalog Empty", style = MaterialTheme.typography.titleMedium, color = SilangSlate900)
                        Text(
                            "Add products manually in Stocks or load the Silang Mini Mart starter catalog to test POS, Delivery, and Inventory immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SilangSlate600
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onLoadStarterCatalog,
                                colors = ButtonDefaults.buttonColors(containerColor = SilangOrange),
                                modifier = Modifier.testTag("load_starter_catalog_button")
                            ) {
                                Text("Load Starter Catalog")
                            }
                            OutlinedButton(onClick = { onNavigateTab(AdminTab.INVENTORY) }) {
                                Text("Go to Stocks")
                            }
                        }
                    }
                }
            }
        }

        // KPI Grid
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Today's Revenue", formatPhp(todaySales), "${branchTxns.size} POS sales", SilangBlue, Modifier.weight(1f))
                KpiCard("Active Deliveries", "$pendingDeliveries", "$activeRiders active riders", SilangOrange, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KpiCard("Total Products", "${state.products.size}", "${state.currentBranch} branch", SilangGreen, Modifier.weight(1f))
                KpiCard("Low / Out Stock", "${lowStockProducts.size}", "$outOfStockCount out of stock", if (lowStockProducts.isNotEmpty()) SilangRed else SilangGreen, Modifier.weight(1f))
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(onClick = { onNavigateTab(AdminTab.POS) }) {
                    Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open POS")
                }
                FilledTonalButton(onClick = { onNavigateTab(AdminTab.DELIVERIES) }) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delivery Queue")
                }
                FilledTonalButton(onClick = { onNavigateTab(AdminTab.SERVICES) }) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("E-Load & Bills")
                }
                FilledTonalButton(onClick = { onNavigateTab(AdminTab.ANALYTICS) }) {
                    Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reports")
                }
            }
        }

        // Low Stock Attention
        item {
            Text("Low Stock Attention (${state.currentBranch})", style = MaterialTheme.typography.titleMedium)
        }
        if (lowStockProducts.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                    Text(
                        "All products at ${state.currentBranch} are sufficiently stocked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilangSlate600,
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    )
                }
            }
        } else {
            items(lowStockProducts.take(6), key = { it.id }) { prod ->
                val stock = prod.stockForBranch(state.currentBranch)
                Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProductThumbnail(prod, modifier = Modifier.size(44.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prod.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "$stock ${prod.unit} remaining (min ${prod.lowStockThreshold})",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (stock <= 0) SilangRed else SilangAmber
                            )
                        }
                        FilledTonalButton(
                            onClick = { onQuickRestock(prod.id, state.currentBranch, 20) },
                            modifier = Modifier.testTag("quick_restock_${prod.id}")
                        ) {
                            Text("+20 Restock")
                        }
                    }
                }
            }
        }

        // Latest Transactions & Orders
        item {
            Text("Recent Store Activity", style = MaterialTheme.typography.titleMedium)
        }
        if (state.transactions.isEmpty() && state.deliveries.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                    Text(
                        "No completed sales or delivery orders yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SilangSlate600,
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    )
                }
            }
        } else {
            items(state.deliveries.take(4), key = { it.id }) { del ->
                Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${del.id} • ${del.customerName}", style = MaterialTheme.typography.titleSmall)
                            Text("${del.barangay} • ${del.status} • ${del.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                        }
                        Text(formatPhp(del.totalAmount), style = MaterialTheme.typography.titleSmall, color = SilangBlue)
                    }
                }
            }
            items(state.transactions.take(4), key = { it.id }) { txn ->
                Card(colors = CardDefaults.cardColors(containerColor = SilangWhite)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${txn.id} • POS Sale", style = MaterialTheme.typography.titleSmall)
                            Text("${txn.branch} • ${txn.items.size} items • ${txn.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                        }
                        Text(formatPhp(txn.total), style = MaterialTheme.typography.titleSmall, color = SilangGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, subtitle: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SilangWhite),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = SilangSlate600)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SilangSlate400, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPosScreen(
    state: SilangUiState,
    onCheckout: suspend (Transaction, List<CartItem>) -> Result<Unit>,
    onShowMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var cart by remember { mutableStateOf<List<CartItem>>(emptyList()) }
    var variantPickerProduct by remember { mutableStateOf<Product?>(null) }
    var showCartSheet by remember { mutableStateOf(false) }
    var receiptToShow by remember { mutableStateOf<Transaction?>(null) }

    var discountType by remember { mutableStateOf("none") } // none, senior, pwd, percentage
    var customDiscountPercent by remember { mutableStateOf("10") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var amountPaidText by remember { mutableStateOf("") }

    val categories = remember(state.categories) { listOf("All") + state.categories }
    val filtered = remember(state.products, search, selectedCategory) {
        state.products.filter { p ->
            val q = search.trim().lowercase()
            val matchSearch = q.isBlank() || p.name.lowercase().contains(q) ||
                p.tagalogName.lowercase().contains(q) || p.barcode.contains(q)
            val matchCat = selectedCategory == "All" || p.category == selectedCategory
            matchSearch && matchCat
        }
    }

    val subtotal = cart.sumOf { it.lineTotal }
    val discountAmount = when (discountType) {
        "senior", "pwd" -> (subtotal * 0.20 * 100.0).roundToInt() / 100.0
        "percentage" -> {
            val pct = (customDiscountPercent.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
            (subtotal * (pct / 100.0) * 100.0).roundToInt() / 100.0
        }
        else -> 0.0
    }
    val total = ((subtotal - discountAmount) * 100.0).roundToInt() / 100.0
    val vatAmount = (total * 12.0 / 112.0 * 100.0).roundToInt() / 100.0

    fun addItemToCart(product: Product, variant: PackagingVariant) {
        if (!product.isSellable()) {
            onShowMessage("${product.name} cannot be sold (expired, missing batch/supplier, or unverified).")
            return
        }
        val available = product.stockForBranch(state.currentBranch)
        val alreadyInCartUnits = cart.filter { it.product.id == product.id }.sumOf { it.totalBaseUnits }
        if (alreadyInCartUnits + variant.qtyPerUnit > available) {
            onShowMessage("Insufficient stock at ${state.currentBranch} ($available ${product.unit} available).")
            return
        }
        val existingIdx = cart.indexOfFirst { it.product.id == product.id && it.selectedVariant.type == variant.type }
        cart = if (existingIdx >= 0) {
            cart.toMutableList().also { list -> list[existingIdx] = list[existingIdx].copy(quantity = list[existingIdx].quantity + 1) }
        } else {
            cart + CartItem(product, 1, variant)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.background(SilangWhite).padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search product, Tagalog name, or barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("pos_search_input")
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No matching products in catalog.", color = SilangSlate600)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { prod ->
                        val stock = prod.stockForBranch(state.currentBranch)
                        val exp = prod.expiryStatus()
                        val sellable = prod.isSellable() && stock > 0
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val variants = prod.effectiveVariants()
                                    if (variants.size > 1) variantPickerProduct = prod
                                    else addItemToCart(prod, variants.first())
                                },
                            colors = CardDefaults.cardColors(containerColor = SilangWhite),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ProductThumbnail(prod)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(prod.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (prod.tagalogName.isNotBlank()) {
                                        Text(prod.tagalogName, style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = if (stock <= prod.lowStockThreshold) SilangAmberContainer else SilangGreenContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "$stock ${prod.unit}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (stock <= prod.lowStockThreshold) SilangAmber else SilangGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (exp == ExpiryStatus.EXPIRED || prod.authenticityStatus != "verified") {
                                            Surface(color = SilangRedContainer, shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    if (exp == ExpiryStatus.EXPIRED) "EXPIRED" else prod.authenticityStatus.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = SilangRed,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatPhp(prod.price), style = MaterialTheme.typography.titleMedium, color = SilangBlue)
                                    FilledTonalButton(
                                        onClick = {
                                            val variants = prod.effectiveVariants()
                                            if (variants.size > 1) variantPickerProduct = prod
                                            else addItemToCart(prod, variants.first())
                                        },
                                        enabled = sellable,
                                        modifier = Modifier.testTag("pos_add_${prod.id}")
                                    ) {
                                        Text(if (prod.variants.size > 1) "Variants" else "+ Add")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sticky Cart Checkout Bar
        if (cart.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                color = SilangSlate900,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCartSheet = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("pos_open_checkout_bar"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${cart.sumOf { it.quantity }} item(s) in Cart", style = MaterialTheme.typography.labelMedium, color = SilangOrangeBright)
                        Text("Total: ${formatPhp(total)} (VAT incl.)", style = MaterialTheme.typography.titleMedium, color = SilangWhite)
                    }
                    Button(
                        onClick = { showCartSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
                    ) {
                        Text("Checkout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Packaging Variant Picker
    variantPickerProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { variantPickerProduct = null },
            title = { Text("Select Packaging • ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prod.effectiveVariants().forEach { v ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    addItemToCart(prod, v)
                                    variantPickerProduct = null
                                },
                            colors = CardDefaults.cardColors(containerColor = SilangSurface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(v.label.ifBlank { v.type }, style = MaterialTheme.typography.titleSmall)
                                    Text("${v.qtyPerUnit} ${prod.unit} per ${v.unitName}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                }
                                Text(formatPhp(v.price), style = MaterialTheme.typography.titleSmall, color = SilangBlue)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { variantPickerProduct = null }) { Text("Cancel") }
            }
        )
    }

    // Checkout Bottom Sheet
    if (showCartSheet) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                showCartSheet = false
            },
            sheetState = sheetState,
            containerColor = SilangSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("POS Checkout (${state.currentBranch})", style = MaterialTheme.typography.titleLarge)
                cart.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.selectedVariant.label} • ${formatPhp(item.selectedVariant.price)}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                cart = if (item.quantity <= 1) cart.filterIndexed { i, _ -> i != idx }
                                else cart.toMutableList().also { it[idx] = item.copy(quantity = item.quantity - 1) }
                            }) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease") }
                            Text("${item.quantity}", style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { addItemToCart(item.product, item.selectedVariant) }) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("Discount", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("none" to "None", "senior" to "Senior 20%", "pwd" to "PWD 20%", "percentage" to "Custom %").forEach { (k, label) ->
                        FilterChip(selected = discountType == k, onClick = { discountType = k }, label = { Text(label) })
                    }
                }
                if (discountType == "percentage") {
                    OutlinedTextField(
                        value = customDiscountPercent,
                        onValueChange = { customDiscountPercent = it },
                        label = { Text("Discount %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Cash", "GCash", "Maya").forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = {
                                paymentMethod = method
                                if (method != "Cash") amountPaidText = "%.2f".format(total)
                            },
                            label = { Text(method) }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountPaidText,
                    onValueChange = { amountPaidText = it },
                    label = { Text("Amount Tendered (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("pos_amount_paid_input")
                )

                val paidNum = amountPaidText.toDoubleOrNull() ?: 0.0
                val changeVal = (paidNum - total).coerceAtLeast(0.0)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total (incl. 12% VAT ${formatPhp(vatAmount)})", style = MaterialTheme.typography.titleSmall)
                    Text(formatPhp(total), style = MaterialTheme.typography.titleLarge, color = SilangBlue)
                }
                if (paidNum >= total && total > 0) {
                    Text("Change: ${formatPhp(changeVal)}", style = MaterialTheme.typography.titleSmall, color = SilangGreen)
                }

                Button(
                    onClick = {
                        if (paidNum < total) {
                            onShowMessage("Insufficient amount paid. Minimum is ${formatPhp(total)}.")
                            return@Button
                        }
                        val txn = Transaction(
                            id = "TXN-${System.currentTimeMillis().toString().takeLast(6)}",
                            branch = state.currentBranch,
                            items = cart.map {
                                TransactionItem(
                                    productId = it.product.id,
                                    name = "${it.product.name} [${it.selectedVariant.label}]",
                                    price = it.selectedVariant.price,
                                    quantity = it.quantity,
                                    unit = it.selectedVariant.unitName
                                )
                            },
                            subtotal = subtotal,
                            discountType = discountType,
                            discountAmount = discountAmount,
                            vatAmount = vatAmount,
                            total = total,
                            paymentMethod = paymentMethod,
                            amountPaid = paidNum,
                            changeAmount = changeVal,
                            timestamp = java.time.Instant.now().toString(),
                            cashier = state.activeCashier
                        )
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        scope.launch {
                            val res = onCheckout(txn, cart)
                            if (res.isSuccess) {
                                showCartSheet = false
                                receiptToShow = txn
                                cart = emptyList()
                                amountPaidText = ""
                                discountType = "none"
                            }
                        }
                    },
                    enabled = cart.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pos_complete_sale_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SilangBlue)
                ) {
                    Text("Complete Sale & Print Receipt", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    receiptToShow?.let { txn ->
        PosReceiptDialog(transaction = txn, onDismiss = { receiptToShow = null })
    }
}
