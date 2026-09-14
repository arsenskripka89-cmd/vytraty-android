package ua.vytraty.app.domain.usecase

import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.MerchantRuleEntity
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.domain.MerchantNormalizer

/**
 * Assigns a category to a transaction and, when the transaction has a merchant, remembers the
 * merchant→category pair so future captured payments are categorized automatically.
 */
class AssignCategoryUseCase(private val db: AppDatabase) {

    data class Outcome(val ruleSaved: Boolean, val otherUncategorizedSameMerchant: List<Long>)

    suspend fun assign(transactionId: Long, categoryId: Long?, learn: Boolean = true): Outcome {
        val tx = db.transactionDao().byId(transactionId) ?: return Outcome(false, emptyList())
        db.transactionDao().setCategory(listOf(transactionId), categoryId)
        if (categoryId == null || !learn) return Outcome(false, emptyList())
        val normalized = MerchantNormalizer.normalize(tx.merchant)
        if (normalized.isEmpty()) return Outcome(false, emptyList())
        val existing = db.merchantRuleDao().byMerchant(normalized)
        db.merchantRuleDao().upsert(
            MerchantRuleEntity(
                id = existing?.id ?: 0,
                merchantNormalized = normalized,
                merchantDisplay = MerchantNormalizer.display(tx.merchant),
                categoryId = categoryId,
                hits = (existing?.hits ?: 0) + 1,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val others = db.transactionDao().uncategorizedWithMerchant()
            .filter { it.id != transactionId && it.kind == tx.kind && MerchantNormalizer.normalize(it.merchant) == normalized }
            .map { it.id }
        return Outcome(true, others)
    }

    suspend fun applyToOthers(ids: List<Long>, categoryId: Long) {
        if (ids.isNotEmpty()) db.transactionDao().setCategory(ids, categoryId)
    }

    /** Re-runs merchant rules over every uncategorized captured transaction. */
    suspend fun applyRulesToUncategorized(): Int {
        var n = 0
        db.transactionDao().uncategorizedWithMerchant()
            .filter { it.source != TxSource.MANUAL }
            .forEach { tx ->
                val rule = db.merchantRuleDao().byMerchant(MerchantNormalizer.normalize(tx.merchant)) ?: return@forEach
                val cat = db.categoryDao().byId(rule.categoryId) ?: return@forEach
                if (cat.kind == tx.kind) {
                    db.transactionDao().setCategory(listOf(tx.id), cat.id)
                    n++
                }
            }
        return n
    }
}
