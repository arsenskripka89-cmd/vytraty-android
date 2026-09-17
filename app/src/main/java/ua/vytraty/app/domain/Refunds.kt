package ua.vytraty.app.domain

import ua.vytraty.app.data.db.TxKind

/**
 * A refund is money coming back for something already spent: "Повернення" in the income screen with an
 * expense category. It is stored as an EXPENSE with a negative amount, so every total that already
 * exists — the category, the budget, the month, the wallet balance — corrects itself without a special case.
 */
object Refunds {

    fun isRefund(kind: TxKind, amountMinor: Long) = kind == TxKind.EXPENSE && amountMinor < 0

    /** Form (income + an expense category) → what is written to the database. [amount] is positive. */
    fun stored(formKind: TxKind, categoryKind: TxKind?, amount: Long): Pair<TxKind, Long> =
        if (formKind == TxKind.INCOME && categoryKind == TxKind.EXPENSE) TxKind.EXPENSE to -amount else formKind to amount

    /** Database row → the kind the editor shows. */
    fun formKind(kind: TxKind, amountMinor: Long) = if (isRefund(kind, amountMinor)) TxKind.INCOME else kind
}
