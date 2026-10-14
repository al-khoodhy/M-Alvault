package com.alvault.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alvault.app.AppContainer

/** Pabrik ViewModel manual (tanpa framework DI): semua ViewModel menerima [AppContainer]. */
class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(container) as T
        modelClass.isAssignableFrom(VaultViewModel::class.java) -> VaultViewModel(container) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
        modelClass.isAssignableFrom(AuditViewModel::class.java) -> AuditViewModel(container) as T
        else -> throw IllegalArgumentException("ViewModel tidak dikenal: ${modelClass.name}")
    }
}
