package ua.vytraty.app.domain.usecase

import android.content.Context
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.db.PlannedPaymentEntity
import ua.vytraty.app.data.db.Recurrence
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.notifications.AppNotifications
import ua.vytraty.app.notifications.ReminderScheduler

/** Creates transactions from planned payments and moves them to the next due date. */
class PlannedPaymentService(
    private val context: Context,
    private val db: AppDatabase,
    private val scheduler: ReminderScheduler,
    private val budgetChecker: BudgetChecker,
) {
    suspend fun save(p: PlannedPaymentEntity): Long {
        val id = if (p.id == 0L) db.plannedPaymentDao().insert(p) else { db.plannedPaymentDao().update(p); p.id }
        val saved = p.copy(id = id)
        if (saved.active) scheduler.schedule(saved) else scheduler.cancel(saved.id)
        return id
    }

    suspend fun delete(p: PlannedPaymentEntity) {
        scheduler.cancel(p.id)
        db.plannedPaymentDao().delete(p)
    }

    /** Records the payment as a real transaction dated now and advances the plan. */
    suspend fun recordNow(plannedId: Long): Long? {
        val p = db.plannedPaymentDao().byId(plannedId) ?: return null
        val txId = db.transactionDao().insert(
            TransactionEntity(
                walletId = p.walletId,
                categoryId = p.categoryId,
                kind = p.kind,
                amountMinor = p.amountMinor,
                currency = p.currency,
                timestamp = System.currentTimeMillis(),
                merchant = p.title,
                note = p.note,
                source = TxSource.MANUAL,
            ),
        )
        advance(p)
        AppNotifications.cancelReminder(context, p.id)
        budgetChecker.checkAfterExpense(p.categoryId)
        return txId
    }

    /** Skips the current occurrence without recording anything. */
    suspend fun skip(plannedId: Long) {
        val p = db.plannedPaymentDao().byId(plannedId) ?: return
        advance(p)
        AppNotifications.cancelReminder(context, p.id)
    }

    private suspend fun advance(p: PlannedPaymentEntity) {
        val next = Dates.nextDue(p.nextDueAt, p.recurrence)
        val updated = if (p.recurrence == Recurrence.NONE || next == null) p.copy(active = false) else p.copy(nextDueAt = next)
        db.plannedPaymentDao().update(updated)
        if (updated.active) scheduler.schedule(updated) else scheduler.cancel(updated.id)
    }

    suspend fun rescheduleAll() {
        db.plannedPaymentDao().allActive().forEach { scheduler.schedule(it) }
    }
}
