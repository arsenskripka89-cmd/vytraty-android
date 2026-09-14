package ua.vytraty.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WalletEntity::class, CategoryEntity::class, TransactionEntity::class, MerchantRuleEntity::class,
        PlannedPaymentEntity::class, BudgetEntity::class, NotificationLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun plannedPaymentDao(): PlannedPaymentDao
    abstract fun budgetDao(): BudgetDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vytraty.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
