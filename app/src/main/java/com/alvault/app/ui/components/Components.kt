package com.alvault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alvault.app.core.security.PasswordStrength
import com.alvault.app.core.security.Strength
import com.alvault.app.ui.UiLogic
import com.alvault.app.ui.theme.PasswordTextStyle
import com.alvault.app.ui.theme.avatarColors
import com.alvault.app.ui.vm.StatusKind

/** Logo + nama aplikasi + satu kalimat penjelasan, dipakai di layar daftar dan layar buka kunci. */
@Composable
fun BrandHeader(subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
        Text("AlVault", style = MaterialTheme.typography.displaySmall)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Isian rahasia: teks disamarkan, ada tombol lihat/sembunyikan. Keyboard bertipe password
 * sehingga IME tidak menyimpan saran/koreksi dari isiannya.
 */
@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    enabled: Boolean = true,
    monospace: Boolean = false,
    trailingExtra: (@Composable () -> Unit)? = null
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        textStyle = if (monospace) PasswordTextStyle else LocalTextStyle.current,
        supportingText = supportingText?.let { text -> { Text(text) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingExtra?.invoke()
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Sembunyikan" else "Tampilkan"
                    )
                }
            }
        }
    )
}

/** Lingkaran berisi huruf pertama judul; warna stabil per [seed]. */
@Composable
fun Avatar(title: String, seed: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val (background, foreground) = avatarColors(seed)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = UiLogic.initialOf(title),
            color = foreground,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Lima segmen kekuatan password (perkiraan lokal, bukan jaminan). */
@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier) {
    if (password.isEmpty()) return
    val strength = remember(password) { PasswordStrength.score(password) }
    val scheme = MaterialTheme.colorScheme
    val (filled, label, color) = when (strength) {
        Strength.VERY_WEAK -> Triple(1, "Sangat lemah", scheme.error)
        Strength.WEAK -> Triple(2, "Lemah", scheme.error)
        Strength.FAIR -> Triple(3, "Cukup", scheme.secondary)
        Strength.STRONG -> Triple(4, "Kuat", scheme.tertiary)
        Strength.VERY_STRONG -> Triple(5, "Sangat kuat", scheme.tertiary)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < filled) color else scheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Kartu berjudul untuk mengelompokkan isi layar. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Kotak pesan berwarna sesuai [kind]: hijau OK, kuningan peringatan, merah galat, netral info. */
@Composable
fun StatusBanner(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = when (kind) {
        StatusKind.OK -> scheme.tertiaryContainer
        StatusKind.WARN -> scheme.secondaryContainer
        StatusKind.ERROR -> scheme.errorContainer
        StatusKind.INFO -> scheme.surfaceContainerHigh
    }
    val content = when (kind) {
        StatusKind.OK -> scheme.onTertiaryContainer
        StatusKind.WARN -> scheme.onSecondaryContainer
        StatusKind.ERROR -> scheme.onErrorContainer
        StatusKind.INFO -> scheme.onSurface
    }
    val icon: ImageVector = when (kind) {
        StatusKind.OK -> Icons.Filled.CheckCircle
        StatusKind.WARN, StatusKind.ERROR -> Icons.Filled.Warning
        StatusKind.INFO -> Icons.Filled.Info
    }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(text, color = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Tampilan kosong dengan ikon, judul, dan penjelasan singkat. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Konfirmasi dua langkah untuk menghapus semua data lokal: pengguna harus mengetik HAPUS. */
@Composable
fun ResetAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus semua data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Brankas, akun lokal, kunci biometrik, dan konfigurasi sinkronisasi di perangkat ini " +
                        "akan dihapus permanen. Data di Supabase tidak ikut terhapus. " +
                        "Tanpa backup .aegis, data tidak bisa dipulihkan."
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Ketik HAPUS untuk konfirmasi") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed.trim() == "HAPUS",
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Hapus semuanya") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Tombol utama dengan indikator sibuk di dalamnya. */
@Composable
fun BusyButton(
    text: String,
    busyText: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.size(10.dp))
            Text(busyText)
        } else {
            Text(text)
        }
    }
}
