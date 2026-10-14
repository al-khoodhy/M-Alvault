@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvault.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvault.app.core.model.MergeMode
import com.alvault.app.core.session.Hardening
import com.alvault.app.core.sync.PullStrategy
import com.alvault.app.core.sync.SupabaseSchema
import com.alvault.app.ui.UiLogic
import com.alvault.app.ui.components.ResetAllDialog
import com.alvault.app.ui.components.SecretField
import com.alvault.app.ui.components.SectionCard
import com.alvault.app.ui.components.StatusBanner
import com.alvault.app.ui.components.StrengthMeter
import com.alvault.app.ui.vm.AuthViewModel
import com.alvault.app.ui.vm.SettingsViewModel
import com.alvault.app.ui.vm.VaultViewModel

/**
 * MIME untuk dialog "Simpan sebagai". Sengaja bukan application/json: penyedia dokumen Android menambahkan
 * ekstensi sesuai MIME, sehingga nama `x.aegis` bisa menjadi `x.aegis.json`. Isinya tetap JSON.
 */
private const val EXPORT_MIME = "application/octet-stream"

/** Tab "Pengaturan": akun, biometrik, backup .aegis, sinkronisasi Supabase, dan hapus data. */
@Composable
fun SettingsScreen(
    authVm: AuthViewModel,
    vaultVm: VaultViewModel,
    settingsVm: SettingsViewModel,
    activity: FragmentActivity
) {
    val ui by settingsVm.ui.collectAsStateWithLifecycle()
    val vaultUi by vaultVm.ui.collectAsStateWithLifecycle()
    val busy by vaultVm.busy.collectAsStateWithLifecycle()
    val pendingExport by vaultVm.pendingExport.collectAsStateWithLifecycle()
    val pendingImport by vaultVm.pendingImport.collectAsStateWithLifecycle()
    val importError by vaultVm.importError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { settingsVm.refresh() }

    // Isian URL/key memakai state lokal; direset hanya bila nilai tersimpan berubah.
    var url by remember(ui.syncUrl) { mutableStateOf(ui.syncUrl) }
    var key by remember(ui.syncKey) { mutableStateOf(ui.syncKey) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showSql by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXPORT_MIME)
    ) { uri -> vaultVm.writeExport(uri) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> vaultVm.readImport(uri) }

    // Backup sudah terenkripsi di RAM: minta lokasi simpan.
    LaunchedEffect(pendingExport) {
        pendingExport?.let { createLauncher.launch(it.fileName) }
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Pengaturan") })

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Akun ----
            SectionCard("Akun") {
                Text(
                    text = if (ui.email.isNotEmpty()) UiLogic.maskEmail(ui.email) else "—",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(onClick = { authVm.lock() }) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Kunci sekarang")
                }
            }

            // ---- Keamanan ----
            SectionCard("Keamanan") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Buka dengan biometrik", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (ui.biometricAvailable || ui.biometricEnabled) {
                                "Kunci brankas dibungkus kunci Android Keystore yang hanya terbuka lewat sidik jari atau " +
                                    "wajah. Otomatis nonaktif bila data biometrik di perangkat berubah."
                            } else {
                                "Tidak tersedia: daftarkan sidik jari atau wajah di pengaturan Android terlebih dahulu."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ui.biometricEnabled,
                        onCheckedChange = { settingsVm.setBiometric(activity, it) },
                        enabled = ui.biometricAvailable || ui.biometricEnabled
                    )
                }
                Text(
                    text = "• Layar tidak bisa di-screenshot atau direkam.\n" +
                        "• Brankas terkunci otomatis setelah ${Hardening.AUTO_LOCK_MS / 1000} detik di latar belakang " +
                        "atau tanpa aktivitas.\n" +
                        "• Clipboard dibersihkan ${Hardening.CLIPBOARD_CLEAR_MS / 1000} detik setelah password disalin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Backup .aegis ----
            SectionCard("Backup .aegis") {
                Text(
                    text = "Berkas terenkripsi (PBKDF2 600.000 iterasi + AES-256-GCM) yang juga bisa dibuka di " +
                        "aplikasi web AlVault. Dilindungi password backup sendiri, bukan master password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showExportDialog = true },
                        enabled = !busy && vaultUi.loaded,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Ekspor", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { openLauncher.launch(arrayOf("*/*")) },
                        enabled = !busy && vaultUi.loaded,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Impor", maxLines = 1)
                    }
                }
                if (busy && pendingImport == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // ---- Supabase ----
            SectionCard("Sinkronisasi Supabase (opsional)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Supabase Sync", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Hanya ciphertext brankas yang dikirim; Supabase tidak pernah melihat isinya.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = ui.syncEnabled, onCheckedChange = { settingsVm.setSyncEnabled(it) })
                }

                if (ui.syncEnabled) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Supabase Project URL") },
                        placeholder = { Text("https://xyz.supabase.co") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next
                        )
                    )
                    SecretField(
                        value = key,
                        onValueChange = { key = it },
                        label = "Supabase Anon/Service Key",
                        modifier = Modifier.fillMaxWidth(),
                        imeAction = ImeAction.Done,
                        supportingText = "Disimpan terenkripsi di perangkat ini."
                    )
                    Text(
                        text = "Disarankan memakai anon key dengan kebijakan RLS. Service key memberi akses penuh " +
                            "ke database Anda; jangan pernah dibagikan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { settingsVm.testConnection(url, key) },
                        enabled = !ui.syncBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Test Connection") }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { settingsVm.push(url, key) },
                            enabled = !ui.syncBusy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Push (Upload)", maxLines = 1) }
                        Button(
                            onClick = { settingsVm.pull(url, key) },
                            enabled = !ui.syncBusy,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("Pull (Download)", maxLines = 1) }
                    }

                    if (ui.syncBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    ui.syncStatus?.let { StatusBanner(text = it.text, kind = it.kind) }

                    Text(
                        text = "Revisi lokal ${ui.localRevision} · revisi cloud terakhir yang diselaraskan ${ui.lastSyncedRevision}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Pull hanya bisa membuka data yang dienkripsi dengan kunci yang sama (email, master " +
                            "password, dan local salt yang sama). Untuk pindah perangkat, gunakan backup .aegis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(onClick = { showSql = !showSql }) {
                        Text(if (showSql) "Sembunyikan SQL tabel" else "Lihat SQL tabel user_vaults")
                    }
                    if (showSql) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp)) {
                                Text(
                                    text = SupabaseSchema.DDL,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp)
                                )
                                IconButton(onClick = { vaultVm.copyText("SQL", SupabaseSchema.DDL) }) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Salin SQL")
                                }
                            }
                        }
                    }
                }
            }

            // ---- Zona bahaya ----
            SectionCard("Zona bahaya") {
                Text(
                    text = "Menghapus brankas, akun lokal, kunci biometrik, dan konfigurasi sinkronisasi dari perangkat ini. " +
                        "Buat backup .aegis lebih dulu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { showReset = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus semua data di perangkat ini") }
            }

            Text(
                text = "AlVault $versionName · zero-knowledge, data tersimpan lokal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    // ---- Dialog ----

    if (showExportDialog) {
        ExportDialog(
            onConfirm = { password ->
                showExportDialog = false
                vaultVm.prepareExport(password)
            },
            onDismiss = { showExportDialog = false }
        )
    }

    if (pendingImport != null) {
        ImportDialog(
            busy = busy,
            error = importError,
            onConfirm = { password, mode -> vaultVm.applyImport(password, mode) },
            onDismiss = { vaultVm.cancelImport() }
        )
    }

    ui.pushConflict?.let { remoteRevision ->
        AlertDialog(
            onDismissRequest = { settingsVm.dismissConflicts() },
            title = { Text("Cloud punya versi lebih baru") },
            text = {
                Text(
                    "Di cloud ada revisi $remoteRevision yang belum pernah diunduh perangkat ini. " +
                        "Jika dilanjutkan, isi cloud akan ditimpa dengan brankas di perangkat ini. " +
                        "Batalkan lalu lakukan Pull bila ingin menggabungkan lebih dulu."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsVm.push(url, key, force = true) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Timpa cloud") }
            },
            dismissButton = { TextButton(onClick = { settingsVm.dismissConflicts() }) { Text("Batal") } }
        )
    }

    ui.pullConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { settingsVm.dismissConflicts() },
            title = { Text("Berubah di dua sisi") },
            text = {
                Text(
                    "Perangkat ini (${conflict.localItems} entri) dan cloud (${conflict.remoteItems} entri, " +
                        "revisi ${conflict.remoteRevision}) sama-sama berubah sejak sinkronisasi terakhir.\n\n" +
                        "Gabungkan: pertahankan versi terbaru tiap entri; entri yang dihapus di satu sisi bisa muncul " +
                        "kembali.\nGanti: buang perubahan di perangkat ini dan pakai isi cloud."
                )
            },
            confirmButton = {
                TextButton(onClick = { settingsVm.pull(url, key, PullStrategy.MERGE) }) { Text("Gabungkan") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { settingsVm.pull(url, key, PullStrategy.REPLACE) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Ganti") }
                    TextButton(onClick = { settingsVm.dismissConflicts() }) { Text("Batal") }
                }
            }
        )
    }

    if (showReset) {
        ResetAllDialog(
            onConfirm = {
                showReset = false
                authVm.resetAll()
            },
            onDismiss = { showReset = false }
        )
    }
}

/** Meminta password backup (dengan konfirmasi) sebelum brankas dienkripsi menjadi berkas .aegis. */
@Composable
private fun ExportDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val problem = UiLogic.backupPasswordError(password, confirm)
    val showProblem = password.isNotEmpty() || confirm.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ekspor backup .aegis") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Pilih password khusus untuk berkas ini (berbeda dari master password). " +
                        "Tanpa password ini backup tidak bisa dibuka, dan tidak ada cara memulihkannya."
                )
                SecretField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password backup",
                    modifier = Modifier.fillMaxWidth()
                )
                StrengthMeter(password)
                SecretField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Ulangi password backup",
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Done
                )
                if (showProblem && problem != null) {
                    Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = problem == null) { Text("Buat backup") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Meminta password backup dan mode penggabungan untuk berkas .aegis yang sudah dibaca. */
@Composable
private fun ImportDialog(
    busy: Boolean,
    error: String?,
    onConfirm: (String, MergeMode) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(MergeMode.MERGE) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Impor backup .aegis") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Masukkan password yang dipakai saat backup dibuat (di aplikasi web atau Android).")
                SecretField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password backup",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    isError = error != null,
                    imeAction = ImeAction.Done
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                ModeOption(
                    selected = mode == MergeMode.MERGE,
                    title = "Gabungkan",
                    description = "Tambahkan entri baru. Untuk id yang sama, yang lebih baru menang.",
                    enabled = !busy
                ) { mode = MergeMode.MERGE }
                ModeOption(
                    selected = mode == MergeMode.REPLACE,
                    title = "Ganti semua",
                    description = "Hapus isi brankas sekarang dan ganti dengan isi backup.",
                    enabled = !busy
                ) { mode = MergeMode.REPLACE }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password, mode) },
                enabled = !busy && UiLogic.backupPasswordError(password, null) == null
            ) { Text(if (mode == MergeMode.REPLACE) "Ganti" else "Impor") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun ModeOption(
    selected: Boolean,
    title: String,
    description: String,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
