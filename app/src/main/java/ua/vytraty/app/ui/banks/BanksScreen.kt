@file:OptIn(ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.banks

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.vytraty.app.data.bank.monobank.MonobankSyncWorker
import ua.vytraty.app.data.prefs.Settings as AppSettings
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.parser.BankSource
import ua.vytraty.app.notifications.PaymentNotificationListener
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.appViewModel

class BanksViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun saveToken(token: String, ctx: android.content.Context) = viewModelScope.launch {
        c.settings.setMonobankToken(token)
        if (token.isBlank()) MonobankSyncWorker.cancel(ctx) else MonobankSyncWorker.schedule(ctx)
        message.value = if (token.isBlank()) "Токен видалено" else "Токен збережено"
    }

    fun validate() = viewModelScope.launch {
        busy.value = true
        message.value = c.monobank.validate()?.let { "Помилка: $it" } ?: "Токен дійсний ✓"
        busy.value = false
    }

    fun sync() = viewModelScope.launch {
        busy.value = true
        message.value = "Синхронізація… (для кількох рахунків це триває понад хвилину через ліміти API)"
        val r = c.monobank.sync()
        message.value = r.error?.let { "Помилка: $it" } ?: "Готово: гаманців створено ${r.walletsCreated}, операцій імпортовано ${r.transactionsImported}"
        busy.value = false
    }

    fun setAutoSync(v: Boolean) = viewModelScope.launch { c.settings.setMonobankAutoSync(v) }

    fun togglePackage(pkgs: List<String>, enabled: Boolean) = viewModelScope.launch {
        val s = c.settings.current()
        val current = s.enabledPackages.ifEmpty { BankSource.allPackages }
        c.settings.setEnabledPackages(if (enabled) current + pkgs else current - pkgs.toSet())
    }
}

@Composable
fun BanksScreen(onBack: () -> Unit) {
    val vm = appViewModel { BanksViewModel(it) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var token by remember(s.monobankToken) { mutableStateOf(s.monobankToken) }
    var showToken by remember { mutableStateOf(false) }
    val listenerOn = PaymentNotificationListener.isEnabled(context)

    AppScaffold(title = "Банки та інтеграції", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Monobank — API", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Отримайте персональний токен на api.monobank.ua (QR-код у застосунку mono). Рахунки стануть гаманцями, виписка за 31 день імпортується, далі — щогодини.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        token, { token = it }, label = { Text("Токен") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Сховати" else "Показати") } },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { vm.saveToken(token, context) }, enabled = !busy) { Text("Зберегти") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.validate() }, enabled = !busy && s.monobankToken.isNotBlank()) { Text("Перевірити") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.sync() }, enabled = !busy && s.monobankToken.isNotBlank()) { Text("Синхронізувати") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Автосинхронізація щогодини", Modifier.weight(1f))
                        Switch(checked = s.monobankAutoSync, onCheckedChange = { vm.setAutoSync(it) })
                    }
                    if (s.monobankLastSync > 0) Text("Остання синхронізація: ${Dates.formatDateTime(s.monobankLastSync)}", style = MaterialTheme.typography.bodySmall)
                    message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = if (it.startsWith("Помилка")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Через сповіщення", style = MaterialTheme.typography.titleMedium)
            Text(
                "Приват24, Raiffeisen, Revolut, Ощадбанк і ПУМБ не мають відкритого особистого API, тому їх платежі зчитуються зі сповіщень. " +
                    "Увімкніть сповіщення про операції в самому банківському застосунку.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!listenerOn) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Надати доступ до сповіщень") }
            }
            Spacer(Modifier.height(8.dp))
            val enabled = s.enabledPackages.ifEmpty { BankSource.allPackages }
            BankSource.entries.filter { it.packages.isNotEmpty() }.forEach { bank ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bank.displayName)
                        Text(bank.packages.first(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bank.packages.any { it in enabled }, onCheckedChange = { v -> vm.togglePackage(bank.packages, v) })
                }
            }
        }
    }
}
