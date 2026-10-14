package com.alvault.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alvault.app.AppContainer
import com.alvault.app.ui.screens.AuditScreen
import com.alvault.app.ui.screens.GeneratorScreen
import com.alvault.app.ui.screens.ItemDetailScreen
import com.alvault.app.ui.screens.ItemEditScreen
import com.alvault.app.ui.screens.RegisterScreen
import com.alvault.app.ui.screens.SettingsScreen
import com.alvault.app.ui.screens.UnlockScreen
import com.alvault.app.ui.screens.VaultListScreen
import com.alvault.app.ui.vm.AppViewModelFactory
import com.alvault.app.ui.vm.AuditViewModel
import com.alvault.app.ui.vm.AuthState
import com.alvault.app.ui.vm.AuthViewModel
import com.alvault.app.ui.vm.SettingsViewModel
import com.alvault.app.ui.vm.VaultViewModel

/** Akar UI: memilih layar menurut status autentikasi (belum daftar / terkunci / terbuka). */
@Composable
fun AlVaultRoot(container: AppContainer, activity: FragmentActivity) {
    val factory = remember(container) { AppViewModelFactory(container) }
    val authVm: AuthViewModel = viewModel(factory = factory)
    val auth by authVm.ui.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (auth.state) {
            AuthState.LOADING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            AuthState.NEEDS_REGISTRATION -> RegisterScreen(vm = authVm, ui = auth)

            AuthState.LOCKED -> UnlockScreen(vm = authVm, ui = auth, activity = activity)

            // Subpohon ini hanya ada selama sesi terbuka: saat terkunci ia keluar dari komposisi,
            // sehingga seluruh state layar (termasuk isian formulir) ikut dibuang.
            AuthState.UNLOCKED -> UnlockedApp(container = container, activity = activity, authVm = authVm, factory = factory)
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    VAULT("Brankas", Icons.Filled.Lock),
    GENERATOR("Generator", Icons.Filled.Casino),
    AUDIT("Audit", Icons.Filled.Security),
    SETTINGS("Pengaturan", Icons.Filled.Settings)
}

/** Sasaran formulir ubah/tambah: [id] null = entri baru. */
private data class EditTarget(val id: String?)

@Composable
private fun UnlockedApp(
    container: AppContainer,
    activity: FragmentActivity,
    authVm: AuthViewModel,
    factory: AppViewModelFactory
) {
    val vaultVm: VaultViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)
    val auditVm: AuditViewModel = viewModel(factory = factory)

    var tab by remember { mutableStateOf(Tab.VAULT) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var edit by remember { mutableStateOf<EditTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }

    // Dekripsi brankas ke RAM begitu sesi terbuka.
    LaunchedEffect(Unit) { vaultVm.load() }

    LaunchedEffect(Unit) { vaultVm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { settingsVm.messages.collect { snackbar.showSnackbar(it) } }

    val overlayOpen = edit != null || detailId != null
    // Tombol kembali dari tab lain menuju tab Brankas dulu; dari tab Brankas keluar seperti biasa.
    BackHandler(enabled = !overlayOpen && tab != Tab.VAULT) { tab = Tab.VAULT }

    Scaffold(
        bottomBar = {
            if (!overlayOpen) {
                NavigationBar {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            label = { Text(entry.label) }
                        )
                    }
                }
            }
        },
        snackbarHost = {
            // Tanpa bilah navigasi bawah (layar detail/formulir), snackbar harus menghindari bilah gestur sistem.
            SnackbarHost(
                hostState = snackbar,
                modifier = if (overlayOpen) Modifier.navigationBarsPadding() else Modifier
            )
        },
        // Setiap layar mengurus inset status bar lewat TopAppBar-nya sendiri.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val editing = edit
            val detail = detailId
            when {
                editing != null -> ItemEditScreen(
                    vaultVm = vaultVm,
                    id = editing.id,
                    onClose = { edit = null }
                )

                detail != null -> ItemDetailScreen(
                    vaultVm = vaultVm,
                    id = detail,
                    onBack = { detailId = null },
                    onEdit = { id -> edit = EditTarget(id) }
                )

                else -> when (tab) {
                    Tab.VAULT -> VaultListScreen(
                        vaultVm = vaultVm,
                        onOpen = { id -> detailId = id },
                        onAdd = { edit = EditTarget(null) },
                        onLock = { authVm.lock() }
                    )

                    Tab.GENERATOR -> GeneratorScreen(vaultVm = vaultVm)

                    Tab.AUDIT -> AuditScreen(
                        auditVm = auditVm,
                        vaultVm = vaultVm,
                        onOpenItem = { id -> detailId = id }
                    )

                    Tab.SETTINGS -> SettingsScreen(
                        authVm = authVm,
                        vaultVm = vaultVm,
                        settingsVm = settingsVm,
                        activity = activity
                    )
                }
            }
        }
    }
}
