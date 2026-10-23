@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.vytraty.app.data.db.CategorySum
import ua.vytraty.app.data.db.DaySum
import ua.vytraty.app.data.db.MonthSum
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.rates.ExchangeRates
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.EmptyState
import ua.vytraty.app.ui.components.SectionHeader
import ua.vytraty.app.ui.components.WalletPickerSheet
import ua.vytraty.app.ui.components.appViewModel
import ua.vytraty.app.ui.components.charts.BarChart
import ua.vytraty.app.ui.components.charts.BarGroup
import ua.vytraty.app.ui.components.charts.DonutChart
import ua.vytraty.app.ui.components.charts.LineChart
import ua.vytraty.app.ui.components.charts.LinePoint
import ua.vytraty.app.ui.components.charts.PieSlice
import ua.vytraty.app.ui.components.toColor
import ua.vytraty.app.ui.history.HistoryFilters
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import ua.vytraty.app.ui.theme.WarnAmber
import java.time.LocalDate
import java.time.YearMonth

data class AnalyticsFilters(
    val month: YearMonth = YearMonth.now(),
    val walletId: Long? = null,
    val kind: TxKind = TxKind.EXPENSE,
    /** Currency to show everything in; null = the main currency from the settings. */
    val currency: String? = null,
)

/** Totals converted into [currency], one row per category / month / day. */
data class AnalyticsState(
    val currency: String = "UAH",
    val byCategory: List<CategorySum> = emptyList(),
    val months: List<MonthSum> = emptyList(),
    val days: List<DaySum> = emptyList(),
    /** Other currencies that were converted into [currency]. */
    val converted: Set<String> = emptySet(),
    /** Currencies without a rate: left out of every total. */
    val missing: Set<String> = emptySet(),
    val rates: Map<String, Double> = mapOf("UAH" to 1.0),
    val ratesUpdated: Long = 0,
)

/** Adds up per-currency sums from the database as one currency, with the cached exchange rates. */
fun convertAnalytics(
    currency: String,
    rates: Map<String, Double>,
    byCategory: List<CategorySum>,
    months: List<MonthSum>,
    days: List<DaySum>,
): AnalyticsState {
    val missing = mutableSetOf<String>()
    fun sum(values: List<Pair<Long, String>>): Long = ExchangeRates.sum(values, currency, rates).also { missing += it.missing }.minor
    val categories = byCategory.groupBy { it.categoryId }.mapNotNull { (id, parts) ->
        val total = sum(parts.map { it.total to it.currency })
        // spending only in currencies without a rate has nothing to show
        if (parts.all { it.currency.uppercase() in missing }) null else CategorySum(id, currency, total)
    }.sortedByDescending { it.total }
    val monthTotals = months.groupBy { it.monthKey }.map { (key, parts) ->
        MonthSum(key, currency, sum(parts.map { it.expense to it.currency }), sum(parts.map { it.income to it.currency }))
    }
    val dayTotals = days.groupBy { it.dayKey }.map { (key, parts) ->
        DaySum(key, currency, sum(parts.map { it.expense to it.currency }), sum(parts.map { it.income to it.currency }))
    }
    val seen = (byCategory.map { it.currency } + months.map { it.currency } + days.map { it.currency }).map { it.uppercase() }.toSet()
    return AnalyticsState(
        currency = currency,
        byCategory = categories,
        months = monthTotals,
        days = dayTotals,
        converted = seen - currency.uppercase() - missing,
        missing = missing,
        rates = rates,
    )
}

class AnalyticsViewModel(c: AppContainer) : ViewModel() {
    private val db = c.db
    val filters = MutableStateFlow(AnalyticsFilters())
    val wallets = db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val money = c.settings.settings.map { Triple(it.mainCurrency, it.ratesRaw, it.ratesUpdated) }.distinctUntilChanged()

    init {
        viewModelScope.launch { c.exchangeRates.refreshIfStale() }
    }

    val state = combine(filters, money) { f, m -> f to m }.flatMapLatest { (f, m) ->
        val (mainCurrency, ratesRaw, ratesUpdated) = m
        val r = Dates.monthRange(f.month)
        val sixMonths = Dates.monthRange(f.month.minusMonths(5)).first..r.last
        combine(
            db.transactionDao().observeSumsByCategory(f.kind, r.first, r.last, f.walletId),
            db.transactionDao().observeMonthSums(sixMonths.first, sixMonths.last, f.walletId),
            db.transactionDao().observeDaySums(r.first, r.last, f.walletId),
        ) { cats, months, days ->
            convertAnalytics(f.currency ?: mainCurrency, ExchangeRates.decode(ratesRaw), cats, months, days)
                .copy(ratesUpdated = ratesUpdated)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun update(block: AnalyticsFilters.() -> AnalyticsFilters) = filters.update(block)
}

/** [onOpenHistory] shows the operations behind a category line, with the same month, wallet and currency. */
@Composable
fun AnalyticsScreen(onOpenHistory: (HistoryFilters) -> Unit = {}) {
    val vm = appViewModel { AnalyticsViewModel(it) }
    val f by vm.filters.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showWallet by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    val currency = s.currency
    val outlineColor = MaterialTheme.colorScheme.outline

    AppScaffold(title = "Аналітика") { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.update { copy(month = month.minusMonths(1)) } }) { Icon(Icons.Filled.ChevronLeft, null) }
                    Text(Dates.formatMonth(f.month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    IconButton(onClick = { vm.update { copy(month = month.plusMonths(1)) } }) { Icon(Icons.Filled.ChevronRight, null) }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(selected = f.kind == TxKind.EXPENSE, onClick = { vm.update { copy(kind = TxKind.EXPENSE) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Витрати") }
                    SegmentedButton(selected = f.kind == TxKind.INCOME, onClick = { vm.update { copy(kind = TxKind.INCOME) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Доходи") }
                }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = f.walletId != null, onClick = { showWallet = true }, label = { Text(wallets.firstOrNull { it.id == f.walletId }?.name ?: "Усі гаманці") })
                    // Every amount is shown in this one currency; the others are converted at the cached rate.
                    Box {
                        FilterChip(
                            selected = f.currency != null, onClick = { showCurrency = true },
                            label = { Text("${Money.symbol(currency)} $currency") },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        )
                        DropdownMenu(expanded = showCurrency, onDismissRequest = { showCurrency = false }) {
                            Money.currencies.filter { it == "UAH" || s.rates.containsKey(it) }.forEach { cur ->
                                DropdownMenuItem(
                                    text = { Text("$cur  ${Money.symbol(cur)}") },
                                    onClick = { vm.update { copy(currency = cur) }; showCurrency = false },
                                )
                            }
                        }
                    }
                }
            }
            val total = s.byCategory.sumOf { it.total }
            val slices = s.byCategory.map { cs ->
                val cat = categories.firstOrNull { it.id == cs.categoryId }
                PieSlice(cat?.name ?: "Без категорії", cs.total, cat?.color?.toColor() ?: outlineColor)
            }
            item {
                if (slices.isEmpty()) EmptyState("Немає даних за цей місяць")
                else DonutChart(slices, if (f.kind == TxKind.EXPENSE) "Витрати" else "Доходи", Money.format(total, currency))
                if (s.converted.isNotEmpty()) Text(
                    "${s.converted.sorted().joinToString(", ")} перераховано в ${Money.symbol(currency)} за курсом на ${Dates.formatDateTime(s.ratesUpdated)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = TextAlign.Center,
                )
                if (s.missing.isNotEmpty()) Text(
                    "Без курсу, не враховано: ${s.missing.sorted().joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall, color = WarnAmber,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = TextAlign.Center,
                )
            }
            items(s.byCategory, key = { it.categoryId ?: -1 }) { cs ->
                val cat = categories.firstOrNull { it.id == cs.categoryId }
                val pct = if (total > 0) cs.total * 100 / total else 0
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            onOpenHistory(
                                HistoryFilters(
                                    month = f.month, walletId = f.walletId, categoryId = cs.categoryId,
                                    onlyUncategorized = cs.categoryId == null, kind = f.kind, currency = currency,
                                ),
                            )
                        }
                        .padding(16.dp, 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CategoryBadge(cat?.icon, cat?.color, 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(cat?.name ?: "Без категорії", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("$pct %", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { if (total > 0) (cs.total.toFloat() / total).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = cat?.color?.toColor() ?: MaterialTheme.colorScheme.outline,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Money.format(cs.total, currency), fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                SectionHeader("Останні 6 місяців")
                val months = (5 downTo 0).map { f.month.minusMonths(it.toLong()) }
                val groups = months.map { m ->
                    val ms = s.months.firstOrNull { it.monthKey == Dates.monthKey(m) }
                    BarGroup(m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("uk")), listOf(ms?.expense ?: 0, ms?.income ?: 0))
                }
                Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                    BarChart(groups, listOf(ExpenseRed, IncomeGreen))
                    Row(Modifier.padding(12.dp, 0.dp, 12.dp, 12.dp)) {
                        Legend(ExpenseRed, "Витрати"); Spacer(Modifier.width(16.dp)); Legend(IncomeGreen, "Доходи")
                    }
                }
            }
            item {
                SectionHeader("Динаміка за місяць (доходи − витрати, накопичено)")
                val daysInMonth = f.month.lengthOfMonth()
                var acc = 0L
                val points = (1..daysInMonth).map { d ->
                    val date = LocalDate.of(f.month.year, f.month.monthValue, d)
                    val key = "%04d-%02d-%02d".format(date.year, date.monthValue, d)
                    val ds = s.days.firstOrNull { it.dayKey == key }
                    acc += (ds?.income ?: 0) - (ds?.expense ?: 0)
                    LinePoint(d.toString(), acc)
                }
                Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) { LineChart(points, MaterialTheme.colorScheme.primary) }
            }
        }
    }
    if (showWallet) {
        val all = listOf(ua.vytraty.app.data.db.WalletEntity(id = -1, name = "Усі гаманці")) + wallets
        WalletPickerSheet(
            all, f.walletId ?: -1,
            // one wallet is shown in its own currency, all of them in the main one
            onSelect = { w -> vm.update { if (w.id == -1L) copy(walletId = null, currency = null) else copy(walletId = w.id, currency = w.currency) }; showWallet = false },
            onDismiss = { showWallet = false },
        )
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ua.vytraty.app.ui.components.Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
