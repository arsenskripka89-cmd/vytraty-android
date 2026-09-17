package ua.vytraty.app.domain.usecase

import android.content.Context
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.data.prefs.SettingsRepository
import ua.vytraty.app.domain.Money
import ua.vytraty.app.notifications.AppNotifications
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.TimeUnit

/**
 * Moving money between two own cards produces two notifications — a debit from one card and a credit to
 * the other, in different currencies when the bank converts. This joins them into a single transfer that
 * keeps both amounts, so the rate the bank actually used (and any fee baked into it) stays visible.
 */
class MergeTransfersUseCase(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    /** Called right after a notification-captured transaction was written. Returns the transfer id, if merged. */
    suspend fun tryMerge(transactionId: Long): Long? {
        if (!settings.current().autoMergeTransfers) return null
        val tx = db.transactionDao().byId(transactionId) ?: return null
        if (!isMergeable(tx)) return null
        val other = db.transactionDao().counterpart(
            id = tx.id,
            kind = if (tx.kind == TxKind.EXPENSE) TxKind.INCOME else TxKind.EXPENSE,
            walletId = tx.walletId,
            from = tx.timestamp - WINDOW_MS,
            to = tx.timestamp + WINDOW_MS,
            around = tx.timestamp,
        ) ?: return null
        if (!isMergeable(other)) return null

        val sent = if (tx.kind == TxKind.EXPENSE) tx else other
        val received = if (tx.kind == TxKind.EXPENSE) other else tx
        val fee = feeFor(sent.amountMinor, received.amountMinor, sent.currency.equals(received.currency, true))
        val transfer = sent.copy(
            kind = TxKind.TRANSFER,
            categoryId = null,
            merchant = null,
            // What the fee takes is an expense of its own, so the transfer itself moves what arrived.
            amountMinor = if (fee != null) received.amountMinor else sent.amountMinor,
            transferToWalletId = received.walletId,
            receivedMinor = received.amountMinor,
            receivedCurrency = received.currency,
            note = listOfNotNull(sent.note?.ifBlank { null }, TRANSFER_NOTE).joinToString(" · "),
        )
        db.transactionDao().update(transfer)
        setFee(transfer.id, fee)
        // The credit notification now belongs to the transfer, not to a separate income.
        db.notificationLogDao().byTransactionId(received.id).forEach { log ->
            db.notificationLogDao().update(log.copy(transactionId = transfer.id))
        }
        db.transactionDao().deleteById(received.id)
        notifyMerged(transfer, fee)
        return transfer.id
    }

    /**
     * The bank fee of a transfer, kept as one expense of its own so it lands in the category totals,
     * the budget and the month like any other spending. Passing null removes it.
     */
    suspend fun setFee(transferId: Long, feeMinor: Long?) {
        val transfer = db.transactionDao().byId(transferId) ?: return
        val existing = db.transactionDao().feeOf(transferId)
        if (feeMinor == null || feeMinor <= 0) {
            existing?.let { db.transactionDao().deleteById(it.id) }
            return
        }
        val category = db.categoryDao().byName(FEE_CATEGORY, TxKind.EXPENSE)
        val fee = TransactionEntity(
            id = existing?.id ?: 0,
            walletId = transfer.walletId,
            categoryId = category?.id,
            kind = TxKind.EXPENSE,
            amountMinor = feeMinor,
            currency = transfer.currency,
            timestamp = transfer.timestamp,
            note = "Комісія за переказ",
            source = transfer.source,
            feeOfTransferId = transferId,
        )
        if (existing == null) db.transactionDao().insert(fee) else db.transactionDao().update(fee)
    }

    /** Undo: the transfer goes back to being an expense, and the credited amount becomes an income again. */
    suspend fun split(transferId: Long) {
        val tx = db.transactionDao().byId(transferId) ?: return
        val toWalletId = tx.transferToWalletId
        if (tx.kind != TxKind.TRANSFER || toWalletId == null) return
        val fee = db.transactionDao().feeOf(transferId)?.amountMinor ?: 0L
        setFee(transferId, null)
        db.transactionDao().update(
            tx.copy(
                kind = TxKind.EXPENSE,
                amountMinor = tx.amountMinor + fee,
                transferToWalletId = null,
                receivedMinor = null,
                receivedCurrency = null,
                note = tx.note?.replace(TRANSFER_NOTE, "")?.trim(' ', '·')?.ifBlank { null },
            ),
        )
        val incomeId = db.transactionDao().insert(
            TransactionEntity(
                walletId = toWalletId,
                kind = TxKind.INCOME,
                amountMinor = tx.receivedMinor ?: tx.amountMinor,
                currency = tx.receivedCurrency ?: tx.currency,
                timestamp = tx.timestamp,
                source = TxSource.NOTIFICATION,
            ),
        )
        db.notificationLogDao().byTransactionId(transferId)
            .filter { it.id != tx.notificationLogId }
            .forEach { db.notificationLogDao().update(it.copy(transactionId = incomeId)) }
        AppNotifications.cancelCaptured(context, transferId)
    }

    private fun notifyMerged(transfer: TransactionEntity, feeMinor: Long?) {
        val sent = Money.format((transfer.amountMinor + (feeMinor ?: 0)), transfer.currency)
        val got = Money.format(transfer.receivedMinor ?: transfer.amountMinor, transfer.receivedCurrency ?: transfer.currency)
        AppNotifications.showTransferMerged(
            context, transfer.id,
            "Переказ $sent → $got",
            listOfNotNull(
                "Два сповіщення об'єднано в переказ між вашими картками",
                rateLine(transfer),
                feeMinor?.let { "Комісія ${Money.format(it, transfer.currency)} записана у витрати" },
            ).joinToString(". "),
        )
    }

    companion object {
        const val TRANSFER_NOTE = "Переказ між своїми картками"
        const val FEE_CATEGORY = "Комісії та податки"

        /**
         * What the bank kept for itself. Only meaningful inside one currency — when the transfer also
         * converts, the difference is the exchange rate, not a fee, so it stays with the transfer.
         */
        fun feeFor(sentMinor: Long, receivedMinor: Long, sameCurrency: Boolean): Long? =
            (sentMinor - receivedMinor).takeIf { sameCurrency && it > 0 }

        /** Both notifications of one card-to-card transfer arrive within seconds; a quarter of an hour is generous. */
        val WINDOW_MS: Long = TimeUnit.MINUTES.toMillis(15)

        /** Only a freshly captured payment without a category can be half of a transfer. */
        fun isMergeable(tx: TransactionEntity) = tx.source == TxSource.NOTIFICATION &&
            tx.categoryId == null &&
            tx.amountMinor > 0 &&
            (tx.kind == TxKind.EXPENSE || tx.kind == TxKind.INCOME)

        /** "Курс: 1 € = 51,8395 ₴" for a transfer that changed currency; null when it did not. */
        fun rateLine(tx: TransactionEntity): String? {
            val received = tx.receivedMinor ?: return null
            val receivedCurrency = tx.receivedCurrency ?: return null
            if (received <= 0 || tx.amountMinor <= 0 || receivedCurrency.equals(tx.currency, true)) return null
            val rate = BigDecimal(tx.amountMinor).divide(BigDecimal(received), 4, RoundingMode.HALF_UP)
            return "Курс: 1 ${Money.symbol(receivedCurrency)} = ${rate.toPlainString().replace('.', ',')} ${Money.symbol(tx.currency)}"
        }
    }
}
