package ua.vytraty.app.ui.navigation

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
import ua.vytraty.app.ui.settings.NotificationLogScreen
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
    const val PARSER_TESTER = "parser_tester"
    const val RULES = "rules"

    fun tx(id: Long = 0, kind: String = "EXPENSE") = "tx/$id?kind=$kind"
    fun wallet(id: Long = 0) = "wallet/$id"
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
            is PendingNav.Planned -> { nav.navigate(Routes.PLANS); onPendingConsumed() }
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
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
        NavHost(nav, startDestination = Routes.OVERVIEW, modifier = Modifier.padding(padding)) {
            composable(Routes.OVERVIEW) {
                OverviewScreen(
                    onOpenTransaction = { nav.navigate(Routes.tx(it)) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onOpenWallets = { nav.navigate(Routes.WALLETS) },
                    onOpenPlans = { nav.navigate(Routes.PLANS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
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
                    onLog = { nav.navigate(Routes.NOTIF_LOG) },
                )
            }
            composable(
                "tx/{id}?kind={kind}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" },
                ),
            ) { entry ->
                TransactionEditScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    initialKind = entry.arguments?.getString("kind") ?: "EXPENSE",
                    onBack = { nav.popBackStack() },
                    onNewCategory = { kind -> nav.navigate(Routes.category(0, kind)) },
                )
            }
            composable(Routes.WALLETS) { WalletsScreen(onEdit = { nav.navigate(Routes.wallet(it)) }, onBack = { nav.popBackStack() }) }
            composable("wallet/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                WalletEditScreen(id = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
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
            composable(Routes.NOTIF_LOG) { NotificationLogScreen(onBack = { nav.popBackStack() }, onOpenTransaction = { nav.navigate(Routes.tx(it)) }) }
            composable(Routes.PARSER_TESTER) { ParserTesterScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.RULES) { MerchantRulesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
