package ua.vytraty.app.domain.usecase

import android.content.Context
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.notifications.AppNotifications
import java.time.YearMonth

/** Sends a notification when a monthly budget crosses 80 % or 100 %. */
class BudgetChecker(private val context: Context, private val db: AppDatabase) {

    suspend fun checkAfterExpense(categoryId: Long?) {
        val month = YearMonth.now()
        val range = Dates.monthRange(month)
        val key = Dates.monthKey(month)
        db.budgetDao().all()
            .filter { it.categoryId == null || it.categoryId == categoryId }
            .forEach { b ->
                val spent = db.transactionDao().sumExpenses(range.first, range.last, b.categoryId) ?: 0L
                if (b.limitMinor <= 0) return@forEach
                val pct = spent * 100 / b.limitMinor
                val name = b.categoryId?.let { db.categoryDao().byId(it)?.name } ?: "Усі витрати"
                if (pct >= 100 && b.notified100 != key) {
                    AppNotifications.showBudgetAlert(
                        context, b.id, "Бюджет «$name» вичерпано",
                        "Витрачено ${Money.format(spent, b.currency)} з ${Money.format(b.limitMinor, b.currency)}",
                    )
                    db.budgetDao().update(b.copy(notified100 = key, notified80 = key))
                } else if (pct >= 80 && b.notified80 != key) {
                    AppNotifications.showBudgetAlert(
                        context, b.id, "Бюджет «$name»: $pct %",
                        "Витрачено ${Money.format(spent, b.currency)} з ${Money.format(b.limitMinor, b.currency)}",
                    )
                    db.budgetDao().update(b.copy(notified80 = key))
                }
            }
    }
}
