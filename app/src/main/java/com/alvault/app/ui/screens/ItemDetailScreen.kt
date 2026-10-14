@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvault.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvault.app.ui.UiLogic
import com.alvault.app.ui.components.Avatar
import com.alvault.app.ui.components.ConfirmDialog
import com.alvault.app.ui.components.StrengthMeter
import com.alvault.app.ui.theme.PasswordTextStyle
import com.alvault.app.ui.vm.VaultViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimestampFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))

private fun formatTimestamp(millis: Long): String =
    if (millis <= 0L) "—" else TimestampFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Detail satu entri: salin username/password/URL, lihat password, ubah, hapus. */
@Composable
fun ItemDetailScreen(
    vaultVm: VaultViewModel,
    id: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val ui by vaultVm.ui.collectAsStateWithLifecycle()
    val item = ui.items.firstOrNull { it.id == id }
    if (item == null) {
        // Entri sudah dihapus (atau brankas dimuat ulang tanpa entri ini): tidak ada yang ditampilkan.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var revealed by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val browsable = remember(item.url) { item.url?.let { UiLogic.browsableUrl(it) } }

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = item.title.ifBlank { "Detail" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            },
            actions = {
                IconButton(onClick = { onEdit(item.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Ubah")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus")
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Avatar(title = item.title, seed = item.category ?: item.title, size = 56.dp)
                Column {
                    Text(
                        text = item.title.ifBlank { "(tanpa judul)" },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    item.category?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FieldCard(
                label = "Username",
                actions = {
                    if (item.username.isNotEmpty()) {
                        IconButton(onClick = { vaultVm.copyText("Username", item.username) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin username")
                        }
                    }
                }
            ) {
                Text(
                    text = item.username.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            FieldCard(
                label = "Password",
                actions = {
                    if (item.password.isNotEmpty()) {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (revealed) "Sembunyikan password" else "Tampilkan password"
                            )
                        }
                        IconButton(onClick = { vaultVm.copyPassword(item.password) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin password")
                        }
                    }
                }
            ) {
                if (item.password.isEmpty()) {
                    Text("—", style = MaterialTheme.typography.bodyLarge)
                } else {
                    // Titik berjumlah tetap supaya panjang password tidak terbaca dari layar.
                    Text(
                        text = if (revealed) item.password else "••••••••••••",
                        style = PasswordTextStyle
                    )
                    StrengthMeter(item.password)
                }
            }

            if (!item.url.isNullOrBlank()) {
                FieldCard(
                    label = "URL",
                    actions = {
                        if (browsable != null) {
                            IconButton(onClick = {
                                try {
                                    uriHandler.openUri(browsable)
                                } catch (_: Exception) {
                                    vaultVm.copyText("URL", item.url)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Buka di browser")
                            }
                        }
                        IconButton(onClick = { vaultVm.copyText("URL", item.url) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Salin URL")
                        }
                    }
                ) {
                    Text(text = item.url, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (!item.notes.isNullOrBlank()) {
                FieldCard(label = "Catatan") {
                    Text(text = item.notes, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Text(
                text = "Terakhir diubah: ${formatTimestamp(item.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Hapus entri?",
            text = "\"${item.title.ifBlank { "Tanpa judul" }}\" akan dihapus dari brankas ini. " +
                "Entri yang sama di Supabase baru terhapus setelah Anda melakukan Upload berikutnya.",
            confirmLabel = "Hapus",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                vaultVm.delete(item.id) { ok -> if (ok) onBack() }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Satu bidang berlabel dengan aksi di sisi kanan (salin, lihat, buka). */
@Composable
private fun FieldCard(
    label: String,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                content()
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
