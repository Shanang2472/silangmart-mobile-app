package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY id ASC")
    fun observeAll(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches ORDER BY id ASC")
    suspend fun getAllOnce(): List<BranchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(branch: BranchEntity): Long

    @Update
    suspend fun update(branch: BranchEntity)

    @Query("DELETE FROM branches WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByUsername(username: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity): Long

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY id DESC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByName(name: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: CustomerEntity): Long

    @Update
    suspend fun update(customer: CustomerEntity)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllOnce(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM deliveries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DeliveryEntity>>

    @Query("SELECT * FROM deliveries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DeliveryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(delivery: DeliveryEntity)

    @Update
    suspend fun update(delivery: DeliveryEntity)

    @Query("DELETE FROM deliveries")
    suspend fun deleteAll()
}

@Dao
interface ServiceTransactionDao {
    @Query("SELECT * FROM service_transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ServiceTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(service: ServiceTransactionEntity)

    @Query("DELETE FROM service_transactions")
    suspend fun deleteAll()
}

@Dao
interface SupplierDeliveryDao {
    @Query("SELECT * FROM supplier_deliveries ORDER BY deliveryDate DESC, id DESC")
    fun observeAll(): Flow<List<SupplierDeliveryEntity>>

    @Query("SELECT * FROM supplier_deliveries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SupplierDeliveryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(delivery: SupplierDeliveryEntity)

    @Update
    suspend fun update(delivery: SupplierDeliveryEntity)

    @Query("DELETE FROM supplier_deliveries")
    suspend fun deleteAll()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE role = :role")
    suspend fun markAllReadForRole(role: String)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM notifications WHERE role = :role")
    suspend fun clearForRole(role: String)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE settingKey = :key LIMIT 1")
    suspend fun get(key: String): SettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: SettingEntity)
}
