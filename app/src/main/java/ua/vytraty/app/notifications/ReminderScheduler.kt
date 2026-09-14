package ua.vytraty.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ua.vytraty.app.data.db.PlannedPaymentEntity
import java.util.concurrent.TimeUnit

/** Schedules one alarm per planned payment at (nextDueAt - remindDaysBefore days) at 10:00 local time. */
class ReminderScheduler(private val context: Context) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun pending(plannedId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(AppNotifications.EXTRA_PLANNED_ID, plannedId)
        }
        return PendingIntent.getBroadcast(
            context, (plannedId % Int.MAX_VALUE).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(p: PlannedPaymentEntity) {
        val remindAt = reminderTime(p)
        val now = System.currentTimeMillis()
        val fireAt = if (remindAt < now) now + 5_000 else remindAt
        if (p.nextDueAt + TimeUnit.DAYS.toMillis(1) < now) return // long overdue: skip re-firing
        val pi = pending(p.id)
        val am = alarmManager
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
    }

    fun cancel(plannedId: Long) {
        alarmManager.cancel(pending(plannedId))
    }

    companion object {
        fun reminderTime(p: PlannedPaymentEntity): Long {
            val dueDay = ua.vytraty.app.domain.Dates.toLocalDate(p.nextDueAt).minusDays(p.remindDaysBefore.toLong())
            return ua.vytraty.app.domain.Dates.toMillis(dueDay.atTime(10, 0))
        }
    }
}
