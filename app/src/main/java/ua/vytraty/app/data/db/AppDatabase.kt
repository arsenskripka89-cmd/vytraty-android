package ua.vytraty.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WalletEntity::class, CategoryEntity::class, TransactionEntity::class, MerchantRuleEntity::class,
        PlannedPaymentEntity::class, BudgetEntity::class, NotificationLogEntity::class, CaptureRuleEntity::class,
    ],
    version = 3,
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
    abstract fun captureRuleDao(): CaptureRuleDao

    companion object {
        /** Capture rules + notification classes. Keeps all user data — never fall back to a destructive migration. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `capture_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `targetId` INTEGER NOT NULL, `bank` TEXT, `pattern` TEXT NOT NULL, " +
                        "`hits` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("ALTER TABLE `notification_log` ADD COLUMN `status` TEXT")
                db.execSQL(
                    "UPDATE notification_log SET status = CASE WHEN parsed = 0 THEN 'NO_AMOUNT' " +
                        "WHEN EXISTS (SELECT 1 FROM transactions t WHERE t.id = transactionId AND t.categoryId IS NOT NULL) " +
                        "THEN 'RECORDED' ELSE 'NO_CATEGORY' END",
                )
            }
        }

        /** Transfers between currencies: how much arrived, next to how much was sent. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receivedMinor` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `receivedCurrency` TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vytraty.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
