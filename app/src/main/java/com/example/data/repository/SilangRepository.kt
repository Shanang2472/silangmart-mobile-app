package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

class SilangRepository(private val db: AppDatabase) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val branches: Flow<List<BranchRecord>> = db.branchDao().observeAll().map { list ->
        list.map { BranchRecord(it.id, it.name, it.address, it.lat, it.lng, it.isActive) }
    }

    val users: Flow<List<UserRecord>> = db.userDao().observeAll().map { list ->
        list.map { UserRecord(it.id, it.name, it.username, it.passwordHash, it.role, it.status) }
    }

    val customers: Flow<List<CustomerRecord>> = db.customerDao().observeAll().map { list ->
        list.map {
            CustomerRecord(it.id, it.name, it.phone, it.pinHash, it.barangay, it.address, it.lat, it.lng, it.storeImageUri)
        }
    }

    val products: Flow<List<Product>> = db.productDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val transactions: Flow<List<Transaction>> = db.transactionDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val deliveries: Flow<List<Delivery>> = db.deliveryDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val serviceTransactions: Flow<List<ServiceTransaction>> = db.serviceTransactionDao().observeAll().map { list ->
        list.map {
            ServiceTransaction(
                id = it.id,
                branch = it.branch,
                type = it.type,
                provider = it.provider,
                recipient = it.recipient,
                senderOrAccountName = it.senderOrAccountName,
                amount = it.amount,
                serviceFee = it.serviceFee,
                totalAmount = it.totalAmount,
                status = it.status,
                timestamp = it.timestamp
            )
        }
    }

    val supplierDeliveries: Flow<List<SupplierDelivery>> = db.supplierDeliveryDao().observeAll().map { list ->
        list.map { it.toModel() }
    }

    val notifications: Flow<List<AppNotification>> = db.notificationDao().observeAll().map { list ->
        list.map {
            AppNotification(
                id = it.id,
                userId = it.userId,
                role = it.role,
                type = it.type,
                category = it.category,
                title = it.title,
                message = it.message,
                entityId = it.entityId,
                isRead = it.isRead,
                createdAt = it.createdAt
            )
        }
    }

    val categories: Flow<List<String>> = db.settingDao().observeAll().map { settings ->
        val raw = settings.firstOrNull { it.settingKey == KEY_CATEGORIES }?.settingValueJson
        if (raw != null) {
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrElse { DEFAULT_CATEGORIES }
        } else {
            DEFAULT_CATEGORIES
        }
    }

    val defaultStockLimit: Flow<Int> = db.settingDao().observeAll().map { settings ->
        val raw = settings.firstOrNull { it.settingKey == KEY_STOCK_LIMIT }?.settingValueJson
        raw?.toIntOrNull() ?: 500
    }

    suspend fun ensureSeeded() {
        if (db.branchDao().getAllOnce().isEmpty()) {
            db.branchDao().insert(
                BranchEntity(name = "Poblacion", address = "Silang Poblacion", lat = 14.2235, lng = 120.9739, isActive = true)
            )
            db.branchDao().insert(
                BranchEntity(name = "Bypass", address = "Silang Bypass Road", lat = 14.2300, lng = 120.9700, isActive = true)
            )
        }
        if (db.userDao().count() == 0) {
            db.userDao().insert(UserEntity(name = "AdminSilang", username = "admin", passwordHash = hashSecret("admin"), role = "admin"))
            db.userDao().insert(UserEntity(name = "Ate Lorna (Cashier)", username = "lorna", passwordHash = hashSecret("1234"), role = "cashier"))
            db.userDao().insert(UserEntity(name = "RiderSilang", username = "rider", passwordHash = hashSecret("1234"), role = "rider"))
            db.userDao().insert(UserEntity(name = "SupplierSilang", username = "supplier", passwordHash = hashSecret("1234"), role = "supplier"))
        }
        if (db.settingDao().get(KEY_CATEGORIES) == null) {
            db.settingDao().set(SettingEntity(KEY_CATEGORIES, json.encodeToString(DEFAULT_CATEGORIES)))
        }
        if (db.settingDao().get(KEY_STOCK_LIMIT) == null) {
            db.settingDao().set(SettingEntity(KEY_STOCK_LIMIT, "500"))
        }
    }

    // ─── Auth ───
    suspend fun loginStaffOrPartner(username: String, password: String, expectedRoleGroup: String): Result<LoggedInUser> {
        val user = db.userDao().findByUsername(username.trim())
            ?: return Result.failure(IllegalArgumentException("Account not found for \"$username\"."))
        if (user.status != "active") {
            return Result.failure(IllegalStateException("This account is currently inactive."))
        }
        if (!verifySecret(password, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("Incorrect username or password."))
        }
        val mappedRole = when (user.role.lowercase()) {
            "admin", "cashier" -> "admin"
            "rider" -> "rider"
            "supplier" -> "supplier"
            else -> user.role.lowercase()
        }
        val valid = when (expectedRoleGroup) {
            "admin" -> mappedRole == "admin"
            "rider" -> mappedRole == "rider"
            "supplier" -> mappedRole == "supplier"
            else -> true
        }
        if (!valid) {
            return Result.failure(IllegalArgumentException("Account \"$username\" belongs to ${user.role}, not $expectedRoleGroup."))
        }
        return Result.success(LoggedInUser(username = user.username, role = mappedRole, name = user.name))
    }

    suspend fun loginCustomer(nameOrPhone: String, pin: String): Result<LoggedInUser> {
        val q = nameOrPhone.trim()
        val found = db.customerDao().findByName(q) ?: db.customerDao().findByPhone(q)
            ?: return Result.failure(IllegalArgumentException("Customer account not found. Register a new account first."))
        if (!verifySecret(pin, found.pinHash)) {
            return Result.failure(IllegalArgumentException("Incorrect PIN."))
        }
        return Result.success(LoggedInUser(username = found.phone, role = "customer", name = found.name))
    }

    suspend fun registerCustomer(
        name: String,
        phone: String,
        pin: String,
        barangay: String,
        address: String,
        lat: Double,
        lng: Double,
        storeImageUri: String?
    ): Result<CustomerRecord> {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        if (cleanName.isBlank() || cleanPhone.length < 10 || pin.length < 4) {
            return Result.failure(IllegalArgumentException("Enter your name, valid phone number, and at least a 4-digit PIN."))
        }
        if (db.customerDao().findByName(cleanName) != null || db.customerDao().findByPhone(cleanPhone) != null) {
            return Result.failure(IllegalArgumentException("A customer with that name or phone number already exists."))
        }
        val entity = CustomerEntity(
            name = cleanName,
            phone = cleanPhone,
            pinHash = hashSecret(pin),
            barangay = barangay.ifBlank { "Brgy. Poblacion" },
            address = address.ifBlank { "Silang, Cavite" },
            lat = lat,
            lng = lng,
            storeImageUri = storeImageUri
        )
        val id = db.customerDao().insert(entity)
        addNotification(
            userId = "admin",
            role = "admin",
            type = "info",
            category = "account",
            title = "New Customer Registered",
            message = "$cleanName ($cleanPhone) registered in ${entity.barangay}."
        )
        return Result.success(CustomerRecord(id, entity.name, entity.phone, entity.pinHash, entity.barangay, entity.address, entity.lat, entity.lng, entity.storeImageUri))
    }

    suspend fun updateCustomerProfile(
        name: String,
        phone: String,
        barangay: String,
        address: String,
        lat: Double,
        lng: Double,
        newPin: String?,
        storeImageUri: String?
    ): Result<Unit> {
        val existing = db.customerDao().findByName(name)
            ?: return Result.failure(IllegalArgumentException("Customer record not found."))
        val updated = existing.copy(
            phone = phone.trim().ifBlank { existing.phone },
            barangay = barangay.trim().ifBlank { existing.barangay },
            address = address.trim().ifBlank { existing.address },
            lat = lat,
            lng = lng,
            pinHash = if (!newPin.isNullOrBlank()) hashSecret(newPin) else existing.pinHash,
            storeImageUri = storeImageUri ?: existing.storeImageUri
        )
        db.customerDao().update(updated)
        return Result.success(Unit)
    }

    // ─── Branches & Users ───
    suspend fun saveBranch(branch: BranchRecord, previousName: String? = null) {
        db.withTransaction {
            val entity = BranchEntity(
                id = branch.id,
                name = branch.name.trim(),
                address = branch.address.trim(),
                lat = branch.lat,
                lng = branch.lng,
                isActive = branch.isActive
            )
            if (branch.id == 0L) {
                db.branchDao().insert(entity)
                // Add 0 stock key for existing products
                db.productDao().getAllOnce().forEach { prodEntity ->
                    val prod = prodEntity.toModel()
                    if (!prod.stockByBranch.containsKey(entity.name)) {
                        val updatedMap = prod.stockByBranch.toMutableMap().apply { put(entity.name, 0) }
                        db.productDao().insert(prod.copy(stockByBranch = updatedMap).toEntity())
                    }
                }
            } else {
                db.branchDao().update(entity)
                if (!previousName.isNullOrBlank() && previousName != entity.name) {
                    db.productDao().getAllOnce().forEach { prodEntity ->
                        val prod = prodEntity.toModel()
                        if (prod.stockByBranch.containsKey(previousName)) {
                            val updatedMap = prod.stockByBranch.toMutableMap()
                            val qty = updatedMap.remove(previousName) ?: 0
                            updatedMap[entity.name] = qty
                            db.productDao().insert(prod.copy(stockByBranch = updatedMap).toEntity())
                        }
                    }
                }
            }
        }
    }

    suspend fun deleteBranch(id: Long) {
        db.branchDao().deleteById(id)
    }

    suspend fun createUser(name: String, username: String, password: String, role: String): Result<Unit> {
        val cleanUser = username.trim()
        if (name.isBlank() || cleanUser.isBlank() || password.length < 4) {
            return Result.failure(IllegalArgumentException("Name, username, and password (min 4 chars) are required."))
        }
        if (db.userDao().findByUsername(cleanUser) != null) {
            return Result.failure(IllegalArgumentException("Username \"$cleanUser\" already exists."))
        }
        db.userDao().insert(
            UserEntity(
                name = name.trim(),
                username = cleanUser,
                passwordHash = hashSecret(password),
                role = role.lowercase(),
                status = "active"
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteUser(id: Long) {
        db.userDao().deleteById(id)
    }

    // ─── Categories & Settings ───
    suspend fun saveCategories(list: List<String>) {
        db.settingDao().set(SettingEntity(KEY_CATEGORIES, json.encodeToString(list.distinct())))
    }

    suspend fun renameCategory(oldName: String, newName: String, currentCategories: List<String>) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        db.withTransaction {
            val updatedCategories = currentCategories.map { if (it == oldName) trimmed else it }.distinct()
            saveCategories(updatedCategories)
            db.productDao().getAllOnce().filter { it.category == oldName }.forEach {
                db.productDao().insert(it.copy(category = trimmed))
            }
        }
    }

    suspend fun setDefaultStockLimit(limit: Int) {
        val safe = limit.coerceAtLeast(1)
        db.withTransaction {
            db.settingDao().set(SettingEntity(KEY_STOCK_LIMIT, safe.toString()))
            db.productDao().getAllOnce().forEach {
                db.productDao().insert(it.copy(overStockThreshold = safe))
            }
        }
    }

    // ─── Products & Inventory ───
    suspend fun saveProduct(product: Product) {
        db.productDao().insert(product.toEntity())
        checkStockAlerts(product)
    }

    suspend fun adjustStock(productId: String, branchName: String, delta: Int) {
        val entity = db.productDao().findById(productId) ?: return
        val prod = entity.toModel()
        val current = prod.stockForBranch(branchName)
        val next = (current + delta).coerceAtLeast(0)
        val updated = prod.copy(stockByBranch = prod.stockByBranch.toMutableMap().apply { put(branchName, next) })
        db.productDao().insert(updated.toEntity())
        checkStockAlerts(updated, branchName)
    }

    suspend fun deleteProduct(id: String) {
        db.productDao().deleteById(id)
    }

    private suspend fun checkStockAlerts(product: Product, specificBranch: String? = null) {
        val targets = if (specificBranch != null) listOf(specificBranch) else product.stockByBranch.keys.toList()
        for (branch in targets) {
            val qty = product.stockForBranch(branch)
            if (qty <= 0) {
                addNotification(
                    userId = "admin",
                    role = "admin",
                    type = "danger",
                    category = "inventory",
                    title = "Out of Stock: ${product.name}",
                    message = "${product.name} is out of stock at $branch branch.",
                    entityId = product.id
                )
            } else if (qty <= product.lowStockThreshold) {
                addNotification(
                    userId = "admin",
                    role = "admin",
                    type = "warning",
                    category = "inventory",
                    title = "Low Stock: ${product.name}",
                    message = "Only $qty ${product.unit} left at $branch branch (threshold ${product.lowStockThreshold}).",
                    entityId = product.id
                )
            }
        }
    }

    // ─── POS Transactions ───
    suspend fun recordPosCheckout(transaction: Transaction, cartItems: List<CartItem>): Result<Unit> {
        return runCatching {
            db.withTransaction {
                for (item in cartItems) {
                    if (!item.product.isSellable()) {
                        throw IllegalStateException("${item.product.name} requires verified authenticity, valid expiry date, supplier, and batch number.")
                    }
                    val prod = db.productDao().findById(item.product.id)?.toModel()
                        ?: throw IllegalStateException("${item.product.name} not found.")
                    val available = prod.stockForBranch(transaction.branch)
                    if (item.totalBaseUnits > available) {
                        throw IllegalStateException("Insufficient stock for ${prod.name} at ${transaction.branch} ($available left).")
                    }
                    val updatedStock = available - item.totalBaseUnits
                    val updatedProd = prod.copy(
                        stockByBranch = prod.stockByBranch.toMutableMap().apply { put(transaction.branch, updatedStock) }
                    )
                    db.productDao().insert(updatedProd.toEntity())
                    checkStockAlerts(updatedProd, transaction.branch)
                }
                db.transactionDao().insert(transaction.toEntity())
                addNotification(
                    userId = "admin",
                    role = "admin",
                    type = "success",
                    category = "orders",
                    title = "POS Sale ${transaction.id}",
                    message = "${formatPhp(transaction.total)} via ${transaction.paymentMethod} at ${transaction.branch}.",
                    entityId = transaction.id
                )
            }
        }
    }

    // ─── Deliveries ───
    suspend fun createDelivery(delivery: Delivery, deductUnitsByProductId: Map<String, Int> = emptyMap()): Result<Unit> {
        return runCatching {
            db.withTransaction {
                for ((productId, qtyNeeded) in deductUnitsByProductId) {
                    val prod = db.productDao().findById(productId)?.toModel()
                        ?: throw IllegalStateException("Product $productId not found.")
                    if (!prod.isSellable()) {
                        throw IllegalStateException("${prod.name} is expired or unverified and cannot be ordered.")
                    }
                    val available = prod.stockForBranch(delivery.branch)
                    if (qtyNeeded > available) {
                        throw IllegalStateException("Insufficient stock for ${prod.name} at ${delivery.branch} ($available left).")
                    }
                    val updated = prod.copy(
                        stockByBranch = prod.stockByBranch.toMutableMap().apply { put(delivery.branch, available - qtyNeeded) }
                    )
                    db.productDao().insert(updated.toEntity())
                    checkStockAlerts(updated, delivery.branch)
                }
                db.deliveryDao().insert(delivery.toEntity())
                addNotification(
                    userId = "admin",
                    role = "admin",
                    type = "info",
                    category = "orders",
                    title = "New Delivery Order ${delivery.id}",
                    message = "${delivery.customerName} (${delivery.barangay}) • ${formatPhp(delivery.totalAmount)} (${delivery.paymentMethod}).",
                    entityId = delivery.id
                )
                addNotification(
                    userId = delivery.customerName,
                    role = "customer",
                    type = "success",
                    category = "orders",
                    title = "Order Placed: ${delivery.id}",
                    message = "Your order of ${formatPhp(delivery.totalAmount)} is ${delivery.status}.",
                    entityId = delivery.id
                )
            }
        }
    }

    suspend fun updateDelivery(updated: Delivery) {
        val previous = db.deliveryDao().findById(updated.id)?.toModel()
        db.deliveryDao().update(updated.toEntity())

        if (previous != null && previous.driverName != updated.driverName && !updated.driverName.contains("Unassigned")) {
            addNotification(
                userId = updated.driverName,
                role = "rider",
                type = "info",
                category = "delivery",
                title = "Assigned Delivery ${updated.id}",
                message = "Deliver to ${updated.customerName}, ${updated.barangay} (${formatPhp(updated.totalAmount)}).",
                entityId = updated.id
            )
        }
        if (previous != null && previous.status != updated.status) {
            addNotification(
                userId = updated.customerName,
                role = "customer",
                type = if (updated.status == "Delivered") "success" else "info",
                category = "delivery",
                title = "Order ${updated.id}: ${updated.status}",
                message = when (updated.status) {
                    "Out for Delivery" -> "${updated.driverName} is on the way (ETA ~${updated.etaMinutes} mins)."
                    "Delivered" -> "Your order has been delivered and confirmed."
                    else -> "Status updated to ${updated.status}."
                },
                entityId = updated.id
            )
            if (updated.status == "Delivered") {
                addNotification(
                    userId = "admin",
                    role = "admin",
                    type = "success",
                    category = "delivery",
                    title = "Delivered: ${updated.id}",
                    message = "${updated.driverName} completed delivery to ${updated.customerName}.",
                    entityId = updated.id
                )
            }
        }
    }

    suspend fun verifyDeliveryPayment(deliveryId: String, approved: Boolean, reason: String? = null) {
        val current = db.deliveryDao().findById(deliveryId)?.toModel() ?: return
        val updated = if (approved) {
            current.copy(
                paymentStatus = "Paid",
                paymentAmount = current.totalAmount
            )
        } else {
            current.copy(
                paymentStatus = "Failed",
                notes = if (!reason.isNullOrBlank()) "${current.notes}\nPayment rejected: $reason".trim() else current.notes
            )
        }
        db.deliveryDao().update(updated.toEntity())
        addNotification(
            userId = current.customerName,
            role = "customer",
            type = if (approved) "success" else "danger",
            category = "payment",
            title = if (approved) "Payment Verified (${current.id})" else "Payment Failed (${current.id})",
            message = if (approved) "Your ${current.paymentMethod} payment is verified." else (reason ?: "Payment verification failed."),
            entityId = current.id
        )
    }

    // ─── Value-Added Services ───
    suspend fun addServiceTransaction(service: ServiceTransaction) {
        db.serviceTransactionDao().insert(
            ServiceTransactionEntity(
                id = service.id,
                branch = service.branch,
                type = service.type,
                provider = service.provider,
                recipient = service.recipient,
                senderOrAccountName = service.senderOrAccountName,
                amount = service.amount,
                serviceFee = service.serviceFee,
                totalAmount = service.totalAmount,
                status = service.status,
                timestamp = service.timestamp
            )
        )
        addNotification(
            userId = "admin",
            role = "admin",
            type = "success",
            category = "payment",
            title = "${service.type} Completed",
            message = "${service.provider} (${service.recipient}) • ${formatPhp(service.totalAmount)}.",
            entityId = service.id
        )
    }

    // ─── Supplier Deliveries ───
    suspend fun createSupplierDelivery(delivery: SupplierDelivery) {
        db.supplierDeliveryDao().insert(delivery.toEntity())
        addNotification(
            userId = "admin",
            role = "admin",
            type = "info",
            category = "inventory",
            title = "Incoming Supplier Delivery ${delivery.id}",
            message = "${delivery.supplierName} scheduled ${delivery.items.size} items to ${delivery.branch} (${formatPhp(delivery.totalCost)}).",
            entityId = delivery.id
        )
    }

    suspend fun receiveSupplierDelivery(deliveryId: String, receivedBy: String) {
        db.withTransaction {
            val entity = db.supplierDeliveryDao().findById(deliveryId) ?: return@withTransaction
            val delivery = entity.toModel()
            if (delivery.status == "Received") return@withTransaction

            for (item in delivery.items) {
                val prodEntity = db.productDao().findById(item.productId) ?: continue
                val prod = prodEntity.toModel()
                val currentQty = prod.stockForBranch(delivery.branch)
                val updatedMap = prod.stockByBranch.toMutableMap().apply { put(delivery.branch, currentQty + item.quantity) }
                db.productDao().insert(prod.copy(stockByBranch = updatedMap, cost = item.cost).toEntity())
            }
            val updatedDelivery = delivery.copy(status = "Received", receivedBy = receivedBy)
            db.supplierDeliveryDao().update(updatedDelivery.toEntity())
            addNotification(
                userId = delivery.supplierName,
                role = "supplier",
                type = "success",
                category = "inventory",
                title = "Delivery Received (${delivery.id})",
                message = "$receivedBy confirmed receipt at ${delivery.branch}.",
                entityId = delivery.id
            )
        }
    }

    // ─── Notifications ───
    suspend fun addNotification(
        userId: String,
        role: String,
        type: String,
        category: String,
        title: String,
        message: String,
        entityId: String? = null
    ) {
        db.notificationDao().insert(
            NotificationEntity(
                id = "NTF-${UUID.randomUUID().toString().take(8)}",
                userId = userId,
                role = role,
                type = type,
                category = category,
                title = title,
                message = message,
                entityId = entityId,
                isRead = false,
                createdAt = Instant.now().toString()
            )
        )
    }

    suspend fun markNotificationRead(id: String) = db.notificationDao().markRead(id)
    suspend fun markAllNotificationsRead(role: String) = db.notificationDao().markAllReadForRole(role)
    suspend fun deleteNotification(id: String) = db.notificationDao().deleteById(id)
    suspend fun clearNotifications(role: String) = db.notificationDao().clearForRole(role)

    suspend fun resetOperationalData() {
        db.withTransaction {
            db.transactionDao().deleteAll()
            db.deliveryDao().deleteAll()
            db.serviceTransactionDao().deleteAll()
            db.supplierDeliveryDao().deleteAll()
            db.productDao().deleteAll()
        }
    }

    // ─── Starter Catalog Helper (user-triggered in Inventory if store is empty) ───
    suspend fun loadSilangStarterCatalog() {
        val branchesList = db.branchDao().getAllOnce().map { it.name }.ifEmpty { listOf("Poblacion", "Bypass") }
        val expiryFresh = LocalDate.now().plusMonths(9).toString()
        fun mapStock(a: Int, b: Int): Map<String, Int> = buildMap {
            branchesList.forEachIndexed { idx, name ->
                put(name, if (idx == 0) a else b)
            }
        }
        val starter = listOf(
            Product(
                id = "PRD-1001",
                name = "Sinandomeng Rice (25kg/kg)",
                tagalogName = "Bigas Sinandomeng",
                category = "Rice & Grains",
                price = 54.0,
                cost = 46.0,
                batchNumber = "LOT-RC-2601",
                expiryDate = expiryFresh,
                supplierName = "SupplierSilang",
                authenticityStatus = "verified",
                stockByBranch = mapStock(120, 85),
                shelfLocation = "Aisle 1 - Grains",
                barcode = "480001001001",
                unit = "kg",
                lowStockThreshold = 25,
                overStockThreshold = 500,
                variants = listOf(
                    PackagingVariant("retail", "Daily Retail (1 kg)", 1, 54.0, 46.0, "kg"),
                    PackagingVariant("package", "Balot (5 kg)", 5, 260.0, 225.0, "pack"),
                    PackagingVariant("sack", "Sako (25 kg)", 25, 1250.0, 1100.0, "sack")
                )
            ),
            Product(
                id = "PRD-1002",
                name = "555 Sardines Tomato Sauce 155g",
                tagalogName = "Sardinas",
                category = "Canned Goods",
                price = 25.0,
                cost = 21.0,
                batchNumber = "LOT-CN-2604",
                expiryDate = expiryFresh,
                supplierName = "SupplierSilang",
                authenticityStatus = "verified",
                stockByBranch = mapStock(90, 48),
                shelfLocation = "Aisle 2 - Canned",
                barcode = "480001001002",
                unit = "can",
                lowStockThreshold = 20,
                overStockThreshold = 500,
                variants = listOf(
                    PackagingVariant("retail", "Tingi (1 can)", 1, 25.0, 21.0, "can"),
                    PackagingVariant("box", "Karton (24 cans)", 24, 576.0, 495.0, "box")
                )
            ),
            Product(
                id = "PRD-1003",
                name = "Lucky Me Pancit Canton Calamansi",
                tagalogName = "Pancit Canton",
                category = "Noodles & Instant",
                price = 16.0,
                cost = 13.0,
                batchNumber = "LOT-ND-2602",
                expiryDate = expiryFresh,
                supplierName = "SupplierSilang",
                authenticityStatus = "verified",
                stockByBranch = mapStock(140, 110),
                shelfLocation = "Aisle 2 - Noodles",
                barcode = "480001001003",
                unit = "pack",
                lowStockThreshold = 24,
                overStockThreshold = 500,
                variants = listOf(
                    PackagingVariant("retail", "Tingi (1 pack)", 1, 16.0, 13.0, "pack"),
                    PackagingVariant("package", "Balot (6 packs)", 6, 92.0, 76.0, "pack"),
                    PackagingVariant("box", "Karton (30 packs)", 30, 440.0, 380.0, "box")
                )
            ),
            Product(
                id = "PRD-1004",
                name = "Bear Brand Fortified Powdered Milk 320g",
                tagalogName = "Gatas Powder",
                category = "Milk & Dairy",
                price = 128.0,
                cost = 112.0,
                batchNumber = "LOT-MK-2603",
                expiryDate = expiryFresh,
                supplierName = "SupplierSilang",
                authenticityStatus = "verified",
                stockByBranch = mapStock(42, 18),
                shelfLocation = "Aisle 3 - Dairy",
                barcode = "480001001004",
                unit = "pack",
                lowStockThreshold = 15,
                overStockThreshold = 500
            ),
            Product(
                id = "PRD-1005",
                name = "C2 Apple Green Tea 500ml",
                tagalogName = "Inumin C2",
                category = "Beverages",
                price = 30.0,
                cost = 24.0,
                batchNumber = "LOT-BV-2605",
                expiryDate = expiryFresh,
                supplierName = "SupplierSilang",
                authenticityStatus = "verified",
                stockByBranch = mapStock(64, 52),
                shelfLocation = "Chiller 1",
                barcode = "480001001005",
                unit = "bottle",
                lowStockThreshold = 12,
                overStockThreshold = 500,
                variants = listOf(
                    PackagingVariant("retail", "Tingi (1 bottle)", 1, 30.0, 24.0, "bottle"),
                    PackagingVariant("box", "Case (24 bottles)", 24, 672.0, 560.0, "box")
                )
            ),
            Product(
                id = "PRD-1006",
                name = "Safeguard Pure White Soap 130g",
                tagalogName = "Sabon Panligo",
                category = "Toiletries",
                price = 48.0,
                cost = 39.0,
                batchNumber = "LOT-TL-2606",
                expiryDate = expiryFresh,
                supplierName = "SupplierSilang",
                authenticityStatus = "verified",
                stockByBranch = mapStock(55, 40),
                shelfLocation = "Aisle 4 - Bath",
                barcode = "480001001006",
                unit = "pc",
                lowStockThreshold = 10,
                overStockThreshold = 500
            )
        )
        db.productDao().insertAll(starter.map { it.toEntity() })
    }

    // ─── Security Hashing ───
    private fun hashSecret(raw: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(salt)
            update(raw.toByteArray(Charsets.UTF_8))
        }.digest()
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(salt)}:${enc.encodeToString(digest)}"
    }

    private fun verifySecret(raw: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return raw == stored
        val dec = Base64.getDecoder()
        val salt = runCatching { dec.decode(parts[0]) }.getOrNull() ?: return false
        val expected = runCatching { dec.decode(parts[1]) }.getOrNull() ?: return false
        val actual = MessageDigest.getInstance("SHA-256").apply {
            update(salt)
            update(raw.toByteArray(Charsets.UTF_8))
        }.digest()
        return MessageDigest.isEqual(expected, actual)
    }

    // ─── Mapping Extensions ───
    private fun ProductEntity.toModel(): Product = Product(
        id = id,
        name = name,
        tagalogName = tagalogName,
        category = category,
        price = price,
        cost = cost,
        batchNumber = batchNumber,
        expiryDate = expiryDate,
        supplierName = supplierName,
        authenticityStatus = authenticityStatus,
        stockByBranch = runCatching { json.decodeFromString<Map<String, Int>>(stockByBranchJson) }.getOrDefault(emptyMap()),
        shelfLocation = shelfLocation,
        barcode = barcode,
        unit = unit,
        lowStockThreshold = lowStockThreshold,
        overStockThreshold = overStockThreshold,
        images = runCatching { json.decodeFromString<List<String>>(imagesJson) }.getOrDefault(emptyList()),
        variants = runCatching { json.decodeFromString<List<PackagingVariant>>(variantsJson) }.getOrDefault(emptyList())
    )

    private fun Product.toEntity(): ProductEntity = ProductEntity(
        id = id,
        name = name,
        tagalogName = tagalogName,
        category = category,
        price = price,
        cost = cost,
        batchNumber = batchNumber,
        expiryDate = expiryDate,
        supplierName = supplierName,
        authenticityStatus = authenticityStatus,
        stockByBranchJson = json.encodeToString(stockByBranch),
        shelfLocation = shelfLocation,
        barcode = barcode,
        unit = unit,
        lowStockThreshold = lowStockThreshold,
        overStockThreshold = overStockThreshold,
        imagesJson = json.encodeToString(images),
        variantsJson = json.encodeToString(variants)
    )

    private fun TransactionEntity.toModel(): Transaction = Transaction(
        id = id,
        branch = branch,
        items = runCatching { json.decodeFromString<List<TransactionItem>>(itemsJson) }.getOrDefault(emptyList()),
        subtotal = subtotal,
        discountType = discountType,
        discountAmount = discountAmount,
        vatAmount = vatAmount,
        total = total,
        paymentMethod = paymentMethod,
        amountPaid = amountPaid,
        changeAmount = changeAmount,
        timestamp = timestamp,
        cashier = cashier
    )

    private fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
        id = id,
        branch = branch,
        itemsJson = json.encodeToString(items),
        subtotal = subtotal,
        discountType = discountType,
        discountAmount = discountAmount,
        vatAmount = vatAmount,
        total = total,
        paymentMethod = paymentMethod,
        amountPaid = amountPaid,
        changeAmount = changeAmount,
        timestamp = timestamp,
        cashier = cashier
    )

    private fun DeliveryEntity.toModel(): Delivery = Delivery(
        id = id,
        branch = branch,
        customerName = customerName,
        customerPhone = customerPhone,
        address = address,
        barangay = barangay,
        lat = lat,
        lng = lng,
        status = status,
        items = runCatching { json.decodeFromString<List<DeliveryItem>>(itemsJson) }.getOrDefault(emptyList()),
        deliveryFee = deliveryFee,
        totalAmount = totalAmount,
        driverName = driverName,
        driverLat = driverLat,
        driverLng = driverLng,
        driverLocationUpdatedAt = driverLocationUpdatedAt,
        driverLocationAccuracyMeters = driverLocationAccuracyMeters,
        etaMinutes = etaMinutes,
        notes = notes,
        timestamp = timestamp,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        paymentReference = paymentReference,
        paymentAmount = paymentAmount,
        paymentProofUri = paymentProofUri,
        podPhotoUri = podPhotoUri,
        podUploadedBy = podUploadedBy,
        podUploadedAt = podUploadedAt,
        deliveredAt = deliveredAt,
        handoverConfirmed = handoverConfirmed,
        archivedAt = archivedAt,
        deletedAt = deletedAt
    )

    private fun Delivery.toEntity(): DeliveryEntity = DeliveryEntity(
        id = id,
        branch = branch,
        customerName = customerName,
        customerPhone = customerPhone,
        address = address,
        barangay = barangay,
        lat = lat,
        lng = lng,
        status = status,
        itemsJson = json.encodeToString(items),
        deliveryFee = deliveryFee,
        totalAmount = totalAmount,
        driverName = driverName,
        driverLat = driverLat,
        driverLng = driverLng,
        driverLocationUpdatedAt = driverLocationUpdatedAt,
        driverLocationAccuracyMeters = driverLocationAccuracyMeters,
        etaMinutes = etaMinutes,
        notes = notes,
        timestamp = timestamp,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        paymentReference = paymentReference,
        paymentAmount = paymentAmount,
        paymentProofUri = paymentProofUri,
        podPhotoUri = podPhotoUri,
        podUploadedBy = podUploadedBy,
        podUploadedAt = podUploadedAt,
        deliveredAt = deliveredAt,
        handoverConfirmed = handoverConfirmed,
        archivedAt = archivedAt,
        deletedAt = deletedAt
    )

    private fun SupplierDeliveryEntity.toModel(): SupplierDelivery = SupplierDelivery(
        id = id,
        supplierName = supplierName,
        branch = branch,
        deliveryDate = deliveryDate,
        items = runCatching { json.decodeFromString<List<SupplierDeliveryItem>>(itemsJson) }.getOrDefault(emptyList()),
        totalCost = totalCost,
        status = status,
        receivedBy = receivedBy
    )

    private fun SupplierDelivery.toEntity(): SupplierDeliveryEntity = SupplierDeliveryEntity(
        id = id,
        supplierName = supplierName,
        branch = branch,
        deliveryDate = deliveryDate,
        itemsJson = json.encodeToString(items),
        totalCost = totalCost,
        status = status,
        receivedBy = receivedBy
    )

    companion object {
        const val KEY_CATEGORIES = "categories"
        const val KEY_STOCK_LIMIT = "default_stock_limit"
        val DEFAULT_CATEGORIES = listOf(
            "Rice & Grains",
            "Canned Goods",
            "Noodles & Instant",
            "Milk & Dairy",
            "Bread & Bakery",
            "Beverages",
            "Snacks",
            "Toiletries",
            "Household",
            "Other"
        )
    }
}
