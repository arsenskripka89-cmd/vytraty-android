@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ua.vytraty.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ua.vytraty.app.data.db.CategoryEntity
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.data.db.WalletEntity
import ua.vytraty.app.data.db.WalletType
import ua.vytraty.app.domain.Dates
import ua.vytraty.app.domain.Money
import ua.vytraty.app.ui.theme.ExpenseRed
import ua.vytraty.app.ui.theme.IncomeGreen
import ua.vytraty.app.ui.theme.TransferBlue
import java.time.LocalDate
import java.time.ZoneOffset

object CategoryIcons {
    val all: Map<String, ImageVector> = mapOf(
        "cart" to Icons.Outlined.ShoppingCart,
        "restaurant" to Icons.Outlined.Restaurant,
        "cafe" to Icons.Outlined.LocalCafe,
        "bus" to Icons.Outlined.DirectionsBus,
        "taxi" to Icons.Outlined.LocalTaxi,
        "car" to Icons.Outlined.DirectionsCar,
        "fuel" to Icons.Outlined.LocalGasStation,
        "home" to Icons.Outlined.Home,
        "bolt" to Icons.Outlined.Bolt,
        "wifi" to Icons.Outlined.Wifi,
        "phone" to Icons.Outlined.Phone,
        "health" to Icons.Outlined.Favorite,
        "pharmacy" to Icons.Outlined.LocalPharmacy,
        "fitness" to Icons.Outlined.FitnessCenter,
        "clothes" to Icons.Outlined.Checkroom,
        "movie" to Icons.Outlined.Movie,
        "games" to Icons.Outlined.SportsEsports,
        "subscriptions" to Icons.Outlined.Subscriptions,
        "shopping" to Icons.Outlined.ShoppingBag,
        "school" to Icons.Outlined.School,
        "gift" to Icons.Outlined.CardGiftcard,
        "flight" to Icons.Outlined.Flight,
        "spa" to Icons.Outlined.Spa,
        "child" to Icons.Outlined.ChildCare,
        "pets" to Icons.Outlined.Pets,
        "receipt" to Icons.Outlined.Receipt,
        "build" to Icons.Outlined.Build,
        "star" to Icons.Outlined.Star,
        "work" to Icons.Outlined.Work,
        "laptop" to Icons.Outlined.Laptop,
        "cashback" to Icons.Outlined.CurrencyExchange,
        "income" to Icons.Outlined.Savings,
        "wallet" to Icons.Outlined.AccountBalanceWallet,
        "category" to Icons.Outlined.Category,
    )

    fun get(name: String?): ImageVector = all[name] ?: Icons.Outlined.Category
}

val PaletteColors = listOf(
    0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5, 0xFF039BE5, 0xFF00ACC1,
    0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFC0CA33, 0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E,
    0xFF6D4C41, 0xFF757575, 0xFF546E7A, 0xFF212121,
)

fun Long.toColor(): Color = Color(this)

@Composable
fun CategoryBadge(icon: String?, color: Long?, size: Int = 40) {
    val c = color?.toColor() ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier.size(size.dp).background(c.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(CategoryIcons.get(icon), contentDescription = null, tint = c, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun WalletIcon(type: WalletType, color: Long, size: Int = 40) {
    val c = color.toColor()
    val icon = when (type) {
        WalletType.CASH -> Icons.Filled.Payments
        WalletType.CARD -> Icons.Filled.CreditCard
        WalletType.BANK_ACCOUNT -> Icons.Filled.AccountBalance
    }
    Box(Modifier.size(size.dp).background(c, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun AmountText(minor: Long, currency: String, kind: TxKind, modifier: Modifier = Modifier, large: Boolean = false) {
    val (color, prefix) = when (kind) {
        TxKind.EXPENSE -> ExpenseRed to "−"
        TxKind.INCOME -> IncomeGreen to "+"
        TxKind.TRANSFER -> TransferBlue to ""
    }
    Text(
        text = prefix + Money.format(minor, currency),
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ColorPicker(selected: Long, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteColors.forEach { c ->
            Box(
                Modifier.size(36.dp).background(c.toColor(), CircleShape)
                    .then(if (c == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Filled.Check, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun IconPicker(selected: String, color: Long, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryIcons.all.keys.forEach { name ->
            Box(
                Modifier.size(44.dp)
                    .background(if (name == selected) color.toColor().copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .then(if (name == selected) Modifier.border(2.dp, color.toColor(), CircleShape) else Modifier)
                    .clickable { onSelect(name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(CategoryIcons.get(name), null, tint = if (name == selected) color.toColor() else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Bottom sheet listing categories of one kind. */
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    kind: TxKind,
    selectedId: Long?,
    onSelect: (CategoryEntity?) -> Unit,
    onDismiss: () -> Unit,
    allowNone: Boolean = true,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Оберіть категорію", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            if (allowNone) item {
                ListItem(
                    headlineContent = { Text("Без категорії") },
                    leadingContent = { CategoryBadge(null, null) },
                    trailingContent = { if (selectedId == null) Icon(Icons.Filled.Check, null) },
                    modifier = Modifier.clickable { onSelect(null) },
                )
            }
            items(categories.filter { it.kind == kind }, key = { it.id }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    leadingContent = { CategoryBadge(c.icon, c.color) },
                    trailingContent = { if (selectedId == c.id) Icon(Icons.Filled.Check, null) },
                    modifier = Modifier.clickable { onSelect(c) },
                )
            }
        }
    }
}

@Composable
fun WalletPickerSheet(wallets: List<WalletEntity>, selectedId: Long?, onSelect: (WalletEntity) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Оберіть гаманець", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(wallets, key = { it.id }) { w ->
                ListItem(
                    headlineContent = { Text(w.name) },
                    supportingContent = { Text(listOfNotNull(w.currency, w.cardLast4?.let { "•••• $it" }).joinToString(" · ")) },
                    leadingContent = { WalletIcon(w.type, w.color) },
                    trailingContent = { if (selectedId == w.id) Icon(Icons.Filled.Check, null) },
                    modifier = Modifier.clickable { onSelect(w) },
                )
            }
        }
    }
}

/** Read-only text field that opens a bottom sheet or dialog when tapped. */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leading,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
fun DatePickerField(label: String, millis: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, Dates.formatDate(millis), onClick = { open = true }, modifier = modifier)
    if (open) {
        val initial = Dates.toLocalDate(millis).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = LocalDate.ofEpochDay(utc / 86_400_000L)
                        val time = Dates.toLocalDateTime(millis).toLocalTime()
                        onChange(Dates.toMillis(date.atTime(time)))
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Скасувати") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun HSpace(dp: Int) = Spacer(Modifier.width(dp.dp))

@Composable
fun VSpace(dp: Int) = Spacer(Modifier.height(dp.dp))
