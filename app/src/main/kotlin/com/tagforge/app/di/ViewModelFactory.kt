package com.tagforge.app.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tagforge.app.AppContainer
import com.tagforge.app.TagForgeApplication

/** The process-wide [AppContainer] for the current Compose context. */
@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as TagForgeApplication).container

/**
 * Creates or retrieves a ViewModel built from the manual-DI container:
 * `val vm = tagForgeViewModel { ReadViewModel(it.scanCoordinator, ...) }`.
 */
@Composable
inline fun <reified VM : ViewModel> tagForgeViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = rememberAppContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
