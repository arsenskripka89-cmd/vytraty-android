package ua.vytraty.app.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ua.vytraty.app.VytratyApp
import ua.vytraty.app.domain.parser.BankSource

/**
 * Listens to notifications of Google Pay and bank apps and forwards payment-looking ones to
 * [ua.vytraty.app.domain.usecase.RecordParsedPaymentUseCase]. The user must grant
 * "Notification access" in system settings; see [isEnabled].
 */
class PaymentNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    /**
     * Payments that came while the listener was not bound (the system killed the app, some firmware
     * unbinds listeners to save battery) are still on the shade: pick them up. Ones already handled
     * are dropped as duplicates by the store.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        val recent = System.currentTimeMillis() - CATCH_UP_MS
        active.filter { it.postTime > recent }.forEach { handle(it, active) }
    }

    /** Ask the system to bind us again instead of silently missing every payment until reboot. */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        rebind(this)
    }

    private fun handle(sbn: StatusBarNotification, active: Array<StatusBarNotification>? = null) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (BankSource.byPackage(pkg) == null) return
        val content = extract(sbn.notification) ?: return
        // A group summary usually repeats its children; it is skipped only when a child is actually
        // there — Google Wallet may post a lone summary that carries the payment itself.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 && hasChildren(sbn, active)) return

        val app = applicationContext as VytratyApp
        scope.launch {
            try {
                app.container.recordPayment.handle(pkg, content.first, content.second, sbn.postTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle notification from $pkg", e)
            }
        }
    }

    private fun hasChildren(summary: StatusBarNotification, active: Array<StatusBarNotification>?): Boolean {
        val all = active ?: try { activeNotifications } catch (e: Exception) { null } ?: return true
        return all.any { it.packageName == summary.packageName && it.key != summary.key && it.groupKey == summary.groupKey }
    }

    companion object {
        private const val TAG = "PaymentListener"
        private const val CATCH_UP_MS = 12 * 60 * 60 * 1000L

        /** Title and body of a notification; the expanded text wins over the collapsed one. */
        fun extract(n: Notification): Pair<String?, String?>? {
            val extras = n.extras ?: return null
            val title = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
                ?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val body = listOfNotNull(text, lines?.takeIf { it != text }).joinToString("\n").ifBlank { null }
                // Some apps put the amount only in the ticker or the sub text.
                ?: listOfNotNull(sub, n.tickerText?.toString()).joinToString("\n").ifBlank { null }
            if (title.isNullOrBlank() && body.isNullOrBlank()) return null
            return title to body
        }

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, PaymentNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Rebinds the listener if access is granted; called on app start so a dropped binding comes back. */
        fun rebind(context: Context) {
            if (!isEnabled(context)) return
            try {
                NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "requestRebind failed", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
