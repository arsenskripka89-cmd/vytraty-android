package ua.vytraty.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ua.vytraty.app.MainActivity
import ua.vytraty.app.data.db.CategoryEntity
import ua.vytraty.app.R

/** All app-originated notifications: channels, uncategorized prompt, reminders, budget alerts. */
object AppNotifications {
    const val CHANNEL_CAPTURED = "captured_payments"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_BUDGETS = "budgets"
    const val CHANNEL_UPDATES = "updates"

    const val EXTRA_TRANSACTION_ID = "transactionId"
    const val EXTRA_CATEGORY_ID = "categoryId"
    const val EXTRA_PLANNED_ID = "plannedId"
    const val EXTRA_OPEN_UPDATES = "openUpdates"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTURED, "Захоплені платежі", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Нові витрати зі сповіщень банків та Google Pay"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Нагадування про платежі", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGETS, "Бюджети", NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "Оновлення застосунку", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openTransactionIntent(context: Context, transactionId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, (transactionId % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun capturedNotificationId(transactionId: Long) = 2000 + (transactionId % 100000).toInt()

    /**
     * "Choose a category" prompt for a captured payment: one button per suggested category assigns it
     * right from the notification shade, the last one opens the app.
     */
    fun showCapturedPayment(
        context: Context,
        transactionId: Long,
        title: String,
        body: String,
        suggestions: List<CategoryEntity> = emptyList(),
    ) {
        if (!canPost(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_CAPTURED)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openTransactionIntent(context, transactionId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Android shows at most three actions, so two categories plus "open" is the ceiling.
        suggestions.take(2).forEachIndexed { i, cat ->
            val intent = Intent(context, CategoryActionReceiver::class.java).apply {
                action = CategoryActionReceiver.ACTION_ASSIGN
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                putExtra(EXTRA_CATEGORY_ID, cat.id)
            }
            val pi = PendingIntent.getBroadcast(
                context, (transactionId % 100000).toInt() * 8 + i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, cat.name, pi)
        }
        builder.addAction(0, "Перейти в програму", openTransactionIntent(context, transactionId))
        NotificationManagerCompat.from(context).notify(capturedNotificationId(transactionId), builder.build())
    }

    fun cancelCaptured(context: Context, transactionId: Long) {
        NotificationManagerCompat.from(context).cancel(capturedNotificationId(transactionId))
    }

    /** Two captured notifications were joined into one transfer; one button undoes that. */
    fun showTransferMerged(context: Context, transactionId: Long, title: String, body: String) {
        if (!canPost(context)) return
        val split = Intent(context, TransferActionReceiver::class.java).apply {
            action = TransferActionReceiver.ACTION_SPLIT
            putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
        val splitPi = PendingIntent.getBroadcast(
            context, (transactionId % 100000).toInt() * 8 + 7, split,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_CAPTURED)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openTransactionIntent(context, transactionId))
            .addAction(0, "Це не переказ", splitPi)
            .addAction(0, "Перейти в програму", openTransactionIntent(context, transactionId))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(capturedNotificationId(transactionId), n)
    }

    fun showReminder(context: Context, plannedId: Long, title: String, body: String) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_PLANNED_ID, plannedId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, (plannedId % Int.MAX_VALUE).toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val record = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_RECORD
            putExtra(EXTRA_PLANNED_ID, plannedId)
        }
        val recordPi = PendingIntent.getBroadcast(
            context, (plannedId % Int.MAX_VALUE).toInt() + 1, record,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openPi)
            .addAction(0, "Записати платіж", recordPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(3000 + (plannedId % 100000).toInt(), n)
    }

    fun cancelReminder(context: Context, plannedId: Long) {
        NotificationManagerCompat.from(context).cancel(3000 + (plannedId % 100000).toInt())
    }

    fun showUpdateAvailable(context: Context, title: String, body: String) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_UPDATES, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(context, 8000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(8000, n)
    }

    fun showBudgetAlert(context: Context, budgetId: Long, title: String, body: String) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(context, 7000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_BUDGETS)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(4000 + (budgetId % 100000).toInt(), n)
    }
}
