@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ua.vytraty.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ua.vytraty.app.data.db.CategoryEntity
import ua.vytraty.app.data.db.TransactionRow
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.Refunds
import ua.vytraty.app.ui.components.AmountText
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.CategoryPickerSheet
import ua.vytraty.app.ui.components.EmptyState
import ua.vytraty.app.ui.components.WalletPickerSheet
import ua.vytraty.app.ui.components.appViewModel
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import ua.vytraty.app.ui.theme.WarnAmber
import java.time.YearMonth

data class HistoryFilters(
    val month: YearMonth = YearMonth.now(),
    val walletId: Long? = null,
    val categoryId: Long? = null,
    val onlyUncategorized: Boolean = false,
    val query: String = "",
)

class HistoryViewModel(c: AppContainer) : ViewModel() {
    private val db = c.db
    val filters = MutableStateFlow(HistoryFilters())
    val wallets = db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rows = filters.flatMapLatest { f ->
        val r = Dates.monthRange(f.month)
        db.transactionDao().observeRows(r.first, r.last, f.walletId, f.categoryId, f.onlyUncategorized, f.query.trim())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(block: HistoryFilters.() -> HistoryFilters) = filters.update(block)
}

@Composable
fun HistoryScreen(onOpenTransaction: (Long) -> Unit) {
    val vm = appViewModel { HistoryViewModel(it) }
    val f by vm.filters.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showWallet by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    AppScaffold(
        title = "Історія",
        actions = { IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Filled.Search, "Пошук") } },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.update { copy(month = month.minusMonths(1)) } }) { Icon(Icons.Filled.ChevronLeft, "Попередній місяць") }
                Text(Dates.formatMonth(f.month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { vm.update { copy(month = month.plusMonths(1)) } }) { Icon(Icons.Filled.ChevronRight, "Наступний місяць") }
            }
            if (showSearch) {
                OutlinedTextField(
                    value = f.query, onValueChange = { q -> vm.update { copy(query = q) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                    placeholder = { Text("Магазин або нотатка") },
                    trailingIcon = { IconButton(onClick = { vm.update { copy(query = "") }; showSearch = false }) { Icon(Icons.Filled.Close, null) } },
                )
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = f.walletId != null, onClick = { showWallet = true },
                    label = { Text(wallets.firstOrNull { it.id == f.walletId }?.name ?: "Гаманець") },
                )
                FilterChip(
                    selected = f.categoryId != null, onClick = { showCategory = true },
                    label = { Text(categories.firstOrNull { it.id == f.categoryId }?.name ?: "Категорія") },
                )
                FilterChip(
                    selected = f.onlyUncategorized, onClick = { vm.update { copy(onlyUncategorized = !onlyUncategorized) } },
                    label = { Text("Без категорії") },
                    leadingIcon = { Icon(Icons.Filled.Warning, null, tint = WarnAmber) },
                )
            }
            val expense = rows.filter { it.kind == TxKind.EXPENSE }.sumOf { it.amountMinor }
            val income = rows.filter { it.kind == TxKind.INCOME }.sumOf { it.amountMinor }
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                Text("Витрати: ${Money.format(expense)}", color = ExpenseRed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("Доходи: ${Money.format(income)}", color = IncomeGreen, style = MaterialTheme.typography.bodyMedium)
            }
            if (rows.isEmpty()) EmptyState("Немає операцій за вибраними фільтрами")
            TransactionList(rows, onOpenTransaction)
        }
    }
    if (showWallet) WalletPickerSheetWithClear(wallets, f.walletId, onSelect = { vm.update { copy(walletId = it) }; showWallet = false }, onDismiss = { showWallet = false })
    if (showCategory) CategoryPickerSheet(
        categories, kind = TxKind.EXPENSE, selectedId = f.categoryId,
        onSelect = { c: CategoryEntity? -> vm.update { copy(categoryId = c?.id) }; showCategory = false }, onDismiss = { showCategory = false },
    )
}

@Composable
private fun WalletPickerSheetWithClear(wallets: List<WalletEntity>, selected: Long?, onSelect: (Long?) -> Unit, onDismiss: () -> Unit) {
    val all = listOf(WalletEntity(id = -1, name = "Усі гаманці")) + wallets
    WalletPickerSheet(all, selected ?: -1, onSelect = { onSelect(if (it.id == -1L) null else it.id) }, onDismiss = onDismiss)
}

@Composable
fun TransactionList(rows: List<TransactionRow>, onOpen: (Long) -> Unit, contentPadding: PaddingValues = PaddingValues(bottom = 96.dp)) {
    val grouped = rows.groupBy { Dates.toLocalDate(it.timestamp) }
    LazyColumn(contentPadding = contentPadding) {
        grouped.forEach { (day, list) ->
            item(key = "h$day") {
                val dayExpense = list.filter { it.kind == TxKind.EXPENSE }.sumOf { it.amountMinor }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(16.dp, 6.dp),
                ) {
                    Text(Dates.formatDay(list.first().timestamp), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (dayExpense > 0) Text("−${Money.format(dayExpense)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.id }) { row -> TransactionRowItem(row, onClick = { onOpen(row.id) }) }
        }
    }
}

@Composable
fun TransactionRowItem(row: TransactionRow, onClick: () -> Unit) {
    val title = when (row.kind) {
        TxKind.TRANSFER -> "Переказ"
        else -> row.merchant?.takeIf { it.isNotBlank() } ?: row.categoryName ?: row.note ?: "Без назви"
    }
    val needsCategory = row.categoryId == null && row.kind != TxKind.TRANSFER
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (needsCategory) {
            CategoryBadge("category", 0xFFF9A825)
        } else {
            CategoryBadge(if (row.kind == TxKind.TRANSFER) "wallet" else row.categoryIcon, if (row.kind == TxKind.TRANSFER) 0xFF1565C0 else row.categoryColor)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        "Повернення".takeIf { Refunds.isRefund(row.kind, row.amountMinor) },
                        row.receivedMinor?.let { "→ ${Money.format(it, row.receivedCurrency ?: row.currency)}" },
                        if (needsCategory) "Без категорії" else row.categoryName.takeIf { row.merchant != null && row.kind != TxKind.TRANSFER },
                        row.walletName,
                        row.cardLast4?.let { "•••• $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (needsCategory) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                when (row.source) {
                    TxSource.NOTIFICATION -> { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.NotificationsActive, "Зі сповіщення", Modifier.width(14.dp), tint = MaterialTheme.colorScheme.primary) }
                    TxSource.BANK_API -> { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.AccountBalance, "З банку", Modifier.width(14.dp), tint = MaterialTheme.colorScheme.primary) }
                    TxSource.MANUAL -> Unit
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(row.amountMinor, row.currency, row.kind)
            Text(Dates.formatTime(row.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
