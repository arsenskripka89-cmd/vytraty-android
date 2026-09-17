@file:OptIn(ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.vytraty.app.data.db.NotificationLogEntity
import ua.vytraty.app.data.update.UpdateChecker
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.prefs.Settings as AppSettings
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.domain.parser.BankSource
import ua.vytraty.app.domain.parser.ParsedPayment
import ua.vytraty.app.notifications.PaymentNotificationListener
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.EmptyState
import ua.vytraty.app.ui.components.CurrencyPicker
import ua.vytraty.app.ui.components.PickerField
import ua.vytraty.app.ui.components.WalletPickerSheet
import ua.vytraty.app.ui.components.appViewModel
import java.io.File

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val wallets = c.db.walletDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unparsed = c.db.notificationLogDao().observeUnparsedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val message = MutableStateFlow<String?>(null)

    data class UpdateUi(
        val checking: Boolean = false,
        val checked: Boolean = false,
        val info: UpdateChecker.ReleaseInfo? = null,
        val error: String? = null,
        val progress: Float? = null,
        val file: File? = null,
    )
    val update = MutableStateFlow(UpdateUi())
    val currentVersion: String get() = c.updateChecker.currentVersion

    fun checkUpdates() = viewModelScope.launch {
        update.value = UpdateUi(checking = true)
        val r = c.updateChecker.check()
        update.value = UpdateUi(checked = true, info = r.getOrNull(), error = r.exceptionOrNull()?.message)
    }

    fun downloadAndInstall() = viewModelScope.launch {
        val info = update.value.info ?: return@launch
        update.value = update.value.copy(progress = 0f, error = null)
        val r = c.updateChecker.download(info) { p -> update.value = update.value.copy(progress = p) }
        r.onSuccess { file ->
            update.value = update.value.copy(progress = null, file = file)
            c.updateChecker.install(file)
        }.onFailure { e -> update.value = update.value.copy(progress = null, error = e.message) }
    }

    fun installDownloaded() { update.value.file?.let { c.updateChecker.install(it) } }

    fun setCapture(v: Boolean) = viewModelScope.launch { c.settings.setCaptureEnabled(v) }
    fun setNotifyUncategorized(v: Boolean) = viewModelScope.launch { c.settings.setNotifyUncategorized(v) }
    fun setOnlyMatchedWallet(v: Boolean) = viewModelScope.launch { c.settings.setOnlyMatchedWallet(v) }
    fun setAutoMergeTransfers(v: Boolean) = viewModelScope.launch { c.settings.setAutoMergeTransfers(v) }
    fun setDefaultWallet(id: Long) = viewModelScope.launch { c.db.walletDao().setDefault(id) }
    fun setMainCurrency(v: String) = viewModelScope.launch { c.settings.setMainCurrency(v) }
    fun applyRules() = viewModelScope.launch { message.value = "Категорію призначено ${c.assignCategory.applyRulesToUncategorized()} операціям" }

    fun exportCsv(context: Context) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val rows = c.db.transactionDao().rowsForExport(0, Long.MAX_VALUE)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "vytraty.csv")
            f.bufferedWriter(Charsets.UTF_8).use { w ->
                w.write("﻿")
                w.write("date;kind;amount;currency;received;receivedCurrency;wallet;category;merchant;note;source;card\n")
                rows.forEach { r ->
                    val cells = listOf(
                        Dates.formatDateTime(r.timestamp), r.kind.name, Money.minorToInput(r.amountMinor), r.currency,
                        r.receivedMinor?.let { Money.minorToInput(it) }.orEmpty(), r.receivedCurrency.orEmpty(), r.walletName,
                        r.categoryName.orEmpty(), r.merchant.orEmpty(), r.note.orEmpty(), r.source.name, r.cardLast4.orEmpty(),
                    )
                    w.write(cells.joinToString(";") { "\"" + it.replace("\"", "\"\"") + "\"" } + "\n")
                }
            }
            f
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Експорт CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLog: () -> Unit, onTester: () -> Unit, onRules: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val unparsed by vm.unparsed.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showWallet by remember { mutableStateOf(false) }
    val listenerOn = PaymentNotificationListener.isEnabled(context)
    val defaultWallet = wallets.firstOrNull { it.isDefault }

    val upd by vm.update.collectAsStateWithLifecycle()

    AppScaffold(title = "Налаштування", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Оновлення застосунку", fontWeight = FontWeight.Bold)
                    Text("Встановлена версія ${vm.currentVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val info = upd.info
                    when {
                        upd.checking -> Text("Перевіряю…")
                        info != null -> {
                            Text("Доступна версія ${info.version}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            val progress = upd.progress
                            if (progress != null) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                Text("Завантаження ${(progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall)
                            } else if (upd.file != null) {
                                Button(onClick = { vm.installDownloaded() }) { Text("Встановити") }
                            } else {
                                Button(onClick = { vm.downloadAndInstall() }) { Text("Завантажити та встановити") }
                            }
                        }
                        upd.checked && upd.error == null -> Text("У вас найновіша версія ✓", color = MaterialTheme.colorScheme.primary)
                    }
                    upd.error?.let { Text("Помилка: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (!upd.checking && upd.info == null) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.checkUpdates() }) { Text("Перевірити оновлення") }
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (listenerOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (listenerOn) "Доступ до сповіщень надано ✓" else "Доступ до сповіщень не надано", fontWeight = FontWeight.Bold)
                    Text("Потрібен, щоб читати сповіщення Google Pay та банків про оплату. Застосунок читає лише пакети зі списку банків.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Text("Відкрити системні налаштування")
                    }
                }
            }
            SwitchRow("Автозахоплення платежів", "Створювати операції зі сповіщень", s.captureEnabled) { vm.setCapture(it) }
            SwitchRow("Сповіщати про операції без категорії", "У сповіщенні — кнопки популярних категорій цієї картки", s.notifyUncategorized) { vm.setNotifyUncategorized(it) }
            SwitchRow(
                "Записувати лише за правилом картки",
                "Реклама й інші сповіщення без правила не стають витратами — вони чекають у сховищі сповіщень",
                s.onlyMatchedWallet,
            ) { vm.setOnlyMatchedWallet(it) }
            SwitchRow(
                "Об'єднувати перекази між своїми картками",
                "Списання й зарахування на різних картках протягом 15 хв стають одним переказом із двома сумами",
                s.autoMergeTransfers,
            ) { vm.setAutoMergeTransfers(it) }
            Column(Modifier.padding(16.dp, 8.dp)) {
                PickerField("Гаманець за замовчуванням для сповіщень", defaultWallet?.name ?: "Не обрано", onClick = { showWallet = true })
                Text("Використовується, коли картку у сповіщенні не розпізнано. Щоб розпізнавалась — вкажіть останні 4 цифри в гаманці.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.padding(16.dp, 8.dp)) {
                CurrencyPicker(s.mainCurrency, onChange = { vm.setMainCurrency(it) }, label = "Основна валюта")
            }
            HorizontalDivider()
            ListItem(headlineContent = { Text("Сховище сповіщень") }, supportingContent = { Text(if (unparsed > 0) "Нерозпізнаних: $unparsed" else "Усі сповіщення за класами") }, modifier = Modifier.clickable(onClick = onLog))
            ListItem(headlineContent = { Text("Тестер парсера") }, supportingContent = { Text("Вставте текст сповіщення і перевірте, як він розпізнається") }, modifier = Modifier.clickable(onClick = onTester))
            ListItem(headlineContent = { Text("Правила мерчантів") }, supportingContent = { Text("Магазин → категорія") }, modifier = Modifier.clickable(onClick = onRules))
            ListItem(headlineContent = { Text("Застосувати правила до операцій без категорії") }, modifier = Modifier.clickable { vm.applyRules() })
            ListItem(headlineContent = { Text("Експорт у CSV") }, supportingContent = { Text("Усі операції, роздільник «;», відкривається в Excel") }, modifier = Modifier.clickable { vm.exportCsv(context) })
            message?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showWallet) WalletPickerSheet(wallets, defaultWallet?.id, onSelect = { vm.setDefaultWallet(it.id); showWallet = false }, onDismiss = { showWallet = false })
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------- Parser tester ----------------

class ParserTesterViewModel(private val c: AppContainer) : ViewModel() {
    val result = MutableStateFlow<ParsedPayment?>(null)
    val message = MutableStateFlow<String?>(null)

    fun parse(bank: BankSource, title: String, text: String) {
        result.value = c.parserRegistry.parseAs(bank, title.ifBlank { null }, text.ifBlank { null })
        message.value = if (result.value == null) "Суму не знайдено. Перевірте, чи є в тексті число з валютою (₴, грн, UAH, $, €)." else null
    }

    fun record() = viewModelScope.launch {
        val p = result.value ?: return@launch
        val id = c.recordPayment.record(p, System.currentTimeMillis(), null)
        message.value = "Операцію №$id записано"
    }
}

@Composable
fun ParserTesterScreen(onBack: () -> Unit) {
    val vm = appViewModel { ParserTesterViewModel(it) }
    val result by vm.result.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var bank by remember { mutableStateOf(BankSource.GOOGLE_PAY) }
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var showBank by remember { mutableStateOf(false) }

    AppScaffold(title = "Тестер парсера", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Column {
                PickerField("Джерело", bank.displayName, onClick = { showBank = true })
                DropdownMenu(expanded = showBank, onDismissRequest = { showBank = false }) {
                    BankSource.entries.forEach { b -> DropdownMenuItem(text = { Text(b.displayName) }, onClick = { bank = b; showBank = false }) }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Заголовок сповіщення") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(text, { text = it }, label = { Text("Текст сповіщення") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { vm.parse(bank, title, text) }, modifier = Modifier.fillMaxWidth()) { Text("Розпізнати") }
            Spacer(Modifier.height(16.dp))
            result?.let { p ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Результат", fontWeight = FontWeight.Bold)
                        Text("Сума: ${Money.format(p.amountMinor, p.currency)}")
                        Text("Тип: ${if (p.kind == TxKind.EXPENSE) "витрата" else "дохід"}")
                        Text("Мерчант: ${p.merchant ?: "—"}")
                        Text("Картка: ${p.cardLast4?.let { "•••• $it" } ?: "—"}")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.record() }) { Text("Записати як операцію") }
                    }
                }
            }
            message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

// ---------------- Merchant rules ----------------

class MerchantRulesViewModel(private val c: AppContainer) : ViewModel() {
    val rules = c.db.merchantRuleDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = c.db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(id: Long) = viewModelScope.launch { rules.value.firstOrNull { it.id == id }?.let { c.db.merchantRuleDao().delete(it) } }
}

@Composable
fun MerchantRulesScreen(onBack: () -> Unit) {
    val vm = appViewModel { MerchantRulesViewModel(it) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    AppScaffold(title = "Правила мерчантів", onBack = onBack) { padding ->
        if (rules.isEmpty()) EmptyState("Правила з'являються, коли ви призначаєте категорію операції з магазином. Наступні платежі цьому магазину отримають категорію автоматично.", Modifier.padding(padding))
        LazyColumn(Modifier.padding(padding)) {
            items(rules, key = { it.id }) { r ->
                val cat = categories.firstOrNull { it.id == r.categoryId }
                ListItem(
                    headlineContent = { Text(r.merchantDisplay) },
                    supportingContent = { Text("${cat?.name ?: "?"} · застосовано ${r.hits} раз(ів)") },
                    leadingContent = { CategoryBadge(cat?.icon, cat?.color) },
                    trailingContent = { IconButton(onClick = { vm.delete(r.id) }) { Icon(Icons.Filled.Delete, "Видалити") } },
                )
            }
        }
    }
}
