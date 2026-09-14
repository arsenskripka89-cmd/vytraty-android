package ua.vytraty.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun byId(id: Long): WalletEntity?

    @Query("SELECT * FROM wallets WHERE cardLast4 = :last4 AND archived = 0 LIMIT 1")
    suspend fun byCardLast4(last4: String): WalletEntity?

    @Query("SELECT * FROM wallets WHERE monoAccountId = :accountId LIMIT 1")
    suspend fun byMonoAccountId(accountId: String): WalletEntity?

    @Query("SELECT * FROM wallets WHERE isDefault = 1 AND archived = 0 LIMIT 1")
    suspend fun defaultWallet(): WalletEntity?

    @Query("SELECT * FROM wallets WHERE archived = 0 ORDER BY sortOrder, id LIMIT 1")
    suspend fun firstActive(): WalletEntity?

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Query("UPDATE wallets SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefault(id: Long)

    @Insert suspend fun insert(w: WalletEntity): Long
    @Update suspend fun update(w: WalletEntity)
    @Delete suspend fun delete(w: WalletEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert suspend fun insert(c: CategoryEntity): Long
    @Insert suspend fun insertAll(c: List<CategoryEntity>)
    @Update suspend fun update(c: CategoryEntity)
    @Delete suspend fun delete(c: CategoryEntity)
}

@Dao
interface TransactionDao {
    companion object {
        const val ROW_SELECT = """
            SELECT t.id, t.walletId, t.categoryId, t.kind, t.amountMinor, t.currency, t.timestamp,
                   t.merchant, t.note, t.source, t.cardLast4, t.transferToWalletId,
                   w.name AS walletName, w.color AS walletColor,
                   c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor
            FROM transactions t
            JOIN wallets w ON w.id = t.walletId
            LEFT JOIN categories c ON c.id = t.categoryId
        """
    }

    @Query(
        "$ROW_SELECT WHERE t.timestamp BETWEEN :from AND :to " +
            "AND (:walletId IS NULL OR t.walletId = :walletId) " +
            "AND (:categoryId IS NULL OR t.categoryId = :categoryId) " +
            "AND (:onlyUncategorized = 0 OR (t.categoryId IS NULL AND t.kind != 'TRANSFER')) " +
            "AND (:query = '' OR t.merchant LIKE '%' || :query || '%' OR t.note LIKE '%' || :query || '%') " +
            "ORDER BY t.timestamp DESC, t.id DESC"
    )
    fun observeRows(
        from: Long, to: Long, walletId: Long?, categoryId: Long?, onlyUncategorized: Boolean, query: String,
    ): Flow<List<TransactionRow>>

    @Query("$ROW_SELECT ORDER BY t.timestamp DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionRow>>

    @Query("$ROW_SELECT WHERE t.id = :id")
    fun observeRow(id: Long): Flow<TransactionRow?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun byExternalId(source: TxSource, externalId: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId IS NULL AND kind != 'TRANSFER'")
    fun observeUncategorizedCount(): Flow<Int>

    @Query("SELECT categoryId, SUM(amountMinor) AS total FROM transactions WHERE kind = :kind AND timestamp BETWEEN :from AND :to AND (:walletId IS NULL OR walletId = :walletId) GROUP BY categoryId ORDER BY total DESC")
    fun observeSumsByCategory(kind: TxKind, from: Long, to: Long, walletId: Long?): Flow<List<CategorySum>>

    @Query(
        "SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch', 'localtime') AS monthKey, " +
            "SUM(CASE WHEN kind = 'EXPENSE' THEN amountMinor ELSE 0 END) AS expense, " +
            "SUM(CASE WHEN kind = 'INCOME' THEN amountMinor ELSE 0 END) AS income " +
            "FROM transactions WHERE timestamp BETWEEN :from AND :to AND (:walletId IS NULL OR walletId = :walletId) " +
            "GROUP BY monthKey ORDER BY monthKey"
    )
    fun observeMonthSums(from: Long, to: Long, walletId: Long?): Flow<List<MonthSum>>

    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS dayKey, " +
            "SUM(CASE WHEN kind = 'EXPENSE' THEN amountMinor ELSE 0 END) AS expense, " +
            "SUM(CASE WHEN kind = 'INCOME' THEN amountMinor ELSE 0 END) AS income " +
            "FROM transactions WHERE timestamp BETWEEN :from AND :to AND (:walletId IS NULL OR walletId = :walletId) " +
            "GROUP BY dayKey ORDER BY dayKey"
    )
    fun observeDaySums(from: Long, to: Long, walletId: Long?): Flow<List<DaySum>>

    @Query(
        "SELECT walletId, SUM(CASE WHEN kind = 'INCOME' THEN amountMinor ELSE -amountMinor END) AS delta " +
            "FROM transactions GROUP BY walletId"
    )
    fun observeWalletDeltas(): Flow<List<WalletBalance>>

    @Query("SELECT transferToWalletId AS walletId, SUM(amountMinor) AS delta FROM transactions WHERE kind = 'TRANSFER' AND transferToWalletId IS NOT NULL GROUP BY transferToWalletId")
    fun observeTransferIncoming(): Flow<List<WalletBalance>>

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE kind = 'EXPENSE' AND timestamp BETWEEN :from AND :to AND (:categoryId IS NULL OR categoryId = :categoryId)")
    suspend fun sumExpenses(from: Long, to: Long, categoryId: Long?): Long?

    @Query("SELECT SUM(amountMinor) FROM transactions WHERE kind = 'EXPENSE' AND timestamp BETWEEN :from AND :to AND (:categoryId IS NULL OR categoryId = :categoryId)")
    fun observeSumExpenses(from: Long, to: Long, categoryId: Long?): Flow<Long?>

    @Query(
        "SELECT COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amountMinor ELSE -amountMinor END), 0) " +
            "+ COALESCE((SELECT SUM(amountMinor) FROM transactions WHERE kind = 'TRANSFER' AND transferToWalletId = :walletId), 0) " +
            "FROM transactions WHERE walletId = :walletId"
    )
    suspend fun sumForWallet(walletId: Long): Long

    @Query("SELECT * FROM transactions WHERE categoryId IS NULL AND merchant IS NOT NULL AND kind != 'TRANSFER'")
    suspend fun uncategorizedWithMerchant(): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?)

    @Query("$ROW_SELECT WHERE t.timestamp BETWEEN :from AND :to ORDER BY t.timestamp")
    suspend fun rowsForExport(from: Long, to: Long): List<TransactionRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: TransactionEntity): Long
    @Update suspend fun update(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules WHERE merchantNormalized = :normalized LIMIT 1")
    suspend fun byMerchant(normalized: String): MerchantRuleEntity?

    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(r: MerchantRuleEntity)
    @Delete suspend fun delete(r: MerchantRuleEntity)
}

@Dao
interface PlannedPaymentDao {
    @Query("SELECT * FROM planned_payments ORDER BY active DESC, nextDueAt")
    fun observeAll(): Flow<List<PlannedPaymentEntity>>

    @Query("SELECT * FROM planned_payments WHERE active = 1 ORDER BY nextDueAt")
    suspend fun allActive(): List<PlannedPaymentEntity>

    @Query("SELECT * FROM planned_payments WHERE id = :id")
    suspend fun byId(id: Long): PlannedPaymentEntity?

    @Insert suspend fun insert(p: PlannedPaymentEntity): Long
    @Update suspend fun update(p: PlannedPaymentEntity)
    @Delete suspend fun delete(p: PlannedPaymentEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun byId(id: Long): BudgetEntity?

    @Insert suspend fun insert(b: BudgetEntity): Long
    @Update suspend fun update(b: BudgetEntity)
    @Delete suspend fun delete(b: BudgetEntity)
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_log ORDER BY postedAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_log WHERE parsed = 0")
    fun observeUnparsedCount(): Flow<Int>

    @Query("SELECT * FROM notification_log WHERE packageName = :pkg AND text = :text AND postedAt > :since LIMIT 1")
    suspend fun findDuplicate(pkg: String, text: String?, since: Long): NotificationLogEntity?

    @Insert suspend fun insert(n: NotificationLogEntity): Long
    @Update suspend fun update(n: NotificationLogEntity)
    @Query("DELETE FROM notification_log") suspend fun clear()
    @Query("DELETE FROM notification_log WHERE postedAt < :before") suspend fun deleteOlderThan(before: Long)
}
