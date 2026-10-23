package ua.vytraty.app.ui.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.StateFlow
import ua.vytraty.app.ui.analytics.AnalyticsScreen
import ua.vytraty.app.ui.banks.BanksScreen
import ua.vytraty.app.ui.categories.CategoriesScreen
import ua.vytraty.app.ui.categories.CategoryEditScreen
import ua.vytraty.app.ui.history.HistoryScreen
import ua.vytraty.app.ui.more.MoreScreen
import ua.vytraty.app.ui.overview.OverviewScreen
import ua.vytraty.app.ui.plans.BudgetEditScreen
import ua.vytraty.app.ui.plans.PlannedPaymentEditScreen
import ua.vytraty.app.ui.plans.PlansScreen
import ua.vytraty.app.ui.settings.MerchantRulesScreen
import ua.vytraty.app.ui.rules.CaptureRulesScreen
import ua.vytraty.app.ui.rules.NotificationStoreScreen
import ua.vytraty.app.ui.rules.RuleEditScreen
import ua.vytraty.app.ui.settings.ParserTesterScreen
import ua.vytraty.app.ui.settings.SettingsScreen
import ua.vytraty.app.ui.transaction.TransactionEditScreen
import ua.vytraty.app.ui.wallets.WalletEditScreen
import ua.vytraty.app.ui.wallets.WalletsScreen

object Routes {
    const val OVERVIEW = "overview"
    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val PLANS = "plans"
    const val MORE = "more"
    const val WALLETS = "wallets"
    const val CATEGORIES = "categories"
    const val BANKS = "banks"
    const val SETTINGS = "settings"
    const val NOTIF_LOG = "notif_log"
    const val CAPTURE_RULES = "capture_rules"
    const val PARSER_TESTER = "parser_tester"
    const val RULES = "rules"

    fun tx(id: Long = 0, kind: String = "EXPENSE", walletId: Long = 0) = "tx/$id?kind=$kind&walletId=$walletId"
    fun wallet(id: Long = 0) = "wallet/$id"
    fun rule(id: Long = 0, logId: Long = 0, walletId: Long = 0) = "rule/$id?logId=$logId&walletId=$walletId"
    fun category(id: Long = 0, kind: String = "EXPENSE") = "category/$id?kind=$kind"
    fun planned(id: Long = 0) = "planned/$id"
    fun budget(id: Long = 0) = "budget/$id"
}

/** Deep-link request coming from a tapped notification. */
sealed class PendingNav {
    data class Transaction(val id: Long) : PendingNav()
    data class Planned(val id: Long) : PendingNav()
    data object Updates : PendingNav()
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/**
 * Switches bottom tabs. Огляд is the start destination: going back to it just pops everything above
 * it — saving and restoring its state would bring back the screen that was on top (Історія opened
 * from a link on Огляд came back instead of Огляд).
 */
private fun NavHostController.navigateToTab(route: String) {
    if (route == Routes.OVERVIEW) {
        if (!popBackStack(Routes.OVERVIEW, inclusive = false)) {
            navigate(Routes.OVERVIEW) { popUpTo(graph.findStartDestination().id) { inclusive = true }; launchSingleTop = true }
        }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val tabs = listOf(
    Tab(Routes.OVERVIEW, "Огляд", Icons.Filled.Dashboard),
    Tab(Routes.HISTORY, "Історія", Icons.Filled.Receipt),
    Tab(Routes.ANALYTICS, "Аналітика", Icons.Filled.PieChart),
    Tab(Routes.PLANS, "Плани", Icons.Filled.Event),
    Tab(Routes.MORE, "Ще", Icons.Filled.MoreHoriz),
)

@Composable
fun VytratyNavHost(pendingNav: StateFlow<PendingNav?>, onPendingConsumed: () -> Unit) {
    val nav: NavHostController = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTab = tabs.any { it.route == currentRoute }

    val pending by pendingNav.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        when (val p = pending) {
            is PendingNav.Transaction -> { nav.navigate(Routes.tx(p.id)); onPendingConsumed() }
            is PendingNav.Planned -> { nav.navigateToTab(Routes.PLANS); onPendingConsumed() }
            is PendingNav.Updates -> { nav.navigate(Routes.SETTINGS); onPendingConsumed() }
            null -> Unit
        }
    }

    Scaffold(
        bottomBar = {
            if (isTab) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { nav.navigateToTab(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (isTab && currentRoute != Routes.PLANS && currentRoute != Routes.MORE) {
                FloatingActionButton(onClick = { nav.navigate(Routes.tx()) }) { Icon(Icons.Filled.Add, "Додати") }
            }
        },
    ) { padding ->
        // The status and navigation bars are already paid for here: inner screens must not pad for them again.
        NavHost(nav, startDestination = Routes.OVERVIEW, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    onOpenTransaction = { nav.navigate(Routes.tx(it)) },
                    onOpenHistory = { nav.navigateToTab(Routes.HISTORY) },
                    onOpenWallets = { nav.navigate(Routes.WALLETS) },
                    onOpenWallet = { nav.navigate(Routes.tx(walletId = it)) },
                    onOpenPlans = { nav.navigateToTab(Routes.PLANS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenStore = { nav.navigate(Routes.NOTIF_LOG) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen(onOpenTransaction = { nav.navigate(Routes.tx(it)) }) }
            composable(Routes.ANALYTICS) { AnalyticsScreen() }
            composable(Routes.PLANS) {
                PlansScreen(
                    onEditPlanned = { nav.navigate(Routes.planned(it)) },
                    onEditBudget = { nav.navigate(Routes.budget(it)) },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onWallets = { nav.navigate(Routes.WALLETS) },
                    onCategories = { nav.navigate(Routes.CATEGORIES) },
                    onBanks = { nav.navigate(Routes.BANKS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onRules = { nav.navigate(Routes.RULES) },
                    onCaptureRules = { nav.navigate(Routes.CAPTURE_RULES) },
                    onLog = { nav.navigate(Routes.NOTIF_LOG) },
                )
            }
            composable(
                "tx/{id}?kind={kind}&walletId={walletId}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" },
                    navArgument("walletId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                TransactionEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialKind = entry.arguments?.getString("kind") ?: "EXPENSE",
                    onBack = { nav.popBackStack() },
                    onNewCategory = { kind -> nav.navigate(Routes.category(0, kind)) },
                    walletId = entry.arguments?.getLong("walletId") ?: 0L,
                )
            }
            composable(Routes.WALLETS) { WalletsScreen(onEdit = { nav.navigate(Routes.wallet(it)) }, onBack = { nav.popBackStack() }) }
            composable("wallet/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val walletId = entry.arguments?.getLong("id") ?: 0L
                WalletEditScreen(
                    id = walletId,
                    onBack = { nav.popBackStack() },
                    onEditRule = { ruleId -> nav.navigate(Routes.rule(id = ruleId, walletId = walletId)) },
                )
            }
            composable(Routes.CATEGORIES) {
                CategoriesScreen(onEdit = { id, kind -> nav.navigate(Routes.category(id, kind)) }, onBack = { nav.popBackStack() })
            }
            composable(
                "category/{id}?kind={kind}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" },
                ),
            ) { entry ->
                CategoryEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialKind = entry.arguments?.getString("kind") ?: "EXPENSE",
                    onBack = { nav.popBackStack() },
                )
            }
            composable("planned/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PlannedPaymentEditScreen(id = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable("budget/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                BudgetEditScreen(id = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(Routes.BANKS) { BanksScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onLog = { nav.navigate(Routes.NOTIF_LOG) },
                    onTester = { nav.navigate(Routes.PARSER_TESTER) },
                    onRules = { nav.navigate(Routes.RULES) },
                )
            }
            composable(Routes.NOTIF_LOG) {
                NotificationStoreScreen(onBack = { nav.popBackStack() }, onOpenRule = { logId -> nav.navigate(Routes.rule(logId = logId)) })
            }
            composable(Routes.CAPTURE_RULES) {
                CaptureRulesScreen(onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.rule(id = it)) })
            }
            composable(
                "rule/{id}?logId={logId}&walletId={walletId}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("logId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("walletId") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                RuleEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    logId = entry.arguments?.getLong("logId") ?: 0L,
                    walletId = entry.arguments?.getLong("walletId") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.PARSER_TESTER) { ParserTesterScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.RULES) { MerchantRulesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
