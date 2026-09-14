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
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (BankSource.byPackage(pkg) == null) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val body = listOfNotNull(text, lines?.takeIf { it != text }).joinToString("\n").ifBlank { null }
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        // Group summaries repeat child notifications; skip them.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val app = applicationContext as VytratyApp
        scope.launch {
            try {
                app.container.recordPayment.handle(pkg, title, body, sbn.postTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle notification from $pkg", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PaymentListener"

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, PaymentNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
