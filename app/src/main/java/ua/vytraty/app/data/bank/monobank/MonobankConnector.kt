package ua.vytraty.app.data.bank.monobank

import ua.vytraty.app.data.bank.BankConnector
import ua.vytraty.app.data.bank.SyncResult
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.data.db.WalletType
import ua.vytraty.app.data.prefs.SettingsRepository
import ua.vytraty.app.domain.MerchantNormalizer
import ua.vytraty.app.domain.usecase.BudgetChecker
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

class MonobankConnector(
    private val api: MonobankApi,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val budgetChecker: BudgetChecker,
) : BankConnector {
    override val code = "mono"
    override val displayName = "Monobank"

    override suspend fun validate(): String? {
        val token = settings.current().monobankToken
        if (token.isBlank()) return "Токен не вказано"
        return try {
            api.clientInfo(token); null
        } catch (e: HttpException) {
            when (e.code()) {
                401, 403 -> "Токен недійсний"
                429 -> "Забагато запитів, спробуйте за хвилину"
                else -> "Помилка Monobank: ${e.code()}"
            }
        } catch (e: Exception) {
            "Немає з'єднання: ${e.message}"
        }
    }

    override suspend fun sync(): SyncResult {
        val s = settings.current()
        val token = s.monobankToken
        if (token.isBlank()) return SyncResult(0, 0, "Токен не вказано")
        return try {
            val info = api.clientInfo(token)
            var walletsCreated = 0
            var imported = 0
            val now = System.currentTimeMillis()
            // First sync: last 31 days; later: since last sync minus a day of overlap (dedup by external id).
            val from = if (s.monobankLastSync == 0L) now - TimeUnit.DAYS.toMillis(31) else s.monobankLastSync - TimeUnit.DAYS.toMillis(1)
            info.accounts.forEachIndexed { index, acc ->
                var wallet = db.walletDao().byMonoAccountId(acc.id)
                if (wallet == null) {
                    val last4 = acc.maskedPan.firstOrNull()?.takeLast(4)
                    wallet = WalletEntity(
                        name = "mono ${acc.type} ${MonoCurrency.code(acc.currencyCode)}",
                        type = WalletType.BANK_ACCOUNT,
                        currency = MonoCurrency.code(acc.currencyCode),
                        initialBalanceMinor = 0,
                        color = 0xFF212121,
                        cardLast4 = last4,
                        bankCode = code,
                        monoAccountId = acc.id,
                        sortOrder = 100 + index,
                    )
                    val id = db.walletDao().insert(wallet)
                    wallet = wallet.copy(id = id)
                    walletsCreated++
                }
                if (index > 0) delay(61_000) // API limit: one statement request per minute
                val items = api.statement(token, acc.id, from / 1000, now / 1000)
                items.forEach { item ->
                    if (db.transactionDao().byExternalId(TxSource.BANK_API, item.id) != null) return@forEach
                    val kind = if (item.amount < 0) TxKind.EXPENSE else TxKind.INCOME
                    val merchant = item.description.ifBlank { item.counterName }.ifBlank { null }
                    val normalized = MerchantNormalizer.normalize(merchant)
                    val rule = if (normalized.isNotEmpty()) db.merchantRuleDao().byMerchant(normalized) else null
                    val categoryId = rule?.let { r -> db.categoryDao().byId(r.categoryId)?.takeIf { it.kind == kind }?.id }
                    val id = db.transactionDao().insert(
                        TransactionEntity(
                            walletId = wallet.id,
                            categoryId = categoryId,
                            kind = kind,
                            amountMinor = kotlin.math.abs(item.amount),
                            currency = wallet.currency,
                            timestamp = item.time * 1000,
                            merchant = merchant,
                            note = item.comment.ifBlank { null },
                            source = TxSource.BANK_API,
                            cardLast4 = wallet.cardLast4,
                            externalId = item.id,
                        ),
                    )
                    if (id > 0) imported++
                }
                // Align wallet initial balance so the computed balance equals the bank balance.
                alignBalance(wallet, acc.balance - acc.creditLimit)
            }
            settings.setMonobankLastSync(now)
            if (imported > 0) budgetChecker.checkAfterExpense(null)
            SyncResult(walletsCreated, imported)
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                401, 403 -> "Токен недійсний"
                429 -> "Забагато запитів (ліміт 1 запит/хв). Спробуйте пізніше"
                else -> "Помилка Monobank: ${e.code()}"
            }
            SyncResult(0, 0, msg)
        } catch (e: Exception) {
            SyncResult(0, 0, "Помилка: ${e.message}")
        }
    }

    private suspend fun alignBalance(wallet: WalletEntity, bankBalanceMinor: Long) {
        val deltas = db.transactionDao().sumForWallet(wallet.id)
        val computed = wallet.initialBalanceMinor + deltas
        if (computed != bankBalanceMinor) {
            db.walletDao().update(wallet.copy(initialBalanceMinor = wallet.initialBalanceMinor + (bankBalanceMinor - computed)))
        }
    }
}
