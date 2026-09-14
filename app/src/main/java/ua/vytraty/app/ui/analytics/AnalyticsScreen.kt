@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ua.vytraty.app.data.db.CategorySum
import ua.vytraty.app.data.db.DaySum
import ua.vytraty.app.data.db.MonthSum
import ua.vytraty.app.data.db.TxKind
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
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import java.time.LocalDate
import java.time.YearMonth

data class AnalyticsFilters(val month: YearMonth = YearMonth.now(), val walletId: Long? = null, val kind: TxKind = TxKind.EXPENSE)

data class AnalyticsState(
    val byCategory: List<CategorySum> = emptyList(),
    val months: List<MonthSum> = emptyList(),
    val days: List<DaySum> = emptyList(),
)

class AnalyticsViewModel(c: AppContainer) : ViewModel() {
    private val db = c.db
    val filters = MutableStateFlow(AnalyticsFilters())
    val wallets = db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state = filters.flatMapLatest { f ->
        val r = Dates.monthRange(f.month)
        val sixMonths = Dates.monthRange(f.month.minusMonths(5)).first..r.last
        combine(
            db.transactionDao().observeSumsByCategory(f.kind, r.first, r.last, f.walletId),
            db.transactionDao().observeMonthSums(sixMonths.first, sixMonths.last, f.walletId),
            db.transactionDao().observeDaySums(r.first, r.last, f.walletId),
        ) { cats, months, days -> AnalyticsState(cats, months, days) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    fun update(block: AnalyticsFilters.() -> AnalyticsFilters) = filters.update(block)
}

@Composable
fun AnalyticsScreen() {
    val vm = appViewModel { AnalyticsViewModel(it) }
    val f by vm.filters.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var showWallet by remember { mutableStateOf(false) }
    val currency = wallets.firstOrNull { it.id == f.walletId }?.currency ?: "UAH"
    val outlineColor = MaterialTheme.colorScheme.outline

    AppScaffold(title = "Аналітика") { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.update { copy(month = month.minusMonths(1)) } }) { Icon(Icons.Filled.ChevronLeft, null) }
                    Text(Dates.formatMonth(f.month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    IconButton(onClick = { vm.update { copy(month = month.plusMonths(1)) } }) { Icon(Icons.Filled.ChevronRight, null) }
                }
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                        SegmentedButton(selected = f.kind == TxKind.EXPENSE, onClick = { vm.update { copy(kind = TxKind.EXPENSE) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Витрати") }
                        SegmentedButton(selected = f.kind == TxKind.INCOME, onClick = { vm.update { copy(kind = TxKind.INCOME) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Доходи") }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = f.walletId != null, onClick = { showWallet = true }, label = { Text(wallets.firstOrNull { it.id == f.walletId }?.name ?: "Усі гаманці") })
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
            }
            items(s.byCategory, key = { it.categoryId ?: -1 }) { cs ->
                val cat = categories.firstOrNull { it.id == cs.categoryId }
                val pct = if (total > 0) cs.total * 100 / total else 0
                Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(cat?.icon, cat?.color, 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(cat?.name ?: "Без категорії", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("$pct %", style = MaterialTheme.typography.bodySmall)
                        }
                        LinearProgressIndicator(
                            progress = { if (total > 0) cs.total.toFloat() / total else 0f },
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
        WalletPickerSheet(all, f.walletId ?: -1, onSelect = { w -> vm.update { copy(walletId = if (w.id == -1L) null else w.id) }; showWallet = false }, onDismiss = { showWallet = false })
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
