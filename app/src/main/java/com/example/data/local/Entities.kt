package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val isActive: Boolean = true
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val username: String,
    val passwordHash: String,
    val role: String,
    val status: String = "active"
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val pinHash: String,
    val barangay: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val storeImageUri: String? = null
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tagalogName: String,
    val category: String,
    val price: Double,
    val cost: Double,
    val batchNumber: String,
    val expiryDate: String,
    val supplierName: String,
    val authenticityStatus: String,
    val stockByBranchJson: String,
    val shelfLocation: String,
    val barcode: String,
    val unit: String,
    val lowStockThreshold: Int,
    val overStockThreshold: Int,
    val imagesJson: String,
    val variantsJson: String
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val branch: String,
    val itemsJson: String,
    val subtotal: Double,
    val discountType: String,
    val discountAmount: Double,
    val vatAmount: Double,
    val total: Double,
    val paymentMethod: String,
    val amountPaid: Double,
    val changeAmount: Double,
    val timestamp: String,
    val cashier: String
)

@Entity(tableName = "deliveries")
data class DeliveryEntity(
    @PrimaryKey val id: String,
    val branch: String,
    val customerName: String,
    val customerPhone: String,
    val address: String,
    val barangay: String,
    val lat: Double,
    val lng: Double,
    val status: String,
    val itemsJson: String,
    val deliveryFee: Double,
    val totalAmount: Double,
    val driverName: String,
    val driverLat: Double,
    val driverLng: Double,
    val driverLocationUpdatedAt: String?,
    val driverLocationAccuracyMeters: Double?,
    val etaMinutes: Int,
    val notes: String,
    val timestamp: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val paymentReference: String?,
    val paymentAmount: Double?,
    val paymentProofUri: String?,
    val podPhotoUri: String?,
    val podUploadedBy: String?,
    val podUploadedAt: String?,
    val deliveredAt: String?,
    val handoverConfirmed: Boolean,
    val archivedAt: String?,
    val deletedAt: String?
)

@Entity(tableName = "service_transactions")
data class ServiceTransactionEntity(
    @PrimaryKey val id: String,
    val branch: String,
    val type: String,
    val provider: String,
    val recipient: String,
    val senderOrAccountName: String,
    val amount: Double,
    val serviceFee: Double,
    val totalAmount: Double,
    val status: String,
    val timestamp: String
)

@Entity(tableName = "supplier_deliveries")
data class SupplierDeliveryEntity(
    @PrimaryKey val id: String,
    val supplierName: String,
    val branch: String,
    val deliveryDate: String,
    val itemsJson: String,
    val totalCost: Double,
    val status: String,
    val receivedBy: String?
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val role: String,
    val type: String,
    val category: String,
    val title: String,
    val message: String,
    val entityId: String?,
    val isRead: Boolean,
    val createdAt: String
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val settingKey: String,
    val settingValueJson: String
)
