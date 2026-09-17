@file:OptIn(ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.transaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.vytraty.app.data.db.CategoryEntity
import ua.vytraty.app.data.db.TransactionEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.TxSource
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Money
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.CategoryPickerSheet
import ua.vytraty.app.ui.components.CurrencyPicker
import ua.vytraty.app.ui.components.DatePickerField
import ua.vytraty.app.ui.components.PickerField
import ua.vytraty.app.ui.components.WalletIcon
import ua.vytraty.app.ui.components.WalletPickerSheet
import ua.vytraty.app.ui.components.appViewModel
import ua.vytraty.app.ui.theme.WarnAmber

data class TxForm(
    val id: Long = 0,
    val kind: TxKind = TxKind.EXPENSE,
    val amount: String = "",
    val walletId: Long? = null,
    val toWalletId: Long? = null,
    val categoryId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: TxSource = TxSource.MANUAL,
    val cardLast4: String? = null,
    val externalId: String? = null,
    val notificationLogId: Long? = null,
    val currency: String? = null,
    val learnRule: Boolean = true,
    val loaded: Boolean = false,
    val error: String? = null,
)

sealed class TxEvent {
    data object Saved : TxEvent()
    data class AskApplyToOthers(val ids: List<Long>, val categoryId: Long, val merchant: String) : TxEvent()
}

class TransactionEditViewModel(private val c: AppContainer, private val id: Long, initialKind: String) : ViewModel() {
    private val db = c.db
    val form = MutableStateFlow(TxForm(kind = runCatching { TxKind.valueOf(initialKind) }.getOrDefault(TxKind.EXPENSE)))
    val wallets = db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val event = MutableStateFlow<TxEvent?>(null)

    init {
        viewModelScope.launch {
            if (id > 0) {
                val t = db.transactionDao().byId(id)
                if (t != null) {
                    form.value = TxForm(
                        id = t.id, kind = t.kind, amount = Money.minorToInput(t.amountMinor), walletId = t.walletId,
                        toWalletId = t.transferToWalletId, categoryId = t.categoryId, merchant = t.merchant.orEmpty(),
                        note = t.note.orEmpty(), timestamp = t.timestamp, source = t.source, cardLast4 = t.cardLast4,
                        externalId = t.externalId, notificationLogId = t.notificationLogId, currency = t.currency,
                        learnRule = !t.merchant.isNullOrBlank(), loaded = true,
                    )
                }
            } else {
                val w = db.walletDao().defaultWallet() ?: db.walletDao().firstActive()
                form.update { it.copy(walletId = w?.id, currency = w?.currency, loaded = true) }
            }
        }
    }

    fun update(block: TxForm.() -> TxForm) = form.update(block)

    fun save() {
        viewModelScope.launch {
            val f = form.value
            val amount = Money.parseToMinor(f.amount)
            if (amount == null || amount <= 0) { form.update { it.copy(error = "Вкажіть суму") }; return@launch }
            val walletId = f.walletId ?: run { form.update { it.copy(error = "Оберіть гаманець") }; return@launch }
            if (f.kind == TxKind.TRANSFER && (f.toWalletId == null || f.toWalletId == walletId)) {
                form.update { it.copy(error = "Оберіть інший гаманець-отримувач") }; return@launch
            }
            val wallet = db.walletDao().byId(walletId)
            val categoryId = if (f.kind == TxKind.TRANSFER) null else f.categoryId
            val entity = TransactionEntity(
                id = f.id, walletId = walletId, categoryId = categoryId, kind = f.kind, amountMinor = amount,
                currency = f.currency ?: wallet?.currency ?: "UAH", timestamp = f.timestamp,
                merchant = f.merchant.trim().ifBlank { null }, note = f.note.trim().ifBlank { null },
                source = f.source, cardLast4 = f.cardLast4, transferToWalletId = if (f.kind == TxKind.TRANSFER) f.toWalletId else null,
                externalId = f.externalId, notificationLogId = f.notificationLogId,
            )
            val savedId = if (f.id == 0L) db.transactionDao().insert(entity) else { db.transactionDao().update(entity); f.id }
            if (f.kind == TxKind.EXPENSE) c.budgetChecker.checkAfterExpense(categoryId)
            if (categoryId != null && f.learnRule && entity.merchant != null) {
                val outcome = c.assignCategory.assign(savedId, categoryId, learn = true)
                if (outcome.otherUncategorizedSameMerchant.isNotEmpty()) {
                    event.value = TxEvent.AskApplyToOthers(outcome.otherUncategorizedSameMerchant, categoryId, entity.merchant)
                    return@launch
                }
            }
            event.value = TxEvent.Saved
        }
    }

    fun applyToOthers(ids: List<Long>, categoryId: Long) {
        viewModelScope.launch { c.assignCategory.applyToOthers(ids, categoryId); event.value = TxEvent.Saved }
    }

    fun finish() { event.value = TxEvent.Saved }

    fun delete() {
        viewModelScope.launch {
            if (id > 0) db.transactionDao().deleteById(id)
            event.value = TxEvent.Saved
        }
    }
}

@Composable
fun TransactionEditScreen(id: Long, initialKind: String, onBack: () -> Unit, onNewCategory: (String) -> Unit) {
    val vm = appViewModel(key = "tx$id") { TransactionEditViewModel(it, id, initialKind) }
    val f by vm.form.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val event by vm.event.collectAsStateWithLifecycle()
    var showWallet by remember { mutableStateOf(false) }
    var showToWallet by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(event) { if (event is TxEvent.Saved) onBack() }

    val wallet = wallets.firstOrNull { it.id == f.walletId }
    val toWallet = wallets.firstOrNull { it.id == f.toWalletId }
    val category = categories.firstOrNull { it.id == f.categoryId }

    AppScaffold(
        title = if (id == 0L) "Нова операція" else "Операція",
        onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Видалити") } },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            if (f.source != TxSource.MANUAL) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (f.source == TxSource.NOTIFICATION) "Записано автоматично зі сповіщення" else "Імпортовано з банку",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            val details = listOfNotNull(wallet?.name, f.cardLast4?.let { "картка •••• $it" }).joinToString(" · ")
                            if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TxKind.EXPENSE to "Витрата", TxKind.INCOME to "Дохід", TxKind.TRANSFER to "Переказ").forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = f.kind == k,
                        onClick = { vm.update { copy(kind = k, categoryId = if (k != kind) null else categoryId) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(
                    value = f.amount, onValueChange = { v -> vm.update { copy(amount = v, error = null) } },
                    label = { Text("Сума") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    isError = f.error != null && f.amount.isBlank(), modifier = Modifier.weight(2f),
                )
                Spacer(Modifier.width(8.dp))
                CurrencyPicker(
                    f.currency ?: wallet?.currency ?: "UAH",
                    onChange = { v -> vm.update { copy(currency = v) } },
                    modifier = Modifier.weight(1.2f),
                )
            }
            Spacer(Modifier.height(12.dp))
            PickerField(
                if (f.kind == TxKind.TRANSFER) "З гаманця" else "Гаманець", wallet?.name ?: "Оберіть",
                onClick = { showWallet = true },
                leading = wallet?.let { { WalletIcon(it.type, it.color, 24) } },
            )
            if (f.kind == TxKind.TRANSFER) {
                Spacer(Modifier.height(12.dp))
                PickerField("На гаманець", toWallet?.name ?: "Оберіть", onClick = { showToWallet = true },
                    leading = toWallet?.let { { WalletIcon(it.type, it.color, 24) } })
            } else {
                Spacer(Modifier.height(12.dp))
                PickerField(
                    "Категорія", category?.name ?: "Без категорії",
                    onClick = { showCategory = true },
                    leading = { CategoryBadge(category?.icon ?: "category", category?.color ?: 0xFFF9A825, 24) },
                )
                if (category == null) Text("Категорію не призначено", color = WarnAmber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = f.merchant, onValueChange = { v -> vm.update { copy(merchant = v) } },
                    label = { Text("Магазин / отримувач") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (f.merchant.isNotBlank() && f.categoryId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = f.learnRule, onCheckedChange = { v -> vm.update { copy(learnRule = v) } })
                        Text("Запам'ятати категорію для «${f.merchant.trim()}»", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DatePickerField("Дата", f.timestamp, onChange = { t -> vm.update { copy(timestamp = t) } })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = f.note, onValueChange = { v -> vm.update { copy(note = v) } },
                label = { Text("Нотатка") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            f.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth(), enabled = f.loaded) { Text("Зберегти") }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showWallet) WalletPickerSheet(wallets, f.walletId, onSelect = { w: WalletEntity -> vm.update { copy(walletId = w.id, currency = w.currency) }; showWallet = false }, onDismiss = { showWallet = false })
    if (showToWallet) WalletPickerSheet(wallets, f.toWalletId, onSelect = { w: WalletEntity -> vm.update { copy(toWalletId = w.id) }; showToWallet = false }, onDismiss = { showToWallet = false })
    if (showCategory) CategoryPickerSheet(
        categories, kind = f.kind, selectedId = f.categoryId,
        onSelect = { cat: CategoryEntity? -> vm.update { copy(categoryId = cat?.id) }; showCategory = false },
        onDismiss = { showCategory = false },
    )
    (event as? TxEvent.AskApplyToOthers)?.let { e ->
        AlertDialog(
            onDismissRequest = vm::finish,
            title = { Text("Застосувати до інших?") },
            text = { Text("Є ще ${e.ids.size} операцій «${e.merchant}» без категорії. Призначити їм цю ж категорію?") },
            confirmButton = { TextButton(onClick = { vm.applyToOthers(e.ids, e.categoryId) }) { Text("Так, застосувати") } },
            dismissButton = { TextButton(onClick = vm::finish) { Text("Ні") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Видалити операцію?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Видалити") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
        )
    }
}
