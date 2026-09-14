package ua.vytraty.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.vytraty.app.VytratyApp
import ua.vytraty.app.di.AppContainer

@Composable
fun rememberContainer(): AppContainer {
    val ctx = LocalContext.current.applicationContext
    return remember(ctx) { (ctx as VytratyApp).container }
}

/** Creates a ViewModel from the app container. [key] separates instances (e.g. per edited id). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = rememberContainer()
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
        }
    }
    return viewModel(key = key, factory = factory)
}
