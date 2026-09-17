@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ua.vytraty.app.ui.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.vytraty.app.data.db.CaptureRuleEntity
import ua.vytraty.app.data.db.CategoryEntity
import ua.vytraty.app.data.db.LogStatus
import ua.vytraty.app.data.db.NotificationLogEntity
import ua.vytraty.app.data.db.RuleType
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.parser.BankSource
import ua.vytraty.app.domain.parser.RuleMatcher
import ua.vytraty.app.domain.parser.TextCues
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.BankLogo
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.CategoryPickerSheet
import ua.vytraty.app.ui.components.EmptyState
import ua.vytraty.app.ui.components.PickerField
import ua.vytraty.app.ui.components.WalletIcon
import ua.vytraty.app.ui.components.WalletPickerSheet
import ua.vytraty.app.ui.components.appViewModel
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import ua.vytraty.app.ui.theme.WarnAmber

// ---------------- Notification store ----------------

class NotificationStoreViewModel(private val c: AppContainer) : ViewModel() {
    val entries = c.db.notificationLogDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow<String?>(null)

    fun clear() = viewModelScope.launch { c.db.notificationLogDao().clear() }

    fun applyRules() = viewModelScope.launch {
        message.value = "Оброблено сповіщень: ${c.recordPayment.reprocessPending()}"
    }
}

/** Every captured notification, sorted into classes: which ones the rules covered and which did not. */
@Composable
fun NotificationStoreScreen(onBack: () -> Unit, onOpenRule: (Long) -> Unit) {
    val vm = appViewModel { NotificationStoreViewModel(it) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<String?>(null) }
    val shown = entries.filter { filter == null || it.status == filter }

    AppScaffold(
        title = "Сховище сповіщень",
        onBack = onBack,
        actions = { IconButton(onClick = { vm.clear() }) { Icon(Icons.Filled.DeleteSweep, "Очистити") } },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(filter == null, { filter = null }, label = { Text("Усі (${entries.size})") })
                }
                items(LogStatus.all) { st ->
                    val n = entries.count { it.status == st }
                    FilterChip(filter == st, { filter = st }, label = { Text("${LogStatus.label(st)} ($n)") })
                }
            }
            TextButton(onClick = { vm.applyRules() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Застосувати правила до непройдених")
            }
            message?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary) }
            if (shown.isEmpty()) EmptyState("Тут з'являться всі сповіщення банків. Торкніться будь-якого, щоб зробити з нього правило.")
            LazyColumn {
                items(shown, key = { it.id }) { e -> LogRow(e, onClick = { onOpenRule(e.id) }) }
            }
        }
    }
}

@Composable
private fun LogRow(e: NotificationLogEntity, onClick: () -> Unit) {
    val bank = BankSource.byPackage(e.packageName)
    val color = when (e.status) {
        LogStatus.RECORDED -> IncomeGreen
        LogStatus.NO_CATEGORY -> WarnAmber
        LogStatus.NO_WALLET -> ExpenseRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        headlineContent = { Text(listOfNotNull(e.title, e.text).joinToString(" — ").ifBlank { "(порожньо)" }, maxLines = 3) },
        overlineContent = { Text("${bank?.displayName ?: e.packageName} · ${Dates.formatDateTime(e.postedAt)}") },
        supportingContent = { Text(LogStatus.label(e.status), color = color) },
        leadingContent = { BankLogo(bank, 32) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

// ---------------- Rules list ----------------

class CaptureRulesViewModel(private val c: AppContainer) : ViewModel() {
    val rules = c.db.captureRuleDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wallets = c.db.walletDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = c.db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { c.db.captureRuleDao().deleteById(id) }
}

@Composable
fun CaptureRulesScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = appViewModel { CaptureRulesViewModel(it) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    AppScaffold(
        title = "Правила розпізнавання",
        onBack = onBack,
        fab = { FloatingActionButton(onClick = { onEdit(0) }) { Icon(Icons.Filled.Add, "Додати правило") } },
    ) { padding ->
        if (rules.isEmpty()) {
            EmptyState(
                "Правило каже, якій картці чи категорії належить сповіщення. Створіть перше зі сховища сповіщень " +
                    "або кнопкою «+».",
                Modifier.padding(padding),
            )
        }
        LazyColumn(Modifier.padding(padding)) {
            items(rules, key = { it.id }) { r ->
                val target = when (r.type) {
                    RuleType.WALLET -> wallets.firstOrNull { it.id == r.targetId }?.name
                    RuleType.CATEGORY -> categories.firstOrNull { it.id == r.targetId }?.name
                }
                ListItem(
                    overlineContent = { Text(if (r.type == RuleType.WALLET) "Картка" else "Категорія") },
                    headlineContent = { Text(target ?: "Видалений об'єкт") },
                    supportingContent = {
                        Text(
                            "містить «${r.pattern}» · ${BankSource.entries.firstOrNull { it.name == r.bank }?.displayName ?: "будь-який застосунок"}" +
                                " · спрацювало ${r.hits}",
                        )
                    },
                    trailingContent = { IconButton(onClick = { vm.delete(r.id) }) { Icon(Icons.Filled.Delete, "Видалити") } },
                    modifier = Modifier.clickable { onEdit(r.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

// ---------------- Rule editor ----------------

data class RuleForm(
    val id: Long = 0,
    val type: RuleType = RuleType.WALLET,
    val targetId: Long? = null,
    val bank: String? = null,
    val pattern: String = "",
    val kind: TxKind = TxKind.EXPENSE,
    val loaded: Boolean = false,
    val error: String? = null,
)

class RuleEditViewModel(
    private val c: AppContainer,
    private val id: Long,
    private val logId: Long,
    presetWalletId: Long,
) : ViewModel() {
    val form = MutableStateFlow(
        RuleForm(targetId = presetWalletId.takeIf { it > 0 }, loaded = id == 0L && logId == 0L),
    )
    val log = MutableStateFlow<NotificationLogEntity?>(null)
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val wallets = c.db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = c.db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val done = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            if (id > 0) c.db.captureRuleDao().byId(id)?.let { r ->
                form.value = RuleForm(r.id, r.type, r.targetId, r.bank, r.pattern, loaded = true)
            }
            if (logId > 0) c.db.notificationLogDao().byId(logId)?.let { e ->
                log.value = e
                val parsed = c.parserRegistry.parse(e.packageName, e.title, e.text)
                val body = listOfNotNull(e.title, e.text).joinToString(" ")
                suggestions.value = listOfNotNull(
                    parsed?.cardLast4?.let { "•••• $it" },
                    TextCues.cardLabel(body),
                    parsed?.merchant,
                ).distinct() + wordsOf(body)
                form.update {
                    it.copy(
                        bank = it.bank ?: BankSource.byPackage(e.packageName)?.name,
                        pattern = it.pattern.ifBlank { suggestions.value.firstOrNull().orEmpty() },
                        kind = parsed?.kind ?: TxKind.EXPENSE,
                        loaded = true,
                    )
                }
            }
        }
    }

    /** Distinct words of the notification, offered as identifier candidates. */
    private fun wordsOf(body: String) = body
        .split(Regex("""[\s\n·•,;]+"""))
        .map { it.trim('.', ':', '(', ')', '"') }
        .filter { it.length in 3..24 && it.any(Char::isLetterOrDigit) }
        .distinct()
        .take(14)

    fun update(block: RuleForm.() -> RuleForm) = form.update(block)

    /** True when the currently typed rule would match the notification being looked at. */
    fun matchesLog(f: RuleForm): Boolean {
        val e = log.value ?: return false
        val probe = CaptureRuleEntity(type = f.type, targetId = f.targetId ?: 0, bank = f.bank, pattern = f.pattern)
        return RuleMatcher.matches(probe, e.packageName, e.title, e.text)
    }

    fun save() = viewModelScope.launch {
        val f = form.value
        val targetId = f.targetId
        if (targetId == null) { form.update { it.copy(error = "Оберіть картку або категорію") }; return@launch }
        if (f.pattern.isBlank()) { form.update { it.copy(error = "Вкажіть ідентифікатор") }; return@launch }
        val dao = c.db.captureRuleDao()
        val rule = CaptureRuleEntity(id = f.id, type = f.type, targetId = targetId, bank = f.bank, pattern = f.pattern.trim())
        if (f.id == 0L) dao.insert(rule) else dao.update(rule)
        val applied = c.recordPayment.reprocessPending()
        done.value = if (applied > 0) "Правило збережено, оброблено сповіщень: $applied" else "Правило збережено"
    }

    fun delete() = viewModelScope.launch {
        if (id > 0) c.db.captureRuleDao().deleteById(id)
        done.value = "Правило видалено"
    }
}

@Composable
fun RuleEditScreen(id: Long, logId: Long, walletId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "rule$id-$logId") { RuleEditViewModel(it, id, logId, walletId) }
    val f by vm.form.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var showWallet by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var showBank by remember { mutableStateOf(false) }

    LaunchedEffect(done) { if (done != null) onBack() }

    val wallet = wallets.firstOrNull { it.id == f.targetId }
    val category = categories.firstOrNull { it.id == f.targetId }

    AppScaffold(
        title = if (id == 0L) "Нове правило" else "Правило",
        onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { vm.delete() }) { Icon(Icons.Filled.Delete, "Видалити") } },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            log?.let { e ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "${BankSource.byPackage(e.packageName)?.displayName ?: e.packageName} · ${Dates.formatDateTime(e.postedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        e.title?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                        e.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("Що визначає правило", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(RuleType.WALLET to "Картка", RuleType.CATEGORY to "Категорія").forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = f.type == t,
                        onClick = { vm.update { copy(type = t, targetId = null) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (f.type == RuleType.WALLET) {
                PickerField(
                    "Картка / гаманець", wallet?.name ?: "Оберіть", onClick = { showWallet = true },
                    leading = wallet?.let { { WalletIcon(it.type, it.color, 24, it.bankCode) } },
                )
            } else {
                PickerField(
                    "Категорія", category?.name ?: "Оберіть", onClick = { showCategory = true },
                    leading = { CategoryBadge(category?.icon ?: "category", category?.color ?: 0xFF9E9E9E, 24) },
                )
            }
            Spacer(Modifier.height(12.dp))

            PickerField(
                "Застосунок-джерело",
                BankSource.entries.firstOrNull { it.name == f.bank }?.displayName ?: "Будь-який застосунок",
                onClick = { showBank = true },
            )
            DropdownMenu(expanded = showBank, onDismissRequest = { showBank = false }) {
                DropdownMenuItem(text = { Text("Будь-який застосунок") }, onClick = { vm.update { copy(bank = null) }; showBank = false })
                BankSource.entries.filter { it.packages.isNotEmpty() }.forEach { b ->
                    DropdownMenuItem(text = { Text(b.displayName) }, onClick = { vm.update { copy(bank = b.name) }; showBank = false })
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                f.pattern,
                { v -> vm.update { copy(pattern = v, error = null) } },
                label = { Text("Ідентифікатор: сповіщення має містити цей текст") },
                supportingText = { Text("Напр. «•••• 4498», «Privat EUR», «Rayf UAH». Регістр не має значення.") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("З цього сповіщення:", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { sug ->
                        AssistChip(onClick = { vm.update { copy(pattern = sug, error = null) } }, label = { Text(sug) })
                    }
                }
            }
            if (log != null) {
                Spacer(Modifier.height(8.dp))
                val ok = vm.matchesLog(f)
                Text(
                    if (ok) "✓ Це сповіщення підходить під правило" else "✗ Це сповіщення не містить такого тексту",
                    color = if (ok) IncomeGreen else ExpenseRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            f.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth(), enabled = f.loaded) { Text("Зберегти правило") }
            log?.transactionId?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Це сповіщення вже записане як операція №$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    if (showWallet) WalletPickerSheet(wallets, f.targetId, onSelect = { w -> vm.update { copy(targetId = w.id) }; showWallet = false }, onDismiss = { showWallet = false })
    if (showCategory) CategoryPickerSheet(
        categories, kind = f.kind, selectedId = f.targetId, allowNone = false,
        onSelect = { cat: CategoryEntity? -> vm.update { copy(targetId = cat?.id) }; showCategory = false },
        onDismiss = { showCategory = false },
    )
}

/** Rules of one wallet, shown inside the wallet editor. */
@Composable
fun WalletRulesSection(rules: List<CaptureRuleEntity>, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    Text("Правила розпізнавання", style = MaterialTheme.typography.labelLarge)
    if (rules.isEmpty()) {
        Text(
            "Поки що немає. Сповіщення потрапляє в цей гаманець за останніми 4 цифрами картки або за правилом.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    rules.forEach { r ->
        Row(Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("містить «${r.pattern}»")
                Text(
                    BankSource.entries.firstOrNull { it.name == r.bank }?.displayName ?: "будь-який застосунок",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${r.hits}×", style = MaterialTheme.typography.labelSmall)
        }
    }
    OutlinedButton(onClick = onAdd) { Text("Додати правило") }
}

