package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.SilangUiState
import com.example.ui.components.DeviceMultiImagePickerField
import com.example.ui.components.ProductThumbnail
import com.example.ui.theme.*
import java.time.LocalDate

@Composable
fun AdminInventoryScreen(
    state: SilangUiState,
    onSaveProduct: (Product) -> Unit,
    onAdjustStock: (String, String, Int) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onSaveCategories: (List<String>) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onSetStockLimit: (Int) -> Unit,
    onReceiveSupplierDelivery: (String, String) -> Unit,
    onLoadStarterCatalog: () -> Unit
) {
    var subTab by remember { mutableStateOf("stocks") } // stocks, incoming
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") } // All, Low Stock, Expiring/Expired, Overstock
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showAddProduct by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showLimitDialog by remember { mutableStateOf(false) }

    val filteredProducts = remember(state.products, search, statusFilter, state.currentBranch) {
        state.products.filter { p ->
            val q = search.trim().lowercase()
            val matchQ = q.isBlank() || p.name.lowercase().contains(q) || p.barcode.contains(q) || p.supplierName.lowercase().contains(q)
            val stock = p.stockForBranch(state.currentBranch)
            val exp = p.expiryStatus()
            val matchFilter = when (statusFilter) {
                "Low Stock" -> stock <= p.lowStockThreshold
                "Expiring/Expired" -> exp == ExpiryStatus.EXPIRED || exp == ExpiryStatus.EXPIRING
                "Overstock" -> stock >= p.overStockThreshold
                else -> true
            }
            matchQ && matchFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-navigation & Tools
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = subTab == "stocks", onClick = { subTab = "stocks" }, label = { Text("Products (${state.products.size})") })
            FilterChip(
                selected = subTab == "incoming",
                onClick = { subTab = "incoming" },
                label = { Text("Supplier Incoming (${state.supplierDeliveries.count { it.status == "Pending" }})") }
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showCategoryManager = true }) {
                Icon(Icons.Default.Category, contentDescription = "Manage Categories")
            }
            IconButton(onClick = { showLimitDialog = true }) {
                Icon(Icons.Default.Tune, contentDescription = "Bulk Stock Limit")
            }
        }

        if (subTab == "stocks") {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search name, barcode, supplier...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("inventory_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = { showAddProduct = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SilangOrange),
                        modifier = Modifier.height(52.dp).testTag("add_product_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("All", "Low Stock", "Expiring/Expired", "Overstock").forEach { f ->
                        FilterChip(selected = statusFilter == f, onClick = { statusFilter = f }, label = { Text(f) })
                    }
                }
            }

            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No products found.", color = SilangSlate600)
                        if (state.products.isEmpty()) {
                            Button(onClick = onLoadStarterCatalog, colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)) {
                                Text("Load Starter Catalog")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { prod ->
                        val stock = prod.stockForBranch(state.currentBranch)
                        val exp = prod.expiryStatus()
                        Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ProductThumbnail(prod)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${prod.category} • ${formatPhp(prod.price)} (Cost ${formatPhp(prod.cost)})", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                        Text(
                                            "Batch: ${prod.batchNumber.ifBlank { "-" }} • Exp: ${prod.expiryDate.ifBlank { "-" }} • ${prod.authenticityStatus}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (exp == ExpiryStatus.EXPIRED || prod.authenticityStatus == "rejected") SilangRed else SilangSlate400
                                        )
                                    }
                                    IconButton(onClick = { editingProduct = prod }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit product")
                                    }
                                    IconButton(onClick = { onDeleteProduct(prod.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete product", tint = SilangRed)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${state.currentBranch}: $stock ${prod.unit}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (stock <= prod.lowStockThreshold) SilangRed else if (stock >= prod.overStockThreshold) SilangAmber else SilangGreen
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { onAdjustStock(prod.id, state.currentBranch, -1) }) { Text("-1") }
                                        FilledTonalButton(onClick = { onAdjustStock(prod.id, state.currentBranch, 10) }) { Text("+10") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Incoming Supplier Deliveries
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.supplierDeliveries.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No supplier deliveries scheduled.", color = SilangSlate600)
                        }
                    }
                } else {
                    items(state.supplierDeliveries, key = { it.id }) { sd ->
                        Card(colors = CardDefaults.cardColors(containerColor = SilangWhite), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${sd.id} • ${sd.supplierName}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        sd.status,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (sd.status == "Received") SilangGreen else SilangAmber
                                    )
                                }
                                Text("Branch: ${sd.branch} • Date: ${sd.deliveryDate}", style = MaterialTheme.typography.bodySmall, color = SilangSlate600)
                                Text(sd.items.joinToString(" • ") { "${it.quantity}x ${it.productName}" }, style = MaterialTheme.typography.bodySmall)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Cost: ${formatPhp(sd.totalCost)}", style = MaterialTheme.typography.titleSmall, color = SilangBlue)
                                    if (sd.status == "Pending") {
                                        Button(
                                            onClick = { onReceiveSupplierDelivery(sd.id, state.activeCashier) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SilangGreen)
                                        ) {
                                            Text("Mark Received (+Stock)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProduct || editingProduct != null) {
        ProductEditorDialog(
            initial = editingProduct,
            branches = state.branches,
            categories = state.categories,
            defaultLimit = state.defaultStockLimit,
            onDismiss = {
                showAddProduct = false
                editingProduct = null
            },
            onSave = { prod ->
                onSaveProduct(prod)
                showAddProduct = false
                editingProduct = null
            }
        )
    }

    if (showCategoryManager) {
        CategoryManagerDialog(
            categories = state.categories,
            onAdd = { cat -> onSaveCategories(state.categories + cat) },
            onRename = onRenameCategory,
            onDelete = { cat -> onSaveCategories(state.categories.filterNot { it == cat }) },
            onDismiss = { showCategoryManager = false }
        )
    }

    if (showLimitDialog) {
        var limitInput by remember { mutableStateOf(state.defaultStockLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Bulk Overstock Limit") },
            text = {
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it.filter(Char::isDigit) },
                    label = { Text("Max Stock Capacity per Product") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    limitInput.toIntOrNull()?.let { onSetStockLimit(it) }
                    showLimitDialog = false
                }) { Text("Apply to All") }
            },
            dismissButton = { TextButton(onClick = { showLimitDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    initial: Product?,
    branches: List<BranchRecord>,
    categories: List<String>,
    defaultLimit: Int,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var tagalog by remember { mutableStateOf(initial?.tagalogName ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: categories.firstOrNull() ?: "Other") }
    var price by remember { mutableStateOf(initial?.price?.toString() ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "pc") }
    var barcode by remember { mutableStateOf(initial?.barcode ?: "480${System.currentTimeMillis().toString().takeLast(9)}") }
    var batchNumber by remember { mutableStateOf(initial?.batchNumber ?: "LOT-${LocalDate.now().toString().replace("-", "")}") }
    var expiryDate by remember { mutableStateOf(initial?.expiryDate ?: LocalDate.now().plusMonths(6).toString()) }
    var supplierName by remember { mutableStateOf(initial?.supplierName ?: "SupplierSilang") }
    var authStatus by remember { mutableStateOf(initial?.authenticityStatus ?: "verified") }
    var lowThreshold by remember { mutableStateOf((initial?.lowStockThreshold ?: 10).toString()) }
    var productImages by remember { mutableStateOf(initial?.images ?: emptyList()) }

    val branchStocks = remember {
        mutableStateMapOf<String, String>().apply {
            branches.forEach { b ->
                put(b.name, (initial?.stockForBranch(b.name) ?: 50).toString())
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Product" else "Edit Product") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name *") }, singleLine = true)
                OutlinedTextField(value = tagalog, onValueChange = { tagalog = it }, label = { Text("Tagalog Name (Optional)") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price (₱) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = { Text("Cost (₱)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit (pc/kg/can)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(
                        value = lowThreshold,
                        onValueChange = { lowThreshold = it.filter(Char::isDigit) },
                        label = { Text("Low Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Text("Branch Stock Quantities", style = MaterialTheme.typography.labelMedium)
                branches.forEach { b ->
                    OutlinedTextField(
                        value = branchStocks[b.name] ?: "0",
                        onValueChange = { branchStocks[b.name] = it.filter(Char::isDigit) },
                        label = { Text("${b.name} Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                Text("Traceability & Safety", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = supplierName, onValueChange = { supplierName = it }, label = { Text("Supplier Name *") }, singleLine = true)
                OutlinedTextField(value = batchNumber, onValueChange = { batchNumber = it }, label = { Text("Batch / Lot Number *") }, singleLine = true)
                OutlinedTextField(value = expiryDate, onValueChange = { expiryDate = it }, label = { Text("Expiry Date (YYYY-MM-DD) *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("verified", "unverified", "rejected").forEach { st ->
                        FilterChip(selected = authStatus == st, onClick = { authStatus = st }, label = { Text(st) })
                    }
                }
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode") }, singleLine = true)
                DeviceMultiImagePickerField(
                    label = "Product Photos (Device Upload)",
                    images = productImages,
                    onImagesChanged = { productImages = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pVal = price.toDoubleOrNull() ?: return@Button
                    if (name.isBlank()) return@Button
                    val cVal = cost.toDoubleOrNull() ?: (pVal * 0.8)
                    val stockMap = branchStocks.mapValues { it.value.toIntOrNull() ?: 0 }
                    onSave(
                        Product(
                            id = initial?.id ?: "PRD-${System.currentTimeMillis().toString().takeLast(5)}",
                            name = name.trim(),
                            tagalogName = tagalog.trim(),
                            category = category.trim().ifBlank { "Other" },
                            price = pVal,
                            cost = cVal,
                            batchNumber = batchNumber.trim(),
                            expiryDate = expiryDate.trim(),
                            supplierName = supplierName.trim(),
                            authenticityStatus = authStatus,
                            stockByBranch = stockMap,
                            shelfLocation = initial?.shelfLocation ?: "Main Aisle",
                            barcode = barcode.trim(),
                            unit = unit.trim().ifBlank { "pc" },
                            lowStockThreshold = lowThreshold.toIntOrNull() ?: 10,
                            overStockThreshold = initial?.overStockThreshold ?: defaultLimit,
                            images = productImages,
                            variants = initial?.variants ?: emptyList()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SilangOrange)
            ) { Text("Save Product") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CategoryManagerDialog(
    categories: List<String>,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newCategory by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newCategory, onValueChange = { newCategory = it }, label = { Text("New category") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = {
                        if (newCategory.isNotBlank()) {
                            onAdd(newCategory.trim())
                            newCategory = ""
                        }
                    }) { Text("Add") }
                }
                categories.forEach { cat ->
                    var renameTarget by remember(cat) { mutableStateOf(cat) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = renameTarget, onValueChange = { renameTarget = it }, modifier = Modifier.weight(1f), singleLine = true)
                        IconButton(onClick = { if (renameTarget.isNotBlank() && renameTarget != cat) onRename(cat, renameTarget) }) {
                            Icon(Icons.Default.Check, contentDescription = "Rename")
                        }
                        IconButton(onClick = { onDelete(cat) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = SilangRed)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}
