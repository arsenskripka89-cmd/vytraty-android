package ua.vytraty.app.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ua.vytraty.app.ui.components.AppScaffold

@Composable
fun MoreScreen(
    onWallets: () -> Unit, onCategories: () -> Unit, onBanks: () -> Unit,
    onSettings: () -> Unit, onRules: () -> Unit, onCaptureRules: () -> Unit, onLog: () -> Unit,
) {
    AppScaffold(title = "Ще") { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Item(Icons.Filled.AccountBalanceWallet, "Гаманці", "Картки, готівка, рахунки та їх баланси", onWallets)
            Item(Icons.Filled.Category, "Категорії", "Категорії витрат і доходів", onCategories)
            Item(Icons.Filled.AccountBalance, "Банки та інтеграції", "Monobank API, сповіщення інших банків", onBanks)
            HorizontalDivider()
            Item(Icons.Filled.Rule, "Правила розпізнавання", "Яке сповіщення до якої картки й категорії належить", onCaptureRules)
            Item(Icons.Filled.Rule, "Правила мерчантів", "Що застосунок запам'ятав: магазин → категорія", onRules)
            Item(Icons.Filled.History, "Сховище сповіщень", "Усі сповіщення банків за класами: які пройшли правила, які ні", onLog)
            HorizontalDivider()
            Item(Icons.Filled.Settings, "Налаштування", "Доступ до сповіщень, експорт, тестер парсера", onSettings)
            Text(
                "Витрати · v${ua.vytraty.app.BuildConfig.VERSION_NAME} · Ваші дані зберігаються лише на цьому пристрої.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
