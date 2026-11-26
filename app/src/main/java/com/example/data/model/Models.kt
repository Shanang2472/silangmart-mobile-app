package com.example.data.model

import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
enum class UserRole(val key: String, val label: String) {
    ADMIN("admin", "Admin / Staff"),
    CASHIER("cashier", "Cashier"),
    CUSTOMER("customer", "Customer"),
    RIDER("rider", "Rider"),
    SUPPLIER("supplier", "Supplier");

    companion object {
        fun fromKey(key: String): UserRole = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CUSTOMER
    }
}

@Serializable
data class LoggedInUser(
    val username: String,
    val role: String, // "admin", "customer", "rider", "supplier"
    val name: String
)

@Serializable
data class BranchRecord(
    val id: Long = 0,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val isActive: Boolean = true
)

@Serializable
data class UserRecord(
    val id: Long = 0,
    val name: String,
    val username: String,
    val passwordHash: String,
    val role: String, // "admin", "cashier", "rider", "supplier"
    val status: String = "active"
)

@Serializable
data class CustomerRecord(
    val id: Long = 0,
    val name: String,
    val phone: String,
    val pinHash: String,
    val barangay: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val storeImageUri: String? = null
)

@Serializable
data class PackagingVariant(
    val type: String = "retail", // retail, wholesale, box, package, sack
    val label: String = "",
    val qtyPerUnit: Int = 1,
    val price: Double = 0.0,
    val cost: Double = 0.0,
    val unitName: String = "pc",
    val image: String? = null
)

enum class ExpiryStatus {
    EXPIRED, EXPIRING, FRESH, UNKNOWN
}

@Serializable
data class Product(
    val id: String,
    val name: String,
    val tagalogName: String = "",
    val category: String,
    val price: Double,
    val cost: Double,
    val batchNumber: String = "",
    val expiryDate: String = "",
    val supplierName: String = "",
    val authenticityStatus: String = "unverified", // unverified, verified, rejected
    val stockByBranch: Map<String, Int> = emptyMap(),
    val shelfLocation: String = "",
    val barcode: String = "",
    val unit: String = "pc", // kg, can, pack, bottle, pc, bag
    val lowStockThreshold: Int = 10,
    val overStockThreshold: Int = 500,
    val images: List<String> = emptyList(),
    val variants: List<PackagingVariant> = emptyList()
) {
    fun stockForBranch(branchName: String): Int {
        return stockByBranch[branchName] ?: 0
    }

    fun effectiveVariants(): List<PackagingVariant> {
        if (variants.isNotEmpty()) return variants
        return listOf(
            PackagingVariant(
                type = "retail",
                label = "Daily Retail (1 $unit)",
                qtyPerUnit = 1,
                price = price,
                cost = cost,
                unitName = unit,
                image = images.firstOrNull()
            )
        )
    }

    fun expiryStatus(): ExpiryStatus {
        if (expiryDate.isBlank()) return ExpiryStatus.UNKNOWN
        return runCatching {
            val target = LocalDate.parse(expiryDate.take(10))
            val days = ChronoUnit.DAYS.between(LocalDate.now(), target)
            when {
                days < 0 -> ExpiryStatus.EXPIRED
                days <= 30 -> ExpiryStatus.EXPIRING
                else -> ExpiryStatus.FRESH
            }
        }.getOrDefault(ExpiryStatus.UNKNOWN)
    }

    fun isSellable(): Boolean {
        val exp = expiryStatus()
        return exp != ExpiryStatus.EXPIRED &&
            exp != ExpiryStatus.UNKNOWN &&
            authenticityStatus == "verified" &&
            supplierName.isNotBlank() &&
            batchNumber.isNotBlank()
    }
}

data class CartItem(
    val product: Product,
    val quantity: Int,
    val selectedVariant: PackagingVariant
) {
    val lineTotal: Double get() = selectedVariant.price * quantity
    val totalBaseUnits: Int get() = quantity * selectedVariant.qtyPerUnit.coerceAtLeast(1)
}

@Serializable
data class TransactionItem(
    val productId: String,
    val name: String,
    val price: Double,
    val quantity: Int,
    val unit: String
)

@Serializable
data class Transaction(
    val id: String,
    val branch: String,
    val items: List<TransactionItem>,
    val subtotal: Double,
    val discountType: String = "none", // none, senior, pwd, percentage
    val discountAmount: Double = 0.0,
    val vatAmount: Double = 0.0,
    val total: Double,
    val paymentMethod: String = "Cash", // Cash, GCash, Maya
    val amountPaid: Double,
    val changeAmount: Double,
    val timestamp: String,
    val cashier: String
)

@Serializable
data class DeliveryItem(
    val productId: String,
    val name: String,
    val quantity: Int,
    val price: Double,
    val batchNumber: String = "",
    val expiryDate: String = "",
    val supplierName: String = "",
    val authenticityStatus: String = "verified"
)

@Serializable
data class Delivery(
    val id: String,
    val branch: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val barangay: String,
    val lat: Double,
    val lng: Double,
    val status: String = "Pending", // Pending, Preparing, Out for Delivery, Delivered, Cancelled
    val items: List<DeliveryItem>,
    val deliveryFee: Double,
    val totalAmount: Double,
    val driverName: String = "Unassigned",
    val driverLat: Double,
    val driverLng: Double,
    val driverLocationUpdatedAt: String? = null,
    val driverLocationAccuracyMeters: Double? = null,
    val etaMinutes: Int = 15,
    val notes: String = "",
    val timestamp: String,
    val paymentMethod: String = "Cash", // Cash, GCash, Maya
    val paymentStatus: String = "Pending", // Pending, Awaiting Verification, Paid, Failed, Refunded
    val paymentReference: String? = null,
    val paymentAmount: Double? = null,
    val paymentProofUri: String? = null,
    val podPhotoUri: String? = null,
    val podUploadedBy: String? = null,
    val podUploadedAt: String? = null,
    val deliveredAt: String? = null,
    val handoverConfirmed: Boolean = false,
    val archivedAt: String? = null,
    val deletedAt: String? = null
)

@Serializable
data class ServiceTransaction(
    val id: String,
    val branch: String,
    val type: String, // E-Load, Bills Payment, Remittance
    val provider: String,
    val recipient: String,
    val senderOrAccountName: String,
    val amount: Double,
    val serviceFee: Double,
    val totalAmount: Double,
    val status: String = "Completed",
    val timestamp: String
)

@Serializable
data class SupplierDeliveryItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val cost: Double
)

@Serializable
data class SupplierDelivery(
    val id: String,
    val supplierName: String,
    val branch: String,
    val deliveryDate: String,
    val items: List<SupplierDeliveryItem>,
    val totalCost: Double,
    val status: String = "Pending", // Pending, Received
    val receivedBy: String? = null
)

@Serializable
data class AppNotification(
    val id: String,
    val userId: String,
    val role: String, // admin, customer, rider, supplier
    val type: String = "info", // info, warning, success, danger
    val category: String = "system", // orders, delivery, payment, inventory, account, system
    val title: String,
    val message: String,
    val entityId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String
)

data class CartAction(
    val productId: String,
    val quantity: Int,
    val productName: String,
    val unitName: String
)

data class AiMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val cartActions: List<CartAction> = emptyList(),
    val cartActionDone: Boolean = false
)

fun categoryFallbackEmoji(category: String): String = when (category) {
    "Rice & Grains" -> "🌾"
    "Canned Goods" -> "🥫"
    "Noodles & Instant" -> "🍜"
    "Milk & Dairy" -> "🥛"
    "Bread & Bakery" -> "🍞"
    "Beverages" -> "🥤"
    "Snacks" -> "🍪"
    "Toiletries" -> "🧼"
    "Household" -> "🧹"
    else -> "🛒"
}

fun formatPhp(amount: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "₱${format.format(amount)}"
}

fun haversineDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLng / 2) * sin(dLng / 2)
    val bounded = min(1.0, max(0.0, a))
    return 6371.0 * 2.0 * atan2(sqrt(bounded), sqrt(1.0 - bounded))
}
