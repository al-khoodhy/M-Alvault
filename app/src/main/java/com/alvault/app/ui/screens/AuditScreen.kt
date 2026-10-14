@file:OptIn(ExperimentalMaterial3Api::class)

package com.alvault.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alvault.app.core.security.AuditEntry
import com.alvault.app.core.security.AuditReport
import com.alvault.app.core.security.AuditStatus
import com.alvault.app.ui.components.EmptyState
import com.alvault.app.ui.components.StatusBanner
import com.alvault.app.ui.vm.AuditUi
import com.alvault.app.ui.vm.AuditViewModel
import com.alvault.app.ui.vm.StatusKind
import com.alvault.app.ui.vm.VaultViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Tab "Audit": cek password lemah secara lokal dan kebocoran lewat HIBP (k-anonymity). */
@Composable
fun AuditScreen(
    auditVm: AuditViewModel,
    vaultVm: VaultViewModel,
    onOpenItem: (String) -> Unit
) {
    val audit by auditVm.ui.collectAsStateWithLifecycle()
    val vault by vaultVm.ui.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Audit keamanan") })

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusBanner(
                    text = "Password diperiksa ke Have I Been Pwned dengan k-anonymity: hanya 5 karakter awal " +
                        "hash SHA-1 yang dikirim, jadi password Anda tidak pernah meninggalkan perangkat. " +
                        "Cek password lemah berjalan sepenuhnya offline.",
                    kind = StatusKind.INFO
                )
            }

            when (val state = audit) {
                AuditUi.Idle -> item {
                    if (vault.items.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Security,
                            title = "Belum ada yang diperiksa",
                            body = "Tambahkan entri ke brankas terlebih dahulu."
                        )
                    } else {
                        Button(
                            onClick = { auditVm.run(vault.items) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mulai audit (${vault.items.size} entri)") }
                    }
                }

                is AuditUi.Running -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.total > 0) {
                            LinearProgressIndicator(
                                progress = { state.done.toFloat() / state.total.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Memeriksa ${state.done} dari ${state.total} kelompok hash…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Menyiapkan…", style = MaterialTheme.typography.bodyMedium)
                        }
                        OutlinedButton(onClick = { auditVm.cancel() }) { Text("Batalkan") }
                    }
                }

                is AuditUi.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusBanner(text = state.message, kind = StatusKind.ERROR)
                        if (vault.items.isNotEmpty()) {
                            Button(onClick = { auditVm.run(vault.items) }) { Text("Coba lagi") }
                        }
                    }
                }

                is AuditUi.Done -> {
                    item { Summary(report = state.report, checkedAt = state.checkedAt) }
                    item {
                        OutlinedButton(onClick = { auditVm.run(vault.items) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Periksa ulang")
                        }
                    }
                    // distinctBy: kunci LazyColumn harus unik, dan id dari berkas impor/cloud tidak dijamin unik.
                    items(state.report.entries.distinctBy { it.itemId }, key = { it.itemId }) { entry ->
                        EntryRow(entry = entry, onClick = { onOpenItem(entry.itemId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Summary(report: AuditReport, checkedAt: Long) {
    val time = TimeFormat.format(Instant.ofEpochMilli(checkedAt).atZone(ZoneId.systemDefault()))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("Bocor", report.compromised, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            Stat("Lemah", report.weak, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            Stat("Aman", report.safe, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }
        if (report.unchecked > 0) {
            StatusBanner(
                text = "${report.unchecked} entri tidak bisa diperiksa ke HIBP (tidak ada koneksi atau layanan menolak). " +
                    "Hasil cek lemah tetap berlaku.",
                kind = StatusKind.WARN
            )
        }
        if (report.itemsWithoutPassword > 0) {
            Text(
                text = "${report.itemsWithoutPassword} entri tanpa password dilewati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "Diperiksa pukul $time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EntryRow(entry: AuditEntry, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val icon: ImageVector
    val tint: Color
    val detail: String
    when (entry.status) {
        AuditStatus.COMPROMISED -> {
            icon = Icons.Filled.Warning
            tint = scheme.error
            val times = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(entry.breachCount.toLong())
            detail = "Muncul $times kali di kebocoran data" + if (entry.isWeak) " · juga lemah" else ""
        }
        AuditStatus.WEAK -> {
            icon = Icons.Filled.Warning
            tint = scheme.secondary
            detail = "Password lemah"
        }
        AuditStatus.UNCHECKED -> {
            icon = Icons.Filled.Info
            tint = scheme.outline
            detail = "Belum bisa diperiksa ke HIBP"
        }
        AuditStatus.SAFE -> {
            icon = Icons.Filled.CheckCircle
            tint = scheme.tertiary
            detail = "Tidak ditemukan di kebocoran data"
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = entry.title.ifBlank { "(tanpa judul)" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column {
                if (entry.username.isNotBlank()) {
                    Text(
                        text = entry.username,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(text = detail, color = tint)
            }
        },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
