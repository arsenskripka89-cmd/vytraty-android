@file:OptIn(ExperimentalCoroutinesApi::class)

package ua.vytraty.app.ui.overview

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ua.vytraty.app.data.db.BudgetEntity
import ua.vytraty.app.data.db.PlannedPaymentEntity
import ua.vytraty.app.data.db.TransactionRow
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.WalletWithBalance
import ua.vytraty.app.domain.observeWalletBalances
import ua.vytraty.app.notifications.PaymentNotificationListener
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.EmptyState
import ua.vytraty.app.ui.components.SectionHeader
import ua.vytraty.app.ui.components.StatTile
import ua.vytraty.app.ui.components.WalletIcon
import ua.vytraty.app.ui.components.appViewModel
import ua.vytraty.app.ui.components.toColor
import ua.vytraty.app.ui.history.TransactionRowItem
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import ua.vytraty.app.ui.theme.WarnAmber
import java.time.YearMonth
import java.util.concurrent.TimeUnit

data class BudgetProgress(val budget: BudgetEntity, val spent: Long, val categoryName: String)

data class OverviewState(
    val wallets: List<WalletWithBalance> = emptyList(),
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val recent: List<TransactionRow> = emptyList(),
    val uncategorized: Int = 0,
    val budgets: List<BudgetProgress> = emptyList(),
    val upcoming: List<PlannedPaymentEntity> = emptyList(),
    val mainCurrency: String = "UAH",
)

class OverviewViewModel(c: AppContainer) : ViewModel() {
    private val db = c.db
    private val range = Dates.monthRange(YearMonth.now())

    private val budgets = combine(db.budgetDao().observeAll(), db.categoryDao().observeAll()) { bs, cats -> bs to cats }
        .flatMapLatest { (bs, cats) ->
            if (bs.isEmpty()) flowOf(emptyList()) else combine(
                bs.map { b ->
                    db.transactionDao().observeSumExpenses(range.first, range.last, b.categoryId).map { spent ->
                        BudgetProgress(b, spent ?: 0L, b.categoryId?.let { id -> cats.firstOrNull { it.id == id }?.name } ?: "Усі витрати")
                    }
                },
            ) { it.toList() }
        }

    private val monthSums = db.transactionDao().observeMonthSums(range.first, range.last, null).map { it.firstOrNull() }
    private val upcoming = db.plannedPaymentDao().observeAll().map { list ->
        val limit = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
        list.filter { it.active && it.nextDueAt <= limit }
    }

    val state: StateFlow<OverviewState> = combine(
        observeWalletBalances(db), monthSums, db.transactionDao().observeRecent(8),
        db.transactionDao().observeUncategorizedCount(), budgets, upcoming, c.settings.settings,
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        OverviewState(
            wallets = arr[0] as List<WalletWithBalance>,
            monthExpense = (arr[1] as ua.vytraty.app.data.db.MonthSum?)?.expense ?: 0L,
            monthIncome = (arr[1] as ua.vytraty.app.data.db.MonthSum?)?.income ?: 0L,
            recent = arr[2] as List<TransactionRow>,
            uncategorized = arr[3] as Int,
            budgets = arr[4] as List<BudgetProgress>,
            upcoming = arr[5] as List<PlannedPaymentEntity>,
            mainCurrency = (arr[6] as ua.vytraty.app.data.prefs.Settings).mainCurrency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState())
}

@Composable
fun OverviewScreen(
    onOpenTransaction: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenWallets: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel { OverviewViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var listenerEnabled by remember { mutableStateOf(PaymentNotificationListener.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) listenerEnabled = PaymentNotificationListener.isEnabled(context) }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    AppScaffold(title = Dates.formatMonth(YearMonth.now())) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            if (!listenerEnabled) item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp).clickable {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsOff, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Автозахоплення вимкнено", fontWeight = FontWeight.Bold)
                            Text("Надайте доступ до сповіщень, щоб платежі з Google Pay та банків записувались самі. Торкніться, щоб відкрити налаштування.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (s.uncategorized > 0) item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp, 8.dp).clickable(onClick = onOpenHistory),
                    colors = CardDefaults.cardColors(containerColor = WarnAmber.copy(alpha = 0.18f)),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = WarnAmber)
                        Spacer(Modifier.width(12.dp))
                        Text("Без категорії: ${s.uncategorized}. Торкніться, щоб призначити.", modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        StatTile("Витрати за місяць", Money.format(s.monthExpense, s.mainCurrency), ExpenseRed, Modifier.weight(1f))
                        StatTile("Доходи за місяць", Money.format(s.monthIncome, s.mainCurrency), IncomeGreen, Modifier.weight(1f))
                    }
                    val total = s.wallets.filter { it.wallet.currency == s.mainCurrency }.sumOf { it.balanceMinor }
                    StatTile("Разом на гаманцях (${s.mainCurrency})", Money.format(total, s.mainCurrency), MaterialTheme.colorScheme.onSurface)
                }
            }
            item {
                SectionHeader("Гаманці") { TextButton(onClick = onOpenWallets) { Text("Усі") } }
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.wallets, key = { it.wallet.id }) { wb -> WalletCard(wb, onClick = onOpenWallets) }
                }
            }
            if (s.budgets.isNotEmpty()) {
                item { SectionHeader("Бюджети") { TextButton(onClick = onOpenPlans) { Text("Усі") } } }
                items(s.budgets, key = { it.budget.id }) { bp -> BudgetRow(bp, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            }
            if (s.upcoming.isNotEmpty()) {
                item { SectionHeader("Найближчі платежі") { TextButton(onClick = onOpenPlans) { Text("Плани") } } }
                items(s.upcoming, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, fontWeight = FontWeight.Medium)
                            Text(Dates.formatDate(p.nextDueAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money.format(p.amountMinor, p.currency), color = if (p.kind == TxKind.EXPENSE) ExpenseRed else IncomeGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { SectionHeader("Останні операції") { TextButton(onClick = onOpenHistory) { Text("Історія") } } }
            if (s.recent.isEmpty()) item { EmptyState("Поки що немає операцій. Додайте вручну або дочекайтесь сповіщення про оплату.") }
            items(s.recent, key = { it.id }) { row -> TransactionRowItem(row, onClick = { onOpenTransaction(row.id) }) }
        }
    }
}

@Composable
fun WalletCard(wb: WalletWithBalance, onClick: () -> Unit) {
    Card(
        Modifier.width(180.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = wb.wallet.color.toColor()),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletIcon(wb.wallet.type, 0xFFFFFFFF, size = 28)
                Spacer(Modifier.width(8.dp))
                Text(wb.wallet.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Text(Money.format(wb.balanceMinor, wb.wallet.currency), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            wb.wallet.cardLast4?.let { Text("•••• $it", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
fun BudgetRow(bp: BudgetProgress, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val pct = if (bp.budget.limitMinor > 0) (bp.spent.toFloat() / bp.budget.limitMinor).coerceIn(0f, 1f) else 0f
    val color = when {
        pct >= 1f -> ExpenseRed
        pct >= 0.8f -> WarnAmber
        else -> IncomeGreen
    }
    Column(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row {
            Text(bp.categoryName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text("${Money.format(bp.spent, bp.budget.currency)} / ${Money.format(bp.budget.limitMinor, bp.budget.currency)}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth(), color = color)
    }
}
