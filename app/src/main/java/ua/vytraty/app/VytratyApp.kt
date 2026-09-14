package ua.vytraty.app

import android.app.Application
import kotlinx.coroutines.launch
import ua.vytraty.app.data.bank.monobank.MonobankSyncWorker
import ua.vytraty.app.data.db.Seed
import ua.vytraty.app.data.update.UpdateCheckWorker
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.notifications.AppNotifications

class VytratyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppNotifications.createChannels(this)
        UpdateCheckWorker.schedule(this)
        container.appScope.launch {
            Seed.seedIfEmpty(container.db)
            container.plannedPayments.rescheduleAll()
            if (container.settings.current().monobankToken.isNotBlank()) MonobankSyncWorker.schedule(this@VytratyApp)
        }
    }
}
