package ua.vytraty.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ua.vytraty.app.VytratyApp
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money

/** Fires planned-payment reminders and handles the "record" action from the notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VytratyApp
        val plannedId = intent.getLongExtra(AppNotifications.EXTRA_PLANNED_ID, -1L)
        if (plannedId <= 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_REMIND -> remind(context, app, plannedId)
                    ACTION_RECORD -> app.container.plannedPayments.recordNow(plannedId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun remind(context: Context, app: VytratyApp, plannedId: Long) {
        val p = app.container.db.plannedPaymentDao().byId(plannedId) ?: return
        if (!p.active) return
        val due = Dates.formatDate(p.nextDueAt)
        val wallet = app.container.db.walletDao().byId(p.walletId)?.name ?: ""
        AppNotifications.showReminder(
            context, p.id,
            "${p.title}: ${Money.format(p.amountMinor, p.currency)}",
            "Платіж заплановано на $due · $wallet",
        )
    }

    companion object {
        const val ACTION_REMIND = "ua.vytraty.app.action.REMIND"
        const val ACTION_RECORD = "ua.vytraty.app.action.RECORD"
    }
}

/** Re-registers alarms after reboot or app update (alarms do not survive those). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as VytratyApp
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.plannedPayments.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
