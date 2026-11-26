package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BranchEntity::class,
        UserEntity::class,
        CustomerEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        DeliveryEntity::class,
        ServiceTransactionEntity::class,
        SupplierDeliveryEntity::class,
        NotificationEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun branchDao(): BranchDao
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun serviceTransactionDao(): ServiceTransactionDao
    abstract fun supplierDeliveryDao(): SupplierDeliveryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "silang_mini_mart.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
