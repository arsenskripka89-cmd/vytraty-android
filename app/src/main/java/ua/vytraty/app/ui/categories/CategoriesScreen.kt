@file:OptIn(ExperimentalMaterial3Api::class)

package ua.vytraty.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
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
import ua.vytraty.app.data.db.TxKind
import ua.vytraty.app.di.AppContainer
import ua.vytraty.app.ui.components.AppScaffold
import ua.vytraty.app.ui.components.CategoryBadge
import ua.vytraty.app.ui.components.ColorPicker
import ua.vytraty.app.ui.components.IconPicker
import ua.vytraty.app.ui.components.appViewModel

class CategoriesViewModel(c: AppContainer) : ViewModel() {
    val categories = c.db.categoryDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun CategoriesScreen(onEdit: (Long, String) -> Unit, onBack: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    val kind = if (tab == 0) TxKind.EXPENSE else TxKind.INCOME
    AppScaffold(title = "Категорії", onBack = onBack, fab = { FloatingActionButton(onClick = { onEdit(0, kind.name) }) { Icon(Icons.Filled.Add, "Додати") } }) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Витрати") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Доходи") })
            }
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)) {
                items(categories.filter { it.kind == kind }, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        leadingContent = { CategoryBadge(c.icon, c.color) },
                        modifier = Modifier.clickable { onEdit(c.id, c.kind.name) },
                    )
                }
            }
        }
    }
}

data class CategoryForm(val id: Long = 0, val name: String = "", val icon: String = "category", val color: Long = 0xFF607D8B, val kind: TxKind = TxKind.EXPENSE, val sortOrder: Int = 50, val error: String? = null)

class CategoryEditViewModel(private val c: AppContainer, private val id: Long, initialKind: String) : ViewModel() {
    val form = MutableStateFlow(CategoryForm(kind = runCatching { TxKind.valueOf(initialKind) }.getOrDefault(TxKind.EXPENSE)))
    val done = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            if (id > 0) c.db.categoryDao().byId(id)?.let { cat -> form.value = CategoryForm(cat.id, cat.name, cat.icon, cat.color, cat.kind, cat.sortOrder) }
        }
    }

    fun update(block: CategoryForm.() -> CategoryForm) = form.update(block)

    fun save() = viewModelScope.launch {
        val f = form.value
        if (f.name.isBlank()) { form.update { it.copy(error = "Вкажіть назву") }; return@launch }
        val cat = CategoryEntity(id = f.id, name = f.name.trim(), icon = f.icon, color = f.color, kind = f.kind, sortOrder = f.sortOrder)
        if (f.id == 0L) c.db.categoryDao().insert(cat) else c.db.categoryDao().update(cat)
        done.value = true
    }

    fun delete() = viewModelScope.launch {
        c.db.categoryDao().byId(id)?.let { c.db.categoryDao().delete(it) }
        done.value = true
    }
}

@Composable
fun CategoryEditScreen(id: Long, initialKind: String, onBack: () -> Unit) {
    val vm = appViewModel(key = "category$id") { CategoryEditViewModel(it, id, initialKind) }
    val f by vm.form.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) onBack() }

    AppScaffold(
        title = if (id == 0L) "Нова категорія" else "Категорія", onBack = onBack,
        actions = { if (id > 0) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Видалити") } },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                f.name, { v -> vm.update { copy(name = v, error = null) } }, label = { Text("Назва") }, singleLine = true,
                leadingIcon = { CategoryBadge(f.icon, f.color, 28) }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = f.kind == TxKind.EXPENSE, onClick = { vm.update { copy(kind = TxKind.EXPENSE) } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Витрата") }
                SegmentedButton(selected = f.kind == TxKind.INCOME, onClick = { vm.update { copy(kind = TxKind.INCOME) } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Дохід") }
            }
            Spacer(Modifier.height(16.dp))
            Text("Іконка", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            IconPicker(f.icon, f.color) { i -> vm.update { copy(icon = i) } }
            Spacer(Modifier.height(16.dp))
            Text("Колір", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorPicker(f.color) { c -> vm.update { copy(color = c) } }
            f.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
            Spacer(Modifier.height(80.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Видалити категорію?") },
        text = { Text("Операції залишаться, але стануть «без категорії». Правила мерчантів для цієї категорії буде видалено.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Видалити") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Скасувати") } },
    )
}
