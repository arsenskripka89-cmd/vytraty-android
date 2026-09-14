package ua.vytraty.app.data.bank.monobank

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.vytraty.app.VytratyApp
import java.util.concurrent.TimeUnit

/** Hourly background sync of Monobank statements when a token is configured. */
class MonobankSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VytratyApp).container
        val s = container.settings.current()
        if (s.monobankToken.isBlank() || !s.monobankAutoSync) return Result.success()
        val r = container.monobank.sync()
        return if (r.error == null) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "monobank_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonobankSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
