@file:OptIn(ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.wallets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.data.db.WalletType
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.observeWalletBalances
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.ColorPicker
import ua.vytraty.app.ui.components.SectionHeader
import ua.vytraty.app.ui.components.WalletIcon
import ua.vytraty.app.ui.components.appViewModel

class WalletsViewModel(c: AppContainer) : ViewModel() {
    val wallets = observeWalletBalances(c.db, includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun WalletsScreen(onEdit: (Long) -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { WalletsViewModel(it) }
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val active = wallets.filter { !it.wallet.archived }
    val archived = wallets.filter { it.wallet.archived }
    AppScaffold(title = "Гаманці", onBack = onBack, fab = { FloatingActionButton(onClick = { onEdit(0) }) { Icon(Icons.Filled.Add, "Додати") } }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
            item {
                val byCurrency = active.groupBy { it.wallet.currency }.map { (cur, list) -> Money.format(list.sumOf { it.balanceMinor }, cur) }
                Text("Разом: ${byCurrency.joinToString(" · ")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            }
            items(active, key = { it.wallet.id }) { wb ->
                ListItem(
                    headlineContent = { Text(wb.wallet.name + if (wb.wallet.isDefault) "  (за замовчуванням)" else "") },
                    supportingContent = { Text(listOfNotNull(walletTypeLabel(wb.wallet.type), wb.wallet.cardLast4?.let { "•••• $it" }, wb.wallet.bankCode?.let { "Monobank" }).joinToString(" · ")) },
                    leadingContent = { WalletIcon(wb.wallet.type, wb.wallet.color) },
                    trailingContent = { Text(Money.format(wb.balanceMinor, wb.wallet.currency), fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.clickable { onEdit(wb.wallet.id) },
                )
            }
            if (archived.isNotEmpty()) {
                item { SectionHeader("Архів") }
                items(archived, key = { it.wallet.id }) { wb ->
                    ListItem(
                        headlineContent = { Text(wb.wallet.name) },
                        leadingContent = { WalletIcon(wb.wallet.type, 0xFF9E9E9E) },
                        trailingContent = { Text(Money.format(wb.balanceMinor, wb.wallet.currency)) },
                        modifier = Modifier.clickable { onEdit(wb.wallet.id) },
                    )
                }
            }
        }
    }
}

fun walletTypeLabel(t: WalletType) = when (t) {
    WalletType.CASH -> "Готівка"
    WalletType.CARD -> "Картка"
    WalletType.BANK_ACCOUNT -> "Рахунок"
}

data class WalletForm(
    val id: Long = 0, val name: String = "", val type: WalletType = WalletType.CARD, val currency: String = "UAH",
    val initialBalance: String = "0", val color: Long = 0xFF1E88E5, val cardLast4: String = "", val isDefault: Boolean = false,
    val archived: Boolean = false, val bankCode: String? = null, val monoAccountId: String? = null, val sortOrder: Int = 0, val error: String? = null,
)

class WalletEditViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val form = MutableStateFlow(WalletForm())
    val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (id > 0) c.db.walletDao().byId(id)?.let { w ->
                form.value = WalletForm(w.id, w.name, w.type, w.currency, Money.minorToInput(w.initialBalanceMinor), w.color, w.cardLast4.orEmpty(), w.isDefault, w.archived, w.bankCode, w.monoAccountId, w.sortOrder)
            } else form.update { it.copy(currency = c.settings.current().mainCurrency) }
        }
    }

    fun update(block: WalletForm.() -> WalletForm) = form.update(block)

    fun save() = viewModelScope.launch {
        val f = form.value
        if (f.name.isBlank()) { form.update { it.copy(error = "Вкажіть назву") }; return@launch }
        val initial = Money.parseToMinor(f.initialBalance) ?: 0L
        val w = WalletEntity(
            id = f.id, name = f.name.trim(), type = f.type, currency = f.currency.uppercase().trim().ifBlank { "UAH" },
            initialBalanceMinor = initial, color = f.color, cardLast4 = f.cardLast4.filter { it.isDigit() }.takeLast(4).ifBlank { null },
            bankCode = f.bankCode, monoAccountId = f.monoAccountId, isDefault = f.isDefault, archived = f.archived, sortOrder = f.sortOrder,
        )
        val savedId = if (f.id == 0L) c.db.walletDao().insert(w) else { c.db.walletDao().update(w); w.id }
        if (f.isDefault) c.db.walletDao().setDefault(savedId)
        done.value = true
    }

    fun delete() = viewModelScope.launch {
        c.db.walletDao().byId(id)?.let { c.db.walletDao().delete(it) }
        done.value = true
    }
}

@Composable
fun WalletEditScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "wallet$id") { WalletEditViewModel(it, id) }
    val f by vm.form.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) onBack() }

    AppScaffold(
        title = if (id == 0L) "Новий гаманець" else "Гаманець", onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Видалити") } },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(f.name, { v -> vm.update { copy(name = v, error = null) } }, label = { Text("Назва") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WalletType.entries.forEachIndexed { i, t ->
                    SegmentedButton(selected = f.type == t, onClick = { vm.update { copy(type = t) } }, shape = SegmentedButtonDefaults.itemShape(i, WalletType.entries.size)) { Text(walletTypeLabel(t)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedTextField(f.currency, { v -> vm.update { copy(currency = v.take(3)) } }, label = { Text("Валюта") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.padding(6.dp))
                OutlinedTextField(
                    f.initialBalance, { v -> vm.update { copy(initialBalance = v) } }, label = { Text("Початковий баланс") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(2f),
                )
            }
            if (f.type != WalletType.CASH) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    f.cardLast4, { v -> vm.update { copy(cardLast4 = v.filter { it.isDigit() }.take(4)) } },
                    label = { Text("Останні 4 цифри картки") }, supportingText = { Text("За ними сповіщення про оплату зіставляється з цим гаманцем") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Колір", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorPicker(f.color) { c -> vm.update { copy(color = c) } }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Гаманець за замовчуванням")
                    Text("Сюди записуються платежі зі сповіщень, якщо картку не розпізнано", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = f.isDefault, onCheckedChange = { v -> vm.update { copy(isDefault = v) } })
            }
            if (id > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("В архіві", Modifier.weight(1f))
                Switch(checked = f.archived, onCheckedChange = { v -> vm.update { copy(archived = v) } })
            }
            f.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
            Spacer(Modifier.height(80.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Видалити гаманець?") },
        text = { Text("Разом з ним будуть видалені всі його операції. Краще перемістити в архів.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Видалити") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
    )
}
