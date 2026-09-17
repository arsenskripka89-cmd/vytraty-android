package ua.vytraty.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WalletType { CASH, CARD, BANK_ACCOUNT }
enum class TxKind { EXPENSE, INCOME, TRANSFER }
enum class TxSource { MANUAL, NOTIFICATION, BANK_API }
enum class Recurrence { NONE, WEEKLY, MONTHLY, YEARLY }

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: WalletType = WalletType.CARD,
    val currency: String = "UAH",
    val initialBalanceMinor: Long = 0,
    val color: Long = 0xFF3F51B5,
    val cardLast4: String? = null,
    val bankCode: String? = null,
    val monoAccountId: String? = null,
    val isDefault: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "category",
    val color: Long = 0xFF607D8B,
    val kind: TxKind = TxKind.EXPENSE,
    val sortOrder: Int = 0,
    val isSystem: Boolean = false,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("walletId"), Index("categoryId"), Index("timestamp"),
        Index(value = ["source", "externalId"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val walletId: Long,
    val categoryId: Long? = null,
    val kind: TxKind = TxKind.EXPENSE,
    val amountMinor: Long,
    val currency: String = "UAH",
    val timestamp: Long,
    val merchant: String? = null,
    val note: String? = null,
    val source: TxSource = TxSource.MANUAL,
    val cardLast4: String? = null,
    val transferToWalletId: Long? = null,
    /** Transfers between currencies: what actually arrived. null = the same amount as was sent. */
    val receivedMinor: Long? = null,
    val receivedCurrency: String? = null,
    /** Set on the expense that holds the bank fee of transfer [feeOfTransferId]. */
    val feeOfTransferId: Long? = null,
    val externalId: String? = null,
    val notificationLogId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "merchant_rules",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["merchantNormalized"], unique = true), Index("categoryId")],
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantNormalized: String,
    val merchantDisplay: String,
    val categoryId: Long,
    val hits: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "planned_payments",
    foreignKeys = [
        ForeignKey(entity = WalletEntity::class, parentColumns = ["id"], childColumns = ["walletId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("walletId"), Index("categoryId")],
)
data class PlannedPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amountMinor: Long,
    val currency: String = "UAH",
    val categoryId: Long? = null,
    val walletId: Long,
    val kind: TxKind = TxKind.EXPENSE,
    val nextDueAt: Long,
    val recurrence: Recurrence = Recurrence.MONTHLY,
    val remindDaysBefore: Int = 1,
    val active: Boolean = true,
    val note: String? = null,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** null = budget for all expenses */
    val categoryId: Long? = null,
    val limitMinor: Long,
    val currency: String = "UAH",
    val notified80: String? = null, // monthKey when 80% notification was sent
    val notified100: String? = null,
)

@Entity(tableName = "notification_log", indices = [Index("postedAt")])
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val parsed: Boolean,
    val transactionId: Long? = null,
    val reason: String? = null,
    /** one of [LogStatus]; null for rows captured before rules existed */
    val status: String? = null,
)

/** Transaction joined with its wallet and category (for lists). */
data class TransactionRow(
    val id: Long,
    val walletId: Long,
    val categoryId: Long?,
    val kind: TxKind,
    val amountMinor: Long,
    val currency: String,
    val timestamp: Long,
    val merchant: String?,
    val note: String?,
    val source: TxSource,
    val cardLast4: String?,
    val transferToWalletId: Long?,
    val receivedMinor: Long?,
    val receivedCurrency: String?,
    val walletName: String,
    val walletColor: Long,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Long?,
)

data class CategorySum(val categoryId: Long?, val total: Long)
data class CurrencySum(val currency: String, val expense: Long, val income: Long)
data class MonthSum(val monthKey: String, val expense: Long, val income: Long)
data class DaySum(val dayKey: String, val expense: Long, val income: Long)
data class WalletBalance(val walletId: Long, val delta: Long)

enum class RuleType { WALLET, CATEGORY }

/**
 * User rule that maps a notification to a wallet or a category: the notification must come from
 * [bank] (null = any app) and contain [pattern]. Several rules may point to the same target.
 */
@Entity(tableName = "capture_rules")
data class CaptureRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RuleType,
    /** walletId for [RuleType.WALLET], categoryId for [RuleType.CATEGORY] */
    val targetId: Long,
    /** [ua.vytraty.app.domain.parser.BankSource] name of the source app, null = any */
    val bank: String? = null,
    val pattern: String,
    val hits: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** How the capture rules classified one notification; stored in [NotificationLogEntity.status]. */
object LogStatus {
    /** wallet and category found by rules */
    const val RECORDED = "RECORDED"
    /** wallet found, category unknown */
    const val NO_CATEGORY = "NO_CATEGORY"
    /** payment, but no wallet rule matched */
    const val NO_WALLET = "NO_WALLET"
    /** not a payment: no amount in the text */
    const val NO_AMOUNT = "NO_AMOUNT"

    val all = listOf(RECORDED, NO_CATEGORY, NO_WALLET, NO_AMOUNT)

    fun label(status: String?) = when (status) {
        RECORDED -> "Пройшло правила"
        NO_CATEGORY -> "Картка є, без категорії"
        NO_WALLET -> "Не пройшло правило картки"
        NO_AMOUNT -> "Не платіж"
        else -> "Невідомо"
    }
}
