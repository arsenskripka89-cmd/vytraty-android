package ua.vytraty.app.domain

import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.domain.parser.BankSource
import java.util.concurrent.TimeUnit

/**
 * One card payment is often announced twice: by the bank app and by Google Wallet, seconds apart and with
 * the same amount. The second notification must not become a second expense — it joins the one already
 * written and fills in what that one lacked (the shop, the card, the wallet a rule points to).
 */
object DuplicatePayments {
    /** Both notifications of one payment arrive within seconds; a few minutes covers a slow bank push. */
    val WINDOW_MS: Long = TimeUnit.MINUTES.toMillis(5)

    /** A freshly parsed notification, as far as grouping is concerned. */
    data class Capture(
        val packageName: String,
        val kind: TxKind,
        val amountMinor: Long,
        val currency: String,
        val timestamp: Long,
        /** Wallet the capture rules found; null when none did. */
        val ruleWalletId: Long?,
        val merchant: String? = null,
        val categoryId: Long? = null,
        val cardLast4: String? = null,
    )

    /** Which app posted a notification; both Google Play services and Wallet count as Google Pay. */
    fun sourceOf(packageName: String): String = BankSource.byPackage(packageName)?.name ?: packageName

    /**
     * [tx] is the same payment as [new] when it was captured from a notification of another app, for the
     * same amount, currency and direction, within [WINDOW_MS] — and the rules do not put the two on
     * different cards. Two identical payments from one app are two real payments, so they never group.
     *
     * @param txPackages apps whose notifications already point to [tx]
     * @param txWalletByRule whether the wallet of [tx] came from a rule rather than the default wallet
     */
    fun isSamePayment(tx: TransactionEntity, txPackages: Collection<String>, txWalletByRule: Boolean, new: Capture): Boolean {
        if (tx.source != TxSource.NOTIFICATION) return false
        if (tx.kind != new.kind || (tx.kind != TxKind.EXPENSE && tx.kind != TxKind.INCOME)) return false
        if (tx.amountMinor != new.amountMinor || !tx.currency.equals(new.currency, true)) return false
        if (kotlin.math.abs(tx.timestamp - new.timestamp) > WINDOW_MS) return false
        val sources = txPackages.map(::sourceOf).toSet()
        if (sources.isEmpty() || sourceOf(new.packageName) in sources) return false
        if (txWalletByRule && new.ruleWalletId != null && new.ruleWalletId != tx.walletId) return false
        if (tx.cardLast4 != null && new.cardLast4 != null && tx.cardLast4 != new.cardLast4) return false
        return true
    }

    /** [tx] with the gaps filled from the second notification; what is already known stays. */
    fun merged(tx: TransactionEntity, txWalletByRule: Boolean, new: Capture): TransactionEntity = tx.copy(
        walletId = if (!txWalletByRule && new.ruleWalletId != null) new.ruleWalletId else tx.walletId,
        categoryId = tx.categoryId ?: new.categoryId,
        merchant = tx.merchant?.ifBlank { null } ?: new.merchant?.ifBlank { null },
        cardLast4 = tx.cardLast4 ?: new.cardLast4,
        timestamp = minOf(tx.timestamp, new.timestamp),
    )
}
