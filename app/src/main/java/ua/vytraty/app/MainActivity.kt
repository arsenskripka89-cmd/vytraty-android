package ua.vytraty.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import ua.vytraty.app.notifications.AppNotifications
import ua.vytraty.app.ui.navigation.PendingNav
import ua.vytraty.app.ui.navigation.VytratyNavHost
import ua.vytraty.app.ui.theme.VytratyTheme

class MainActivity : ComponentActivity() {
    private val pendingNav = MutableStateFlow<PendingNav?>(null)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotificationPermission()
        setContent {
            VytratyTheme {
                VytratyNavHost(pendingNav = pendingNav, onPendingConsumed = { pendingNav.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val txId = intent.getLongExtra(AppNotifications.EXTRA_TRANSACTION_ID, -1L)
        val plannedId = intent.getLongExtra(AppNotifications.EXTRA_PLANNED_ID, -1L)
        when {
            txId > 0 -> pendingNav.value = PendingNav.Transaction(txId)
            plannedId > 0 -> pendingNav.value = PendingNav.Planned(plannedId)
            intent.getBooleanExtra(AppNotifications.EXTRA_OPEN_UPDATES, false) -> pendingNav.value = PendingNav.Updates
        }
        intent.removeExtra(AppNotifications.EXTRA_TRANSACTION_ID)
        intent.removeExtra(AppNotifications.EXTRA_PLANNED_ID)
        intent.removeExtra(AppNotifications.EXTRA_OPEN_UPDATES)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
