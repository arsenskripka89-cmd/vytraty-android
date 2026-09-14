package ua.vytraty.app.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ua.vytraty.app.VytratyApp
import ua.vytraty.app.notifications.AppNotifications
import java.util.concurrent.TimeUnit

/** Daily check for a new release; shows a notification that opens the update screen. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as VytratyApp).container
        val info = container.updateChecker.check().getOrNull() ?: return Result.success()
        AppNotifications.showUpdateAvailable(
            applicationContext,
            "Доступне оновлення ${info.version}",
            "Торкніться, щоб завантажити та встановити нову версію застосунку.",
        )
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "update_check"
        private const val ONCE = "update_check_once"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build(),
            )
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
