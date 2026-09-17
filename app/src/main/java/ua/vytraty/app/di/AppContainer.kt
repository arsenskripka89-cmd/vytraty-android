package ua.vytraty.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ua.vytraty.app.data.bank.monobank.MonobankApi
import ua.vytraty.app.data.bank.monobank.MonobankConnector
import ua.vytraty.app.data.db.AppDatabase
import ua.vytraty.app.data.prefs.SettingsRepository
import ua.vytraty.app.data.rates.ExchangeRates
import ua.vytraty.app.data.update.UpdateChecker
import ua.vytraty.app.domain.parser.ParserRegistry
import ua.vytraty.app.domain.usecase.AssignCategoryUseCase
import ua.vytraty.app.domain.usecase.BudgetChecker
import ua.vytraty.app.domain.usecase.MergeTransfersUseCase
import ua.vytraty.app.domain.usecase.PlannedPaymentService
import ua.vytraty.app.domain.usecase.RecordParsedPaymentUseCase
import ua.vytraty.app.notifications.ReminderScheduler

/** Manual dependency graph; created once in [ua.vytraty.app.VytratyApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val parserRegistry = ParserRegistry()
    val budgetChecker = BudgetChecker(context, db)
    val mergeTransfers = MergeTransfersUseCase(context, db, settings)
    val recordPayment = RecordParsedPaymentUseCase(context, db, settings, parserRegistry, budgetChecker, mergeTransfers)
    val assignCategory = AssignCategoryUseCase(db)
    val reminderScheduler = ReminderScheduler(context)
    val plannedPayments = PlannedPaymentService(context, db, reminderScheduler, budgetChecker)
    val monobank = MonobankConnector(MonobankApi.create(), db, settings, budgetChecker)
    val updateChecker = UpdateChecker(context)
    val exchangeRates = ExchangeRates(settings)
}
