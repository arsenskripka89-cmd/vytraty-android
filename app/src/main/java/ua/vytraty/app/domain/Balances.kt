package ua.vytraty.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.WalletEntity

data class WalletWithBalance(val wallet: WalletEntity, val balanceMinor: Long)

/** Balance = initial + incomes − expenses − outgoing transfers + incoming transfers. */
fun observeWalletBalances(db: AppDatabase, includeArchived: Boolean = false): Flow<List<WalletWithBalance>> = combine(
    if (includeArchived) db.walletDao().observeAll() else db.walletDao().observeActive(),
    db.transactionDao().observeWalletDeltas(),
    db.transactionDao().observeTransferIncoming(),
) { wallets, deltas, incoming ->
    val d = deltas.associate { it.walletId to it.delta }
    val i = incoming.associate { it.walletId to it.delta }
    wallets.map { w -> WalletWithBalance(w, w.initialBalanceMinor + (d[w.id] ?: 0L) + (i[w.id] ?: 0L)) }
}
