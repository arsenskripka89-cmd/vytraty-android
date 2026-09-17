@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package ua.vytraty.app.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.vytraty.app.data.db.BudgetEntity
import ua.vytraty.app.data.db.CategoryEntity
import ua.vytraty.app.data.db.PlannedPaymentEntity
import ua.vytraty.app.data.db.Recurrence
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.CategoryPickerSheet
import ua.vytraty.app.ui.components.DatePickerField
import ua.vytraty.app.ui.components.EmptyState
import ua.vytraty.app.ui.components.PickerField
import ua.vytraty.app.ui.components.WalletPickerSheet
import ua.vytraty.app.ui.components.appViewModel
import ua.vytraty.app.ui.overview.BudgetProgress
import ua.vytraty.app.ui.overview.BudgetRow
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import java.time.YearMonth

val recurrenceLabels = mapOf(
    Recurrence.NONE to "Одноразово", Recurrence.WEEKLY to "Щотижня", Recurrence.MONTHLY to "Щомісяця", Recurrence.YEARLY to "Щороку",
)

class PlansViewModel(private val c: AppContainer) : ViewModel() {
    private val db = c.db
    private val range = Dates.monthRange(YearMonth.now())
    val planned = db.plannedPaymentDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wallets = db.walletDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = combine(db.budgetDao().observeAll(), db.categoryDao().observeAll()) { bs, cats -> bs to cats }
        .flatMapLatest { (bs, cats) ->
            if (bs.isEmpty()) flowOf(emptyList()) else combine(
                bs.map { b ->
                    db.transactionDao().observeSumExpenses(range.first, range.last, b.categoryId).map { spent ->
                        BudgetProgress(b, spent ?: 0L, b.categoryId?.let { id -> cats.firstOrNull { it.id == id }?.name } ?: "Усі витрати")
                    }
                },
            ) { it.toList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun recordNow(id: Long) = viewModelScope.launch { c.plannedPayments.recordNow(id) }
    fun skip(id: Long) = viewModelScope.launch { c.plannedPayments.skip(id) }
}

@Composable
fun PlansScreen(onEditPlanned: (Long) -> Unit, onEditBudget: (Long) -> Unit) {
    val vm = appViewModel { PlansViewModel(it) }
    val planned by vm.planned.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }

    AppScaffold(
        title = "Плани",
        fab = { FloatingActionButton(onClick = { if (tab == 0) onEditPlanned(0) else onEditBudget(0) }) { Icon(Icons.Filled.Add, "Додати") } },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Планові платежі") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Бюджети") })
            }
            if (tab == 0) {
                if (planned.isEmpty()) EmptyState("Додайте регулярні платежі: оренда, підписки, комуналка. Застосунок нагадає та запише їх одним тапом.")
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    items(planned, key = { it.id }) { p ->
                        val cat = categories.firstOrNull { it.id == p.categoryId }
                        val overdue = p.active && p.nextDueAt < System.currentTimeMillis()
                        Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp).clickable { onEditPlanned(p.id) }) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge(cat?.icon, cat?.color)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, fontWeight = FontWeight.Medium)
                                    Text(
                                        listOfNotNull(
                                            if (p.active) Dates.formatDate(p.nextDueAt) else "Вимкнено",
                                            recurrenceLabels[p.recurrence],
                                            wallets.firstOrNull { it.id == p.walletId }?.name,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (overdue) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(Money.format(p.amountMinor, p.currency), color = if (p.kind == TxKind.EXPENSE) ExpenseRed else IncomeGreen, fontWeight = FontWeight.SemiBold)
                            }
                            if (p.active) Row(Modifier.padding(horizontal = 8.dp)) {
                                TextButton(onClick = { vm.recordNow(p.id) }) { Text("Записати зараз") }
                                TextButton(onClick = { vm.skip(p.id) }) { Text("Пропустити") }
                            }
                        }
                    }
                }
            } else {
                if (budgets.isEmpty()) EmptyState("Встановіть місячний ліміт на категорію або на всі витрати. Ви отримаєте сповіщення на 80 % і 100 %.")
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                    items(budgets, key = { it.budget.id }) { bp ->
                        Card(Modifier.fillMaxWidth().padding(16.dp, 6.dp)) {
                            BudgetRow(bp, Modifier.padding(12.dp), onClick = { onEditBudget(bp.budget.id) })
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Planned payment edit ----------------

data class PlannedForm(
    val id: Long = 0, val title: String = "", val amount: String = "", val kind: TxKind = TxKind.EXPENSE,
    val categoryId: Long? = null, val walletId: Long? = null, val currency: String = "UAH",
    val nextDueAt: Long = System.currentTimeMillis(), val recurrence: Recurrence = Recurrence.MONTHLY,
    val remindDaysBefore: String = "1", val active: Boolean = true, val note: String = "", val error: String? = null,
)

class PlannedEditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val form = MutableStateFlow(PlannedForm())
    val wallets = c.db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = c.db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (id > 0) c.db.plannedPaymentDao().byId(id)?.let { p ->
                form.value = PlannedForm(
                    p.id, p.title, Money.minorToInput(p.amountMinor), p.kind, p.categoryId, p.walletId, p.currency,
                    p.nextDueAt, p.recurrence, p.remindDaysBefore.toString(), p.active, p.note.orEmpty(),
                )
            } else {
                val w = c.db.walletDao().defaultWallet() ?: c.db.walletDao().firstActive()
                form.update { it.copy(walletId = w?.id, currency = w?.currency ?: "UAH") }
            }
        }
    }

    fun update(block: PlannedForm.() -> PlannedForm) = form.update(block)

    fun save() = viewModelScope.launch {
        val f = form.value
        val amount = Money.parseToMinor(f.amount)
        if (f.title.isBlank()) { form.update { it.copy(error = "Вкажіть назву") }; return@launch }
        if (amount == null || amount <= 0) { form.update { it.copy(error = "Вкажіть суму") }; return@launch }
        val walletId = f.walletId ?: run { form.update { it.copy(error = "Оберіть гаманець") }; return@launch }
        c.plannedPayments.save(
            PlannedPaymentEntity(
                id = f.id, title = f.title.trim(), amountMinor = amount, currency = f.currency, categoryId = f.categoryId,
                walletId = walletId, kind = f.kind, nextDueAt = f.nextDueAt, recurrence = f.recurrence,
                remindDaysBefore = f.remindDaysBefore.toIntOrNull()?.coerceIn(0, 30) ?: 1, active = f.active, note = f.note.ifBlank { null },
            ),
        )
        done.value = true
    }

    fun delete() = viewModelScope.launch {
        c.db.plannedPaymentDao().byId(id)?.let { c.plannedPayments.delete(it) }
        done.value = true
    }
}

@Composable
fun PlannedPaymentEditScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "planned$id") { PlannedEditViewModel(it, id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var showWallet by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showRecurrence by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) onBack() }
    val wallet = wallets.firstOrNull { it.id == f.walletId }
    val category = categories.firstOrNull { it.id == f.categoryId }

    AppScaffold(
        title = if (id == 0L) "Новий плановий платіж" else "Плановий платіж", onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Видалити") } },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = f.kind == TxKind.EXPENSE, onClick = { vm.update { copy(kind = TxKind.EXPENSE, categoryId = null) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Витрата") }
                SegmentedButton(selected = f.kind == TxKind.INCOME, onClick = { vm.update { copy(kind = TxKind.INCOME, categoryId = null) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Дохід") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(f.title, { v -> vm.update { copy(title = v, error = null) } }, label = { Text("Назва (напр. Оренда)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                f.amount, { v -> vm.update { copy(amount = v, error = null) } }, label = { Text("Сума") },
                suffix = { Text(Money.symbol(f.currency)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PickerField("Гаманець", wallet?.name ?: "Оберіть", onClick = { showWallet = true })
            Spacer(Modifier.height(12.dp))
            PickerField("Категорія", category?.name ?: "Без категорії", onClick = { showCategory = true }, leading = { CategoryBadge(category?.icon, category?.color, 24) })
            Spacer(Modifier.height(12.dp))
            DatePickerField("Наступна дата платежу", f.nextDueAt, onChange = { t -> vm.update { copy(nextDueAt = t) } })
            Spacer(Modifier.height(12.dp))
            Column {
                PickerField("Повторення", recurrenceLabels[f.recurrence] ?: "", onClick = { showRecurrence = true })
                DropdownMenu(expanded = showRecurrence, onDismissRequest = { showRecurrence = false }) {
                    Recurrence.entries.forEach { r ->
                        DropdownMenuItem(text = { Text(recurrenceLabels[r] ?: r.name) }, onClick = { vm.update { copy(recurrence = r) }; showRecurrence = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                f.remindDaysBefore, { v -> vm.update { copy(remindDaysBefore = v.filter { it.isDigit() }.take(2)) } },
                label = { Text("Нагадати за N днів (0 = у день платежу)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Активний", Modifier.weight(1f))
                Switch(checked = f.active, onCheckedChange = { v -> vm.update { copy(active = v) } })
            }
            OutlinedTextField(f.note, { v -> vm.update { copy(note = v) } }, label = { Text("Нотатка") }, modifier = Modifier.fillMaxWidth())
            f.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
            Spacer(Modifier.height(80.dp))
        }
    }
    if (showWallet) WalletPickerSheet(wallets, f.walletId, onSelect = { w -> vm.update { copy(walletId = w.id, currency = w.currency) }; showWallet = false }, onDismiss = { showWallet = false })
    if (showCategory) CategoryPickerSheet(categories, f.kind, f.categoryId, onSelect = { c: CategoryEntity? -> vm.update { copy(categoryId = c?.id) }; showCategory = false }, onDismiss = { showCategory = false })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Видалити плановий платіж?") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Видалити") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
    )
}

// ---------------- Budget edit ----------------

data class BudgetForm(val id: Long = 0, val categoryId: Long? = null, val limit: String = "", val currency: String = "UAH", val error: String? = null)

class BudgetEditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val form = MutableStateFlow(BudgetForm())
    val categories = c.db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (id > 0) c.db.budgetDao().byId(id)?.let { b -> form.value = BudgetForm(b.id, b.categoryId, Money.minorToInput(b.limitMinor), b.currency) }
            else form.update { it.copy(currency = c.settings.current().mainCurrency) }
        }
    }

    fun update(block: BudgetForm.() -> BudgetForm) = form.update(block)

    fun save() = viewModelScope.launch {
        val f = form.value
        val limit = Money.parseToMinor(f.limit)
        if (limit == null || limit <= 0) { form.update { it.copy(error = "Вкажіть ліміт") }; return@launch }
        val b = BudgetEntity(id = f.id, categoryId = f.categoryId, limitMinor = limit, currency = f.currency)
        if (f.id == 0L) c.db.budgetDao().insert(b) else c.db.budgetDao().update(b)
        done.value = true
    }

    fun delete() = viewModelScope.launch {
        c.db.budgetDao().byId(id)?.let { c.db.budgetDao().delete(it) }
        done.value = true
    }
}

@Composable
fun BudgetEditScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "budget$id") { BudgetEditViewModel(it, id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var showCategory by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) onBack() }
    val category = categories.firstOrNull { it.id == f.categoryId }

    AppScaffold(
        title = if (id == 0L) "Новий бюджет" else "Бюджет", onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { vm.delete() }) { Icon(Icons.Filled.Delete, "Видалити") } },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Місячний ліміт витрат. Сповіщення надходять при 80 % і 100 %.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            PickerField("Категорія", category?.name ?: "Усі витрати", onClick = { showCategory = true }, leading = { CategoryBadge(category?.icon ?: "wallet", category?.color, 24) })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                f.limit, { v -> vm.update { copy(limit = v, error = null) } }, label = { Text("Ліміт на місяць") },
                suffix = { Text(Money.symbol(f.currency)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            f.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
        }
    }
    if (showCategory) CategoryPickerSheet(categories, TxKind.EXPENSE, f.categoryId, onSelect = { c: CategoryEntity? -> vm.update { copy(categoryId = c?.id) }; showCategory = false }, onDismiss = { showCategory = false })
}
