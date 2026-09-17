package ua.vytraty.app.domain.usecase

import android.content.Context
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.LogStatus
import ua.vytraty.app.data.db.NotificationLogEntity
import ua.vytraty.app.data.db.RuleType
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.data.prefs.SettingsRepository
import ua.vytraty.app.domain.MerchantNormalizer
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.parser.ParsedPayment
import ua.vytraty.app.domain.parser.ParserRegistry
import ua.vytraty.app.domain.parser.RuleMatcher
import ua.vytraty.app.notifications.AppNotifications
import java.util.concurrent.TimeUnit

/**
 * Entry point for a bank / Google Pay notification: parse it, find the wallet and the category by the
 * user's capture rules, insert the transaction and file the notification in the store with the class
 * the rules gave it.
 */
class RecordParsedPaymentUseCase(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val registry: ParserRegistry,
    private val budgetChecker: BudgetChecker,
    private val mergeTransfers: MergeTransfersUseCase,
) {
    sealed class Result {
        data class Recorded(val transactionId: Long, val categoryAssigned: Boolean) : Result()
        data object Duplicate : Result()
        data class NotParsed(val logId: Long) : Result()
        /** Payment, but no wallet rule matched it: logged for the store, nothing written. */
        data class NoWalletRule(val logId: Long) : Result()
        data object Disabled : Result()
    }

    /** What the rules made of one notification. */
    data class Resolution(val wallet: WalletEntity?, val categoryId: Long?, val byWalletRule: Boolean)

    suspend fun handle(packageName: String, title: String?, text: String?, postedAt: Long): Result {
        val s = settings.current()
        if (!s.captureEnabled) return Result.Disabled
        if (s.enabledPackages.isNotEmpty() && packageName !in s.enabledPackages) return Result.Disabled

        val logDao = db.notificationLogDao()
        if (logDao.findDuplicate(packageName, text, postedAt - DUPLICATE_WINDOW_MS) != null) return Result.Duplicate
        logDao.deleteOlderThan(postedAt - RETENTION_MS)

        val parsed = registry.parse(packageName, title, text)
        if (parsed == null) {
            // Google Play services posts many unrelated notifications; only log the ones that look like payments.
            if (packageName == "com.google.android.gms") return Result.Disabled
            val id = logDao.insert(
                NotificationLogEntity(
                    packageName = packageName, title = title, text = text, postedAt = postedAt,
                    parsed = false, reason = "Не знайдено суму", status = LogStatus.NO_AMOUNT,
                ),
            )
            return Result.NotParsed(id)
        }

        val res = resolve(packageName, title, text, parsed)
        val wallet = res.wallet ?: if (s.onlyMatchedWallet) null else (db.walletDao().defaultWallet() ?: db.walletDao().firstActive())
        val status = when {
            !res.byWalletRule -> LogStatus.NO_WALLET
            res.categoryId == null -> LogStatus.NO_CATEGORY
            else -> LogStatus.RECORDED
        }
        val log = NotificationLogEntity(
            packageName = packageName, title = title, text = text, postedAt = postedAt,
            parsed = true, status = status,
            reason = if (res.byWalletRule) null else "Жодне правило картки не підійшло",
        )
        val logId = logDao.insert(log)
        if (wallet == null) return Result.NoWalletRule(logId)

        val txId = insert(parsed, wallet, res.categoryId, postedAt, logId)
        logDao.update(log.copy(id = logId, transactionId = txId))
        // A debit and a credit of two own cards within minutes are one transfer, not two operations.
        if (mergeTransfers.tryMerge(txId) != null) return Result.Recorded(txId, categoryAssigned = true)
        notify(txId, wallet, parsed, res.categoryId != null)
        if (parsed.kind == TxKind.EXPENSE) budgetChecker.checkAfterExpense(res.categoryId)
        return Result.Recorded(txId, res.categoryId != null)
    }

    /**
     * Wallet: a capture rule first, then the last 4 digits of a card, then the default wallet.
     * Category: a capture rule first, then the learned merchant → category rule.
     */
    suspend fun resolve(packageName: String, title: String?, text: String?, parsed: ParsedPayment): Resolution {
        val rules = db.captureRuleDao().all()
        val walletRule = RuleMatcher.best(rules, RuleType.WALLET, packageName, title, text)
        var wallet = walletRule?.let { db.walletDao().byId(it.targetId) }?.takeIf { !it.archived }
        if (wallet != null) db.captureRuleDao().bumpHits(walletRule!!.id)
        var byRule = wallet != null
        if (wallet == null) {
            wallet = parsed.cardLast4?.let { db.walletDao().byCardLast4(it) }
            byRule = wallet != null
        }

        val categoryRule = RuleMatcher.best(rules, RuleType.CATEGORY, packageName, title, text)
        var categoryId = categoryRule
            ?.let { db.categoryDao().byId(it.targetId) }
            ?.takeIf { it.kind == parsed.kind }
            ?.id
        if (categoryId != null) db.captureRuleDao().bumpHits(categoryRule!!.id)
        if (categoryId == null) {
            val normalized = MerchantNormalizer.normalize(parsed.merchant)
            val merchantRule = if (normalized.isNotEmpty()) db.merchantRuleDao().byMerchant(normalized) else null
            categoryId = merchantRule?.categoryId?.let { id -> db.categoryDao().byId(id)?.takeIf { it.kind == parsed.kind }?.id }
            if (merchantRule != null && categoryId != null) {
                db.merchantRuleDao().upsert(merchantRule.copy(hits = merchantRule.hits + 1, updatedAt = System.currentTimeMillis()))
            }
        }
        return Resolution(wallet, categoryId, byRule)
    }

    /** Inserts a transaction from an already parsed payment. Used by the parser tester. */
    suspend fun record(parsed: ParsedPayment, occurredAt: Long, logId: Long?): Long {
        val res = resolve("", null, parsed.rawText, parsed)
        val wallet = res.wallet ?: db.walletDao().defaultWallet() ?: db.walletDao().firstActive() ?: error("No wallet")
        return insert(parsed, wallet, res.categoryId, occurredAt, logId)
    }

    private suspend fun insert(parsed: ParsedPayment, wallet: WalletEntity, categoryId: Long?, occurredAt: Long, logId: Long?): Long {
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

    /**
     * Re-runs the rules over a stored notification after the user changed them: records it when it is
     * still missing, otherwise fixes the wallet and the category of the transaction it created.
     */
    suspend fun reprocess(logId: Long): Boolean {
        val log = db.notificationLogDao().byId(logId) ?: return false
        val parsed = registry.parse(log.packageName, log.title, log.text) ?: return false
        val res = resolve(log.packageName, log.title, log.text, parsed)
        val wallet = res.wallet ?: return false
        val existing = log.transactionId?.let { db.transactionDao().byId(it) }
        val status = if (res.categoryId == null) LogStatus.NO_CATEGORY else LogStatus.RECORDED
        if (existing == null) {
            val txId = insert(parsed, wallet, res.categoryId, log.postedAt, logId)
            db.notificationLogDao().update(log.copy(transactionId = txId, parsed = true, status = status, reason = null))
        } else {
            db.transactionDao().update(
                existing.copy(walletId = wallet.id, categoryId = res.categoryId ?: existing.categoryId),
            )
            db.notificationLogDao().update(log.copy(status = status, reason = null))
        }
        return true
    }

    /** Applies rules to every stored notification that no rule had covered; returns how many changed. */
    suspend fun reprocessPending(): Int {
        val since = System.currentTimeMillis() - RETENTION_MS
        val pending = db.notificationLogDao().byStatuses(listOf(LogStatus.NO_WALLET, LogStatus.NO_CATEGORY), since)
        return pending.count { reprocess(it.id) }
    }

    private suspend fun notify(txId: Long, wallet: WalletEntity, parsed: ParsedPayment, categoryAssigned: Boolean) {
        val s = settings.current()
        if (categoryAssigned || !s.notifyUncategorized) return
        val amount = Money.format(parsed.amountMinor, parsed.currency)
        val where = parsed.merchant?.let { " · $it" } ?: ""
        val title = if (parsed.kind == TxKind.INCOME) "Дохід $amount$where" else "Витрата $amount$where"
        val suggestions = db.categoryDao().popularForWallet(wallet.id, parsed.kind, 2)
        AppNotifications.showCapturedPayment(
            context, txId, title,
            "${wallet.name} · оберіть категорію",
            suggestions = suggestions,
        )
    }

    companion object {
        const val DUPLICATE_WINDOW_MS = 90_000L
        val RETENTION_MS: Long = TimeUnit.DAYS.toMillis(60)
    }
}
