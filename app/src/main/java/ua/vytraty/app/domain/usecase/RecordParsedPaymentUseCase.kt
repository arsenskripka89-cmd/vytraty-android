package ua.vytraty.app.domain.usecase

import android.content.Context
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.NotificationLogEntity
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.data.prefs.SettingsRepository
import ua.vytraty.app.domain.MerchantNormalizer
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.parser.ParsedPayment
import ua.vytraty.app.domain.parser.ParserRegistry
import ua.vytraty.app.notifications.AppNotifications

/**
 * Entry point for a bank / Google Pay notification: parse it, find wallet by card, apply the learned
 * merchant→category rule, insert the transaction and log the notification.
 */
class RecordParsedPaymentUseCase(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val registry: ParserRegistry,
    private val budgetChecker: BudgetChecker,
) {
    sealed class Result {
        data class Recorded(val transactionId: Long, val categoryAssigned: Boolean) : Result()
        data object Duplicate : Result()
        data class NotParsed(val logId: Long) : Result()
        data object Disabled : Result()
    }

    suspend fun handle(packageName: String, title: String?, text: String?, postedAt: Long): Result {
        val s = settings.current()
        if (!s.captureEnabled) return Result.Disabled
        if (s.enabledPackages.isNotEmpty() && packageName !in s.enabledPackages) return Result.Disabled

        val logDao = db.notificationLogDao()
        if (logDao.findDuplicate(packageName, text, postedAt - DUPLICATE_WINDOW_MS) != null) return Result.Duplicate

        val parsed = registry.parse(packageName, title, text)
        if (parsed == null) {
            // Google Play services posts many unrelated notifications; only log the ones that look like payments.
            if (packageName == "com.google.android.gms") return Result.Disabled
            val id = logDao.insert(
                NotificationLogEntity(packageName = packageName, title = title, text = text, postedAt = postedAt, parsed = false, reason = "Не знайдено суму"),
            )
            return Result.NotParsed(id)
        }
        val logEntry = NotificationLogEntity(packageName = packageName, title = title, text = text, postedAt = postedAt, parsed = true)
        val logId = logDao.insert(logEntry)
        val txId = record(parsed, postedAt, logId)
        logDao.update(logEntry.copy(id = logId, transactionId = txId))
        val tx = db.transactionDao().byId(txId)
        val categoryAssigned = tx?.categoryId != null
        notify(txId, parsed, categoryAssigned)
        if (parsed.kind == TxKind.EXPENSE) budgetChecker.checkAfterExpense(tx?.categoryId)
        return Result.Recorded(txId, categoryAssigned)
    }

    /** Inserts a transaction from an already parsed payment. Used by the listener and the parser tester. */
    suspend fun record(parsed: ParsedPayment, occurredAt: Long, logId: Long?): Long {
        val walletDao = db.walletDao()
        val wallet = parsed.cardLast4?.let { walletDao.byCardLast4(it) }
            ?: walletDao.defaultWallet()
            ?: walletDao.firstActive()
            ?: error("No wallet")
        val normalized = MerchantNormalizer.normalize(parsed.merchant)
        val rule = if (normalized.isNotEmpty()) db.merchantRuleDao().byMerchant(normalized) else null
        val categoryId = rule?.categoryId?.let { id ->
            db.categoryDao().byId(id)?.takeIf { it.kind == parsed.kind }?.id
        }
        if (rule != null && categoryId != null) {
            db.merchantRuleDao().upsert(rule.copy(hits = rule.hits + 1, updatedAt = System.currentTimeMillis()))
        }
        val tx = TransactionEntity(
            walletId = wallet.id,
            categoryId = categoryId,
            kind = parsed.kind,
            amountMinor = parsed.amountMinor,
            currency = parsed.currency,
            timestamp = occurredAt,
            merchant = MerchantNormalizer.display(parsed.merchant).ifBlank { null },
            source = TxSource.NOTIFICATION,
            cardLast4 = parsed.cardLast4,
            notificationLogId = logId,
        )
        return db.transactionDao().insert(tx)
    }

    private suspend fun notify(txId: Long, parsed: ParsedPayment, categoryAssigned: Boolean) {
        val s = settings.current()
        if (categoryAssigned || !s.notifyUncategorized) return
        val amount = Money.format(parsed.amountMinor, parsed.currency)
        val where = parsed.merchant?.let { " у $it" } ?: ""
        val title = if (parsed.kind == TxKind.INCOME) "Дохід $amount" else "Витрата $amount$where"
        val card = parsed.cardLast4?.let { " з картки •••• $it" } ?: ""
        AppNotifications.showCapturedPayment(
            context, txId, title,
            "Записано зі сповіщення ${parsed.bank.displayName}$card. Торкніться, щоб обрати категорію.",
            needsCategory = true,
        )
    }

    companion object {
        const val DUPLICATE_WINDOW_MS = 90_000L
    }
}
